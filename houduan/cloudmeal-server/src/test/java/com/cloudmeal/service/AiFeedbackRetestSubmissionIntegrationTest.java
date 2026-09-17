package com.cloudmeal.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloudmeal.context.BaseContext;
import com.cloudmeal.dto.AiFeedbackRetestSubmitDTO;
import com.cloudmeal.entity.AiChatFeedback;
import com.cloudmeal.entity.AiFeedbackKnowledgeRelation;
import com.cloudmeal.entity.AiFeedbackRetest;
import com.cloudmeal.entity.AiKnowledge;
import com.cloudmeal.enums.AiChatFeedbackHandleStatus;
import com.cloudmeal.enums.AiChatFeedbackResult;
import com.cloudmeal.enums.AiKnowledgeCategory;
import com.cloudmeal.enums.AiKnowledgeEmbeddingStatus;
import com.cloudmeal.enums.AiKnowledgeStatus;
import com.cloudmeal.mapper.AiChatFeedbackMapper;
import com.cloudmeal.mapper.AiFeedbackKnowledgeRelationMapper;
import com.cloudmeal.mapper.AiFeedbackRetestMapper;
import com.cloudmeal.mapper.AiKnowledgeMapper;
import com.cloudmeal.vo.AiFeedbackRetestSubmitVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 使用真实MySQL验证同一反馈并发提交时只创建一个活动复测。 */
@SpringBootTest(properties = {
        "cloudmeal.websocket.enabled=false",
        "cloudmeal.ai.embedding-task-initial-delay-ms=3600000",
        "cloudmeal.ai.retest-task-initial-delay-ms=3600000"
})
class AiFeedbackRetestSubmissionIntegrationTest {

    @Autowired
    private AiChatFeedbackReviewService reviewService;

    @Autowired
    private AiChatFeedbackMapper feedbackMapper;

    @Autowired
    private AiFeedbackKnowledgeRelationMapper relationMapper;

    @Autowired
    private AiFeedbackRetestMapper retestMapper;

    @Autowired
    private AiKnowledgeMapper knowledgeMapper;

    @Test
    void shouldCreateOnlyOneRetestWhenTwoAdministratorsSubmitConcurrently()
            throws Exception {
        String knowledgeKey = "TEST_RETEST_"
                + UUID.randomUUID().toString().replace("-", "");
        AiChatFeedback feedback = insertFeedback();
        insertReadyKnowledge(knowledgeKey);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            AiFeedbackRetestSubmitDTO submit = new AiFeedbackRetestSubmitDTO();
            submit.setQuestion("待接单订单能取消吗？");
            submit.setKnowledgeKeys(Collections.singletonList(knowledgeKey));
            CountDownLatch start = new CountDownLatch(1);

            Future<AiFeedbackRetestSubmitVO> first = executor.submit(
                    () -> submitAs(101L, feedback.getId(), submit, start));
            Future<AiFeedbackRetestSubmitVO> second = executor.submit(
                    () -> submitAs(202L, feedback.getId(), submit, start));
            start.countDown();

            AiFeedbackRetestSubmitVO firstResult =
                    first.get(10, TimeUnit.SECONDS);
            AiFeedbackRetestSubmitVO secondResult =
                    second.get(10, TimeUnit.SECONDS);
            assertEquals(firstResult.getRetestId(), secondResult.getRetestId());
            assertEquals(1L, retestMapper.selectCount(
                    Wrappers.<AiFeedbackRetest>lambdaQuery()
                            .eq(AiFeedbackRetest::getFeedbackId, feedback.getId())));
            assertEquals(1L, relationMapper.selectCount(
                    Wrappers.<AiFeedbackKnowledgeRelation>lambdaQuery()
                            .eq(AiFeedbackKnowledgeRelation::getFeedbackId,
                                    feedback.getId())));
            assertEquals(AiChatFeedbackHandleStatus.PENDING_RETEST,
                    feedbackMapper.selectById(feedback.getId()).getHandleStatus());
        } finally {
            executor.shutdownNow();
            deleteTestData(feedback.getId(), knowledgeKey);
        }
    }

    private AiFeedbackRetestSubmitVO submitAs(
            Long operatorId, Long feedbackId, AiFeedbackRetestSubmitDTO submit,
            CountDownLatch start) throws Exception {
        start.await();
        BaseContext.setCurrentId(operatorId);
        try {
            return reviewService.submitRetest(feedbackId, submit);
        } finally {
            BaseContext.removeCurrentId();
        }
    }

    private AiChatFeedback insertFeedback() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        long sessionId = Math.abs(UUID.randomUUID().getMostSignificantBits());
        AiChatFeedback feedback = AiChatFeedback.builder()
                .sessionId(sessionId)
                .userId(sessionId)
                .result(AiChatFeedbackResult.UNSOLVED)
                .handleStatus(AiChatFeedbackHandleStatus.PENDING)
                .createTime(now)
                .updateTime(now)
                .build();
        assertEquals(1, feedbackMapper.insert(feedback));
        return feedback;
    }

    private void insertReadyKnowledge(String knowledgeKey) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        AiKnowledge knowledge = AiKnowledge.builder()
                .knowledgeKey(knowledgeKey)
                .title("订单取消规则")
                .category(AiKnowledgeCategory.ORDER)
                .content("商家接单前可以取消订单。")
                .embedding(validEmbedding())
                .embeddingModel("embedding-3")
                .embeddingDimensions(256)
                .embeddingStatus(AiKnowledgeEmbeddingStatus.READY)
                .versionNo(1)
                .status(AiKnowledgeStatus.ENABLED)
                .createTime(now)
                .updateTime(now)
                .build();
        assertEquals(1, knowledgeMapper.insert(knowledge));
    }

    private String validEmbedding() {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < 256; index++) {
            if (index > 0) {
                result.append(',');
            }
            result.append(index == 0 ? "1.0" : "0.0");
        }
        return result.append(']').toString();
    }

    private void deleteTestData(Long feedbackId, String knowledgeKey) {
        relationMapper.delete(Wrappers.<AiFeedbackKnowledgeRelation>lambdaQuery()
                .eq(AiFeedbackKnowledgeRelation::getFeedbackId, feedbackId));
        retestMapper.delete(Wrappers.<AiFeedbackRetest>lambdaQuery()
                .eq(AiFeedbackRetest::getFeedbackId, feedbackId));
        feedbackMapper.deleteById(feedbackId);
        knowledgeMapper.delete(Wrappers.<AiKnowledge>lambdaQuery()
                .eq(AiKnowledge::getKnowledgeKey, knowledgeKey));
    }
}
