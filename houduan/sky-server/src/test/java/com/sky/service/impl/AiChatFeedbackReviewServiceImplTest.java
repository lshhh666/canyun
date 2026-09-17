package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.dto.AiChatFeedbackPageQueryDTO;
import com.sky.dto.AiFeedbackRetestSubmitDTO;
import com.sky.dto.AiFeedbackRetestReviewDTO;
import com.sky.entity.AiChatFeedback;
import com.sky.entity.AiFeedbackKnowledgeRelation;
import com.sky.entity.AiFeedbackRetest;
import com.sky.enums.AiChatFeedbackHandleStatus;
import com.sky.enums.AiChatFeedbackResult;
import com.sky.enums.AiFeedbackRetestExecutionStatus;
import com.sky.enums.AiFeedbackRetestReviewResult;
import com.sky.exception.BaseException;
import com.sky.context.BaseContext;
import com.sky.mapper.AiChatFeedbackMapper;
import com.sky.mapper.AiChatMessageMapper;
import com.sky.mapper.AiFeedbackKnowledgeRelationMapper;
import com.sky.mapper.AiFeedbackRetestMapper;
import com.sky.mapper.AiKnowledgeMapper;
import com.sky.properties.AiProperties;
import com.sky.result.PageResult;
import com.sky.vo.AiChatFeedbackPageVO;
import com.sky.vo.AiChatFeedbackTurnVO;
import com.sky.vo.AiFeedbackRetestSubmitVO;
import com.sky.vo.AiFeedbackRetestDetailVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatFeedbackReviewServiceImplTest {

    @Mock
    private AiChatFeedbackMapper feedbackMapper;

    @Mock
    private AiChatMessageMapper messageMapper;

    @Mock
    private AiFeedbackKnowledgeRelationMapper relationMapper;

    @Mock
    private AiFeedbackRetestMapper retestMapper;

    @Mock
    private AiKnowledgeMapper knowledgeMapper;

    private AiProperties aiProperties;

    private AiChatFeedbackReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        aiProperties.setEmbeddingModel("embedding-3");
        aiProperties.setEmbeddingDimensions(256);
        service = new AiChatFeedbackReviewServiceImpl(
                feedbackMapper, messageMapper, relationMapper,
                retestMapper, knowledgeMapper, aiProperties);
        BaseContext.setCurrentId(77L);
    }

    @AfterEach
    void clearPageHelper() {
        PageHelper.clearPage();
        BaseContext.removeCurrentId();
    }

    @Test
    void shouldPageFeedbackFirstAndThenBatchAssembleLatestTurns() {
        AiChatFeedbackPageQueryDTO query = query(
                1, 10, LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 10));
        LocalDateTime beginTime = LocalDate.of(2026, 9, 1).atStartOfDay();
        LocalDateTime endExclusive = LocalDate.of(2026, 9, 11).atStartOfDay();
        AiChatFeedback feedback = AiChatFeedback.builder()
                .id(8L)
                .sessionId(20L)
                .userId(30L)
                .updateTime(LocalDateTime.of(2026, 9, 5, 12, 0))
                .build();
        Page<AiChatFeedback> page = new Page<>(1, 10);
        page.setTotal(1L);
        page.add(feedback);
        when(feedbackMapper.pageUnsolved(beginTime, endExclusive))
                .thenReturn(page);
        when(messageMapper.selectLatestTurnsBySessionIds(
                Collections.singletonList(20L)))
                .thenReturn(Collections.singletonList(
                        AiChatFeedbackTurnVO.builder()
                                .sessionId(20L)
                                .userQuestion("question")
                                .aiAnswer("answer")
                                .build()));

        PageResult result = service.pageUnsolved(query);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
        AiChatFeedbackPageVO record =
                (AiChatFeedbackPageVO) result.getRecords().get(0);
        assertThat(record.getFeedbackId()).isEqualTo(8L);
        assertThat(record.getSessionId()).isEqualTo(20L);
        assertThat(record.getUserQuestion()).isEqualTo("question");
        assertThat(record.getAiAnswer()).isEqualTo("answer");
        assertThat(record.getFeedbackTime())
                .isEqualTo(LocalDateTime.of(2026, 9, 5, 12, 0));
        verify(feedbackMapper).pageUnsolved(beginTime, endExclusive);
        verify(messageMapper).selectLatestTurnsBySessionIds(
                Collections.singletonList(20L));
    }

    @Test
    void shouldSkipMessageQueryWhenRequestedPageIsEmpty() {
        AiChatFeedbackPageQueryDTO query = query(2, 10, null, null);
        Page<AiChatFeedback> page = new Page<>(2, 10);
        page.setTotal(1L);
        when(feedbackMapper.pageUnsolved(null, null)).thenReturn(page);

        PageResult result = service.pageUnsolved(query);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).isEmpty();
        verify(messageMapper, never()).selectLatestTurnsBySessionIds(
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void shouldRejectInvalidPaginationBeforeQueryingDatabase() {
        AiChatFeedbackPageQueryDTO query = query(0, 101, null, null);

        assertThatThrownBy(() -> service.pageUnsolved(query))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_CHAT_FEEDBACK_PAGE_INVALID);

        verify(feedbackMapper, never()).pageUnsolved(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectReversedDateRangeBeforeQueryingDatabase() {
        AiChatFeedbackPageQueryDTO query = query(
                1, 10, LocalDate.of(2026, 9, 11),
                LocalDate.of(2026, 9, 10));

        assertThatThrownBy(() -> service.pageUnsolved(query))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_CHAT_FEEDBACK_DATE_RANGE_INVALID);

        verify(feedbackMapper, never()).pageUnsolved(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectMaximumEndDateWithoutOverflow() {
        AiChatFeedbackPageQueryDTO query = query(1, 10, null, LocalDate.MAX);

        assertThatThrownBy(() -> service.pageUnsolved(query))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_CHAT_FEEDBACK_DATE_RANGE_INVALID);

        verify(feedbackMapper, never()).pageUnsolved(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReplaceRelationsAndQueueOneRetestInShortTransaction() {
        AiFeedbackRetestSubmitDTO submit = new AiFeedbackRetestSubmitDTO();
        submit.setQuestion("  待接单订单能取消吗？  ");
        submit.setKnowledgeKeys(Arrays.asList("ORDER_CANCEL", "ORDER_RULE", "ORDER_CANCEL"));
        AiChatFeedback feedback = AiChatFeedback.builder()
                .id(8L)
                .result(AiChatFeedbackResult.UNSOLVED)
                .handleStatus(AiChatFeedbackHandleStatus.PENDING)
                .build();
        when(feedbackMapper.selectByIdForUpdate(8L)).thenReturn(feedback);
        when(retestMapper.selectUnfinishedByFeedbackIdForUpdate(8L)).thenReturn(null);
        when(knowledgeMapper.countReadyCurrentByKeys(
                Arrays.asList("ORDER_CANCEL", "ORDER_RULE"),
                "embedding-3", 256)).thenReturn(2);
        when(relationMapper.insert(any(AiFeedbackKnowledgeRelation.class)))
                .thenReturn(1);
        when(retestMapper.insert(any(AiFeedbackRetest.class))).thenAnswer(invocation -> {
            AiFeedbackRetest retest = invocation.getArgument(0);
            retest.setId(91L);
            return 1;
        });
        when(feedbackMapper.markPendingRetest(eq(8L), any(LocalDateTime.class)))
                .thenReturn(1);

        AiFeedbackRetestSubmitVO result = service.submitRetest(8L, submit);

        assertThat(result.getRetestId()).isEqualTo(91L);
        assertThat(result.getExecutionStatus())
                .isEqualTo(AiFeedbackRetestExecutionStatus.PENDING);
        assertThat(result.getMessage())
                .isEqualTo(MessageConstant.AI_FEEDBACK_RETEST_QUEUED);
        verify(relationMapper).deleteByFeedbackId(8L);
        verify(relationMapper, times(2))
                .insert(any(AiFeedbackKnowledgeRelation.class));
        verify(feedbackMapper).markPendingRetest(eq(8L), any(LocalDateTime.class));
    }

    @Test
    void shouldReturnExistingTaskWithoutChangingKnowledgeRelations() {
        AiFeedbackRetestSubmitDTO submit = retestSubmit();
        AiChatFeedback feedback = AiChatFeedback.builder()
                .id(8L)
                .result(AiChatFeedbackResult.UNSOLVED)
                .handleStatus(AiChatFeedbackHandleStatus.PENDING_RETEST)
                .build();
        AiFeedbackRetest existing = AiFeedbackRetest.builder()
                .id(90L)
                .feedbackId(8L)
                .executionStatus(AiFeedbackRetestExecutionStatus.PROCESSING)
                .build();
        when(feedbackMapper.selectByIdForUpdate(8L)).thenReturn(feedback);
        when(retestMapper.selectUnfinishedByFeedbackIdForUpdate(8L))
                .thenReturn(existing);

        AiFeedbackRetestSubmitVO result = service.submitRetest(8L, submit);

        assertThat(result.getRetestId()).isEqualTo(90L);
        assertThat(result.getMessage())
                .isEqualTo(MessageConstant.AI_FEEDBACK_RETEST_PROCESSING);
        verify(knowledgeMapper, never()).countReadyCurrentByKeys(
                any(), any(), anyInt());
        verify(relationMapper, never()).deleteByFeedbackId(anyLong());
    }

    @Test
    void shouldTreatHandledFeedbackAsIdempotentSuccess() {
        AiChatFeedback feedback = AiChatFeedback.builder()
                .id(8L)
                .result(AiChatFeedbackResult.UNSOLVED)
                .handleStatus(AiChatFeedbackHandleStatus.HANDLED)
                .build();
        when(feedbackMapper.selectByIdForUpdate(8L)).thenReturn(feedback);

        AiFeedbackRetestSubmitVO result = service.submitRetest(8L, retestSubmit());

        assertThat(result.getHandleStatus())
                .isEqualTo(AiChatFeedbackHandleStatus.HANDLED);
        assertThat(result.getMessage())
                .isEqualTo(MessageConstant.AI_FEEDBACK_ALREADY_HANDLED);
        verify(retestMapper, never())
                .selectUnfinishedByFeedbackIdForUpdate(anyLong());
    }

    @Test
    void shouldRejectWhenAnySelectedKnowledgeIsNotReady() {
        AiChatFeedback feedback = AiChatFeedback.builder()
                .id(8L)
                .result(AiChatFeedbackResult.UNSOLVED)
                .handleStatus(AiChatFeedbackHandleStatus.PENDING)
                .build();
        when(feedbackMapper.selectByIdForUpdate(8L)).thenReturn(feedback);
        when(retestMapper.selectUnfinishedByFeedbackIdForUpdate(8L)).thenReturn(null);
        when(knowledgeMapper.countReadyCurrentByKeys(
                Collections.singletonList("ORDER_CANCEL"),
                "embedding-3", 256)).thenReturn(0);

        assertThatThrownBy(() -> service.submitRetest(8L, retestSubmit()))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_FEEDBACK_RETEST_KNOWLEDGE_UNAVAILABLE);

        verify(relationMapper, never()).deleteByFeedbackId(anyLong());
        verify(retestMapper, never()).insert(any(AiFeedbackRetest.class));
    }

    @Test
    void shouldReturnLatestRetestDetails() {
        AiChatFeedback feedback = pendingRetestFeedback();
        AiFeedbackRetest retest = succeededRetest();
        when(feedbackMapper.selectById(8L)).thenReturn(feedback);
        when(retestMapper.selectLatestByFeedbackId(8L)).thenReturn(retest);

        AiFeedbackRetestDetailVO result = service.getLatestRetest(8L);

        assertThat(result.getRetestId()).isEqualTo(91L);
        assertThat(result.getQuestion()).isEqualTo("待接单订单能取消吗？");
        assertThat(result.getAnswer()).isEqualTo("可以取消。");
        assertThat(result.getExecutionStatus())
                .isEqualTo(AiFeedbackRetestExecutionStatus.SUCCEEDED);
        assertThat(result.getUsedKnowledgeIds()).isEqualTo("[31]");
    }

    @Test
    void shouldConfirmCorrectAndMarkFeedbackHandled() {
        AiChatFeedback feedback = pendingRetestFeedback();
        AiFeedbackRetest retest = succeededRetest();
        when(feedbackMapper.selectByIdForUpdate(8L)).thenReturn(feedback);
        when(retestMapper.selectByIdForUpdate(91L)).thenReturn(retest);
        when(retestMapper.markReviewed(
                eq(91L), eq(1), eq(77L), any(LocalDateTime.class)))
                .thenReturn(1);
        when(feedbackMapper.markHandled(
                eq(8L), eq(77L), any(LocalDateTime.class))).thenReturn(1);

        AiFeedbackRetestDetailVO result = service.reviewRetest(
                8L, 91L, review(AiFeedbackRetestReviewResult.CORRECT));

        assertThat(result.getReviewResult())
                .isEqualTo(AiFeedbackRetestReviewResult.CORRECT);
        assertThat(result.getHandleStatus())
                .isEqualTo(AiChatFeedbackHandleStatus.HANDLED);
        assertThat(result.getMessage())
                .isEqualTo(MessageConstant.AI_FEEDBACK_RETEST_CONFIRMED_CORRECT);
        verify(feedbackMapper).markHandled(
                eq(8L), eq(77L), any(LocalDateTime.class));
    }

    @Test
    void shouldKeepFeedbackOpenWhenAnswerIsStillIncorrect() {
        AiChatFeedback feedback = pendingRetestFeedback();
        AiFeedbackRetest retest = succeededRetest();
        when(feedbackMapper.selectByIdForUpdate(8L)).thenReturn(feedback);
        when(retestMapper.selectByIdForUpdate(91L)).thenReturn(retest);
        when(retestMapper.markReviewed(
                eq(91L), eq(0), eq(77L), any(LocalDateTime.class)))
                .thenReturn(1);

        AiFeedbackRetestDetailVO result = service.reviewRetest(
                8L, 91L, review(AiFeedbackRetestReviewResult.INCORRECT));

        assertThat(result.getReviewResult())
                .isEqualTo(AiFeedbackRetestReviewResult.INCORRECT);
        assertThat(result.getHandleStatus())
                .isEqualTo(AiChatFeedbackHandleStatus.PENDING_RETEST);
        verify(feedbackMapper, never()).markHandled(
                anyLong(), anyLong(), any(LocalDateTime.class));
    }

    @Test
    void shouldRejectReviewBeforeRetestSucceeds() {
        AiChatFeedback feedback = pendingRetestFeedback();
        AiFeedbackRetest retest = succeededRetest();
        retest.setExecutionStatus(AiFeedbackRetestExecutionStatus.PROCESSING);
        when(feedbackMapper.selectByIdForUpdate(8L)).thenReturn(feedback);
        when(retestMapper.selectByIdForUpdate(91L)).thenReturn(retest);

        assertThatThrownBy(() -> service.reviewRetest(
                8L, 91L, review(AiFeedbackRetestReviewResult.CORRECT)))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_FEEDBACK_RETEST_NOT_REVIEWABLE);

        verify(retestMapper, never()).markReviewed(
                anyLong(), anyInt(), anyLong(), any(LocalDateTime.class));
    }

    @Test
    void shouldReturnOriginalDecisionWhenReviewIsRepeated() {
        AiChatFeedback feedback = pendingRetestFeedback();
        AiFeedbackRetest retest = succeededRetest();
        retest.setReviewResult(AiFeedbackRetestReviewResult.INCORRECT);
        when(feedbackMapper.selectByIdForUpdate(8L)).thenReturn(feedback);
        when(retestMapper.selectByIdForUpdate(91L)).thenReturn(retest);

        AiFeedbackRetestDetailVO result = service.reviewRetest(
                8L, 91L, review(AiFeedbackRetestReviewResult.CORRECT));

        assertThat(result.getReviewResult())
                .isEqualTo(AiFeedbackRetestReviewResult.INCORRECT);
        assertThat(result.getMessage())
                .isEqualTo(MessageConstant.AI_FEEDBACK_RETEST_ALREADY_REVIEWED);
        verify(retestMapper, never()).markReviewed(
                anyLong(), anyInt(), anyLong(), any(LocalDateTime.class));
        verify(feedbackMapper, never()).markHandled(
                anyLong(), anyLong(), any(LocalDateTime.class));
    }

    private AiChatFeedbackPageQueryDTO query(
            int page, int pageSize, LocalDate begin, LocalDate end) {
        AiChatFeedbackPageQueryDTO query = new AiChatFeedbackPageQueryDTO();
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setBegin(begin);
        query.setEnd(end);
        return query;
    }

    private AiFeedbackRetestSubmitDTO retestSubmit() {
        AiFeedbackRetestSubmitDTO submit = new AiFeedbackRetestSubmitDTO();
        submit.setQuestion("待接单订单能取消吗？");
        submit.setKnowledgeKeys(Collections.singletonList("ORDER_CANCEL"));
        return submit;
    }

    private AiFeedbackRetestReviewDTO review(
            AiFeedbackRetestReviewResult reviewResult) {
        AiFeedbackRetestReviewDTO review = new AiFeedbackRetestReviewDTO();
        review.setReviewResult(reviewResult);
        return review;
    }

    private AiChatFeedback pendingRetestFeedback() {
        return AiChatFeedback.builder()
                .id(8L)
                .result(AiChatFeedbackResult.UNSOLVED)
                .handleStatus(AiChatFeedbackHandleStatus.PENDING_RETEST)
                .build();
    }

    private AiFeedbackRetest succeededRetest() {
        return AiFeedbackRetest.builder()
                .id(91L)
                .feedbackId(8L)
                .question("待接单订单能取消吗？")
                .answer("可以取消。")
                .usedKnowledgeIds("[31]")
                .executionStatus(AiFeedbackRetestExecutionStatus.SUCCEEDED)
                .retryCount(0)
                .maxRetryCount(3)
                .build();
    }
}
