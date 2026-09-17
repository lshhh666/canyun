package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.AiChatFeedbackDTO;
import com.sky.entity.AiChatFeedback;
import com.sky.entity.AiChatSession;
import com.sky.enums.AiChatFeedbackResult;
import com.sky.enums.AiChatSessionStatus;
import com.sky.exception.BaseException;
import com.sky.mapper.AiChatFeedbackMapper;
import com.sky.mapper.AiChatSessionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatFeedbackServiceImplTest {

    @Mock private AiChatSessionMapper sessionMapper;
    @Mock private AiChatFeedbackMapper feedbackMapper;
    private AiChatFeedbackServiceImpl service;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(7L);
        service = new AiChatFeedbackServiceImpl(sessionMapper, feedbackMapper);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void shouldSaveFeedbackForOwnedClosedSession() {
        when(sessionMapper.selectOwned(25L, 7L)).thenReturn(
                AiChatSession.builder().id(25L).userId(7L)
                        .status(AiChatSessionStatus.CLOSED).build());

        service.submit(AiChatFeedbackDTO.builder()
                .sessionId(25L)
                .result(AiChatFeedbackResult.UNSOLVED)
                .build());

        ArgumentCaptor<AiChatFeedback> captor =
                ArgumentCaptor.forClass(AiChatFeedback.class);
        verify(feedbackMapper).upsert(captor.capture(), eq(2));
        assertThat(captor.getValue().getSessionId()).isEqualTo(25L);
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getResult()).isEqualTo(AiChatFeedbackResult.UNSOLVED);
        assertThat(captor.getValue().getCreateTime()).isNotNull();
        assertThat(captor.getValue().getUpdateTime()).isNotNull();
    }

    @Test
    void changingFeedbackInvalidatesOldRetestAndKnowledgeRelations() {
        when(sessionMapper.selectOwned(25L, 7L)).thenReturn(
                AiChatSession.builder().id(25L).userId(7L)
                        .status(AiChatSessionStatus.CLOSED).build());
        when(feedbackMapper.selectBySessionIdForUpdate(25L)).thenReturn(
                AiChatFeedback.builder().id(91L).sessionId(25L)
                        .userId(7L).result(AiChatFeedbackResult.UNSOLVED).build());

        service.submit(AiChatFeedbackDTO.builder()
                .sessionId(25L)
                .result(AiChatFeedbackResult.HELPFUL)
                .build());

        verify(feedbackMapper).obsoleteActiveRetests(eq(91L), any());
        verify(feedbackMapper).deleteKnowledgeRelations(91L);
        verify(feedbackMapper).upsert(any(), eq(1));
    }

    @Test
    void repeatingSameFeedbackKeepsCurrentRetestContext() {
        when(sessionMapper.selectOwned(25L, 7L)).thenReturn(
                AiChatSession.builder().id(25L).userId(7L)
                        .status(AiChatSessionStatus.CLOSED).build());
        when(feedbackMapper.selectBySessionIdForUpdate(25L)).thenReturn(
                AiChatFeedback.builder().id(91L).sessionId(25L)
                        .userId(7L).result(AiChatFeedbackResult.UNSOLVED).build());

        service.submit(AiChatFeedbackDTO.builder()
                .sessionId(25L)
                .result(AiChatFeedbackResult.UNSOLVED)
                .build());

        verify(feedbackMapper, never()).obsoleteActiveRetests(any(), any());
        verify(feedbackMapper, never()).deleteKnowledgeRelations(any());
    }

    @Test
    void shouldRejectSessionOwnedByAnotherUser() {
        when(sessionMapper.selectOwned(25L, 7L)).thenReturn(null);

        assertThatThrownBy(() -> service.submit(AiChatFeedbackDTO.builder()
                .sessionId(25L).result(AiChatFeedbackResult.HELPFUL).build()))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_CHAT_SESSION_UNAVAILABLE);

        verify(feedbackMapper, never()).upsert(any(), any(Integer.class));
    }

    @Test
    void shouldRejectFeedbackBeforeSessionIsClosed() {
        when(sessionMapper.selectOwned(25L, 7L)).thenReturn(
                AiChatSession.builder().id(25L).userId(7L)
                        .status(AiChatSessionStatus.ACTIVE).build());

        assertThatThrownBy(() -> service.submit(AiChatFeedbackDTO.builder()
                .sessionId(25L).result(AiChatFeedbackResult.HELPFUL).build()))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_CHAT_FEEDBACK_SESSION_NOT_CLOSED);

        verify(feedbackMapper, never()).upsert(any(), any(Integer.class));
    }

    @Test
    void shouldHideDatabaseFailureBehindFriendlyMessage() {
        when(sessionMapper.selectOwned(25L, 7L)).thenReturn(
                AiChatSession.builder().id(25L).userId(7L)
                        .status(AiChatSessionStatus.CLOSED).build());
        when(feedbackMapper.upsert(any(), any(Integer.class)))
                .thenThrow(new DataIntegrityViolationException("database detail"));

        assertThatThrownBy(() -> service.submit(AiChatFeedbackDTO.builder()
                .sessionId(25L).result(AiChatFeedbackResult.HELPFUL).build()))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_CHAT_FEEDBACK_SAVE_FAILED);
    }
}
