package com.sky.mapper;

import com.sky.entity.AiChatFeedback;
import com.sky.entity.AiFeedbackKnowledgeRelation;
import com.sky.entity.AiFeedbackRetest;
import com.sky.entity.AiKnowledge;
import com.sky.entity.AiKnowledgeEmbeddingTask;
import com.sky.context.BaseContext;
import com.sky.dto.AiFeedbackRetestReviewDTO;
import com.sky.enums.AiChatFeedbackHandleStatus;
import com.sky.enums.AiChatFeedbackResult;
import com.sky.enums.AiFeedbackRetestExecutionStatus;
import com.sky.enums.AiFeedbackRetestReviewResult;
import com.sky.enums.AiKnowledgeCategory;
import com.sky.enums.AiKnowledgeEmbeddingStatus;
import com.sky.enums.AiKnowledgeEmbeddingTaskStatus;
import com.sky.enums.AiKnowledgeStatus;
import com.sky.service.AiChatFeedbackReviewService;
import com.sky.vo.AiFeedbackRetestDetailVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 真实数据库验证处理闭环所依赖的条件更新和状态映射。 */
@SpringBootTest(properties = {
        "sky.websocket.enabled=false",
        "sky.ai.retest-task-initial-delay-ms=3600000"
})
@Transactional
class AiFeedbackResolutionMapperIntegrationTest {

    @Autowired
    private AiChatFeedbackMapper feedbackMapper;

    @Autowired
    private AiKnowledgeMapper knowledgeMapper;

    @Autowired
    private AiKnowledgeEmbeddingTaskMapper embeddingTaskMapper;

    @Autowired
    private AiFeedbackKnowledgeRelationMapper relationMapper;

    @Autowired
    private AiFeedbackRetestMapper retestMapper;

    @Autowired
    private AiChatFeedbackReviewService reviewService;

    @AfterEach
    void clearCurrentAdministrator() {
        BaseContext.removeCurrentId();
    }

    @Test
    void shouldClaimEmbeddingTaskOnceAndRejectStaleCompletionToken() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        AiKnowledge knowledge = insertPendingKnowledge(now);
        AiKnowledgeEmbeddingTask task = AiKnowledgeEmbeddingTask.builder()
                .knowledgeId(knowledge.getId())
                .status(AiKnowledgeEmbeddingTaskStatus.PENDING)
                .retryCount(0)
                .maxRetryCount(3)
                .nextRetryTime(now.minusSeconds(1))
                .createTime(now)
                .updateTime(now)
                .build();
        assertThat(embeddingTaskMapper.insert(task)).isEqualTo(1);

        assertThat(embeddingTaskMapper.claimPendingTask(task.getId(), now)).isEqualTo(1);
        assertThat(embeddingTaskMapper.claimPendingTask(task.getId(), now)).isZero();
        assertThat(embeddingTaskMapper.markSucceeded(
                task.getId(), now.minusSeconds(1), now.plusSeconds(1))).isZero();
        assertThat(embeddingTaskMapper.markSucceeded(
                task.getId(), now, now.plusSeconds(1))).isEqualTo(1);

        AiKnowledgeEmbeddingTask saved = embeddingTaskMapper.selectById(task.getId());
        assertThat(saved.getStatus()).isEqualTo(AiKnowledgeEmbeddingTaskStatus.SUCCEEDED);
        assertThat(saved.getSuccessTime()).isEqualTo(now.plusSeconds(1));
    }

    @Test
    void shouldOnlyExposeReadyEnabledKnowledgeAndHandleFeedbackIdempotently() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        AiChatFeedback feedback = insertUnsolvedFeedback(now);
        AiKnowledge knowledge = insertPendingKnowledge(now);
        AiFeedbackKnowledgeRelation relation = AiFeedbackKnowledgeRelation.builder()
                .feedbackId(feedback.getId())
                .knowledgeKey(knowledge.getKnowledgeKey())
                .operatorId(91L)
                .createTime(now)
                .build();
        assertThat(relationMapper.insert(relation)).isEqualTo(1);

        assertThat(relationMapper.countReadyKnowledge(
                feedback.getId(), "embedding-test", 3)).isZero();
        assertThat(knowledgeMapper.markEmbeddingReady(
                knowledge.getId(), "[1.0,0.0,0.0]", "embedding-test", 3, now)).isEqualTo(1);
        assertThat(relationMapper.countReadyKnowledge(
                feedback.getId(), "embedding-test", 3)).isEqualTo(1);

        assertThat(feedbackMapper.markPendingRetest(feedback.getId(), now)).isEqualTo(1);
        assertThat(feedbackMapper.markHandled(feedback.getId(), 101L, now)).isEqualTo(1);
        assertThat(feedbackMapper.markHandled(feedback.getId(), 202L, now.plusSeconds(1))).isZero();

        AiChatFeedback handled = feedbackMapper.selectById(feedback.getId());
        assertThat(handled.getHandleStatus()).isEqualTo(AiChatFeedbackHandleStatus.HANDLED);
        assertThat(handled.getHandlerId()).isEqualTo(101L);
        assertThat(handled.getHandleTime()).isEqualTo(now);
    }

    @Test
    void shouldSeparateRetestExecutionFromManualReview() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        AiChatFeedback feedback = insertUnsolvedFeedback(now);
        AiFeedbackRetest retest = AiFeedbackRetest.builder()
                .feedbackId(feedback.getId())
                .question("待接单订单能取消吗？")
                .executionStatus(AiFeedbackRetestExecutionStatus.PENDING)
                .retryCount(0)
                .maxRetryCount(3)
                .nextRetryTime(now.minusSeconds(1))
                .initiatorId(301L)
                .createTime(now)
                .updateTime(now)
                .build();
        assertThat(retestMapper.insert(retest)).isEqualTo(1);

        assertThat(retestMapper.claimPendingRetest(retest.getId(), now)).isEqualTo(1);
        assertThat(retestMapper.claimPendingRetest(retest.getId(), now)).isZero();
        assertThat(retestMapper.markExecutionSucceeded(
                retest.getId(), now, "可以，商家接单前可以取消。", "[1]",
                now.plusSeconds(1))).isEqualTo(1);

        AiFeedbackRetest generated = retestMapper.selectById(retest.getId());
        assertThat(generated.getExecutionStatus())
                .isEqualTo(AiFeedbackRetestExecutionStatus.SUCCEEDED);
        assertThat(generated.getReviewResult()).isNull();

        assertThat(retestMapper.markReviewed(
                retest.getId(), AiFeedbackRetestReviewResult.CORRECT.getValue(),
                302L, now.plusSeconds(2))).isEqualTo(1);
        assertThat(retestMapper.markReviewed(
                retest.getId(), AiFeedbackRetestReviewResult.INCORRECT.getValue(),
                303L, now.plusSeconds(3))).isZero();

        AiFeedbackRetest reviewed = retestMapper.selectById(retest.getId());
        assertThat(reviewed.getReviewResult())
                .isEqualTo(AiFeedbackRetestReviewResult.CORRECT);
        assertThat(reviewed.getReviewerId()).isEqualTo(302L);
    }

    @Test
    void feedbackChangeShouldObsoleteEveryUnreviewedRetestState() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        AiChatFeedback feedback = insertUnsolvedFeedback(now);
        AiKnowledge knowledge = insertPendingKnowledge(now);
        assertThat(relationMapper.insert(AiFeedbackKnowledgeRelation.builder()
                .feedbackId(feedback.getId())
                .knowledgeKey(knowledge.getKnowledgeKey())
                .operatorId(91L)
                .createTime(now)
                .build())).isEqualTo(1);

        AiFeedbackRetest pending = insertRetest(
                feedback.getId(), AiFeedbackRetestExecutionStatus.PENDING, null, now);
        AiFeedbackRetest processing = insertRetest(
                feedback.getId(), AiFeedbackRetestExecutionStatus.PROCESSING, null, now);
        AiFeedbackRetest awaitingReview = insertRetest(
                feedback.getId(), AiFeedbackRetestExecutionStatus.SUCCEEDED, null, now);
        AiFeedbackRetest reviewed = insertRetest(
                feedback.getId(), AiFeedbackRetestExecutionStatus.SUCCEEDED,
                AiFeedbackRetestReviewResult.CORRECT, now);

        assertThat(feedbackMapper.obsoleteActiveRetests(feedback.getId(), now.plusSeconds(1)))
                .isEqualTo(3);
        assertThat(feedbackMapper.deleteKnowledgeRelations(feedback.getId())).isEqualTo(1);

        assertThat(retestMapper.selectById(pending.getId()).getExecutionStatus())
                .isEqualTo(AiFeedbackRetestExecutionStatus.OBSOLETE);
        assertThat(retestMapper.selectById(processing.getId()).getExecutionStatus())
                .isEqualTo(AiFeedbackRetestExecutionStatus.OBSOLETE);
        assertThat(retestMapper.selectById(awaitingReview.getId()).getExecutionStatus())
                .isEqualTo(AiFeedbackRetestExecutionStatus.OBSOLETE);
        assertThat(retestMapper.selectById(reviewed.getId()).getExecutionStatus())
                .isEqualTo(AiFeedbackRetestExecutionStatus.SUCCEEDED);
        assertThat(relationMapper.countByFeedbackId(feedback.getId())).isZero();
    }

    @Test
    void shouldFailOnlyAfterInitialAttemptAndThreeAutomaticRetries() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        AiChatFeedback feedback = insertUnsolvedFeedback(now);
        AiFeedbackRetest retest = AiFeedbackRetest.builder()
                .feedbackId(feedback.getId())
                .question("待接单订单能取消吗？")
                .executionStatus(AiFeedbackRetestExecutionStatus.PENDING)
                .retryCount(0)
                .maxRetryCount(3)
                .nextRetryTime(now.minusSeconds(1))
                .initiatorId(301L)
                .createTime(now)
                .updateTime(now)
                .build();
        assertThat(retestMapper.insert(retest)).isEqualTo(1);

        for (int attempt = 0; attempt < 4; attempt++) {
            LocalDateTime token = now.plusMinutes(attempt);
            LocalDateTime nextRetry = token.plusMinutes(1);
            assertThat(retestMapper.claimPendingRetest(retest.getId(), token))
                    .isEqualTo(1);
            assertThat(retestMapper.markRetryFailure(
                    retest.getId(), token, "timeout", nextRetry, token))
                    .isEqualTo(1);

            AiFeedbackRetest saved = retestMapper.selectById(retest.getId());
            if (attempt < 3) {
                assertThat(saved.getExecutionStatus())
                        .isEqualTo(AiFeedbackRetestExecutionStatus.PENDING);
                assertThat(saved.getRetryCount()).isEqualTo(attempt + 1);
            } else {
                assertThat(saved.getExecutionStatus())
                        .isEqualTo(AiFeedbackRetestExecutionStatus.FAILED);
                assertThat(saved.getRetryCount()).isEqualTo(3);
            }
        }
    }

    @Test
    void shouldConfirmCorrectOnceAndKeepOriginalReviewerOnRepeatedRequest() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        AiChatFeedback feedback = insertUnsolvedFeedback(now);
        feedbackMapper.markPendingRetest(feedback.getId(), now);
        AiFeedbackRetest retest = AiFeedbackRetest.builder()
                .feedbackId(feedback.getId())
                .question("待接单订单能取消吗？")
                .answer("可以，确认后执行取消。")
                .usedKnowledgeIds("[31]")
                .executionStatus(AiFeedbackRetestExecutionStatus.SUCCEEDED)
                .retryCount(0)
                .maxRetryCount(3)
                .nextRetryTime(now)
                .initiatorId(301L)
                .createTime(now)
                .updateTime(now)
                .build();
        assertThat(retestMapper.insert(retest)).isEqualTo(1);

        BaseContext.setCurrentId(401L);
        AiFeedbackRetestReviewDTO correct = new AiFeedbackRetestReviewDTO();
        correct.setReviewResult(AiFeedbackRetestReviewResult.CORRECT);
        AiFeedbackRetestDetailVO first = reviewService.reviewRetest(
                feedback.getId(), retest.getId(), correct);
        assertThat(first.getHandleStatus())
                .isEqualTo(AiChatFeedbackHandleStatus.HANDLED);

        BaseContext.setCurrentId(402L);
        AiFeedbackRetestReviewDTO conflicting = new AiFeedbackRetestReviewDTO();
        conflicting.setReviewResult(AiFeedbackRetestReviewResult.INCORRECT);
        AiFeedbackRetestDetailVO repeated = reviewService.reviewRetest(
                feedback.getId(), retest.getId(), conflicting);

        assertThat(repeated.getReviewResult())
                .isEqualTo(AiFeedbackRetestReviewResult.CORRECT);
        AiFeedbackRetest savedRetest = retestMapper.selectById(retest.getId());
        AiChatFeedback savedFeedback = feedbackMapper.selectById(feedback.getId());
        assertThat(savedRetest.getReviewerId()).isEqualTo(401L);
        assertThat(savedFeedback.getHandlerId()).isEqualTo(401L);
    }

    private AiChatFeedback insertUnsolvedFeedback(LocalDateTime now) {
        long sessionId = Math.abs(UUID.randomUUID().getMostSignificantBits());
        AiChatFeedback feedback = AiChatFeedback.builder()
                .sessionId(sessionId)
                .userId(sessionId)
                .result(AiChatFeedbackResult.UNSOLVED)
                .handleStatus(AiChatFeedbackHandleStatus.PENDING)
                .createTime(now)
                .updateTime(now)
                .build();
        assertThat(feedbackMapper.insert(feedback)).isEqualTo(1);
        return feedback;
    }

    private AiKnowledge insertPendingKnowledge(LocalDateTime now) {
        AiKnowledge knowledge = AiKnowledge.builder()
                .knowledgeKey("TEST_FEEDBACK_" + UUID.randomUUID().toString().replace("-", ""))
                .title("订单取消规则")
                .category(AiKnowledgeCategory.ORDER)
                .content("商家接单前可以取消订单。")
                .embeddingStatus(AiKnowledgeEmbeddingStatus.PENDING)
                .versionNo(1)
                .status(AiKnowledgeStatus.ENABLED)
                .createTime(now)
                .updateTime(now)
                .build();
        assertThat(knowledgeMapper.insert(knowledge)).isEqualTo(1);
        return knowledge;
    }

    private AiFeedbackRetest insertRetest(
            Long feedbackId,
            AiFeedbackRetestExecutionStatus executionStatus,
            AiFeedbackRetestReviewResult reviewResult,
            LocalDateTime now) {
        AiFeedbackRetest retest = AiFeedbackRetest.builder()
                .feedbackId(feedbackId)
                .question("评价变更失效测试")
                .executionStatus(executionStatus)
                .reviewResult(reviewResult)
                .reviewerId(reviewResult == null ? null : 92L)
                .reviewTime(reviewResult == null ? null : now)
                .retryCount(0)
                .maxRetryCount(3)
                .nextRetryTime(now)
                .processingTime(executionStatus == AiFeedbackRetestExecutionStatus.PROCESSING
                        ? now : null)
                .initiatorId(91L)
                .createTime(now)
                .updateTime(now)
                .build();
        assertThat(retestMapper.insert(retest)).isEqualTo(1);
        return retest;
    }
}
