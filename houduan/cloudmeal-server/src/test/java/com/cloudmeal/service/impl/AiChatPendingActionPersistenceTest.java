package com.cloudmeal.service.impl;

import com.cloudmeal.entity.AiChatPendingAction;
import com.cloudmeal.entity.AiChatMessage;
import com.cloudmeal.entity.AiChatSession;
import com.cloudmeal.enums.AiChatSessionStatus;
import com.cloudmeal.enums.AiPendingActionType;
import com.cloudmeal.mapper.AiChatMessageMapper;
import com.cloudmeal.mapper.AiChatPendingActionMapper;
import com.cloudmeal.mapper.AiChatSessionMapper;
import com.cloudmeal.properties.AiProperties;
import com.cloudmeal.service.model.AiChatTurnClaim;
import com.cloudmeal.service.model.AiChatHistorySnapshot;
import com.cloudmeal.service.model.AiPendingActionDraft;
import com.cloudmeal.service.model.AiPendingActionReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatPendingActionPersistenceTest {

    @Mock private AiChatSessionMapper sessionMapper;
    @Mock private AiChatMessageMapper messageMapper;
    @Mock private AiChatPendingActionMapper pendingActionMapper;
    private AiChatPersistenceServiceImpl service;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        properties.setActionConfirmTimeoutSeconds(300);
        properties.setTurnTimeoutSeconds(30);
        service = new AiChatPersistenceServiceImpl(
                sessionMapper, messageMapper, pendingActionMapper, properties);
    }

    @Test
    void shouldPersistPromptActionAndSessionCompletionAsOneServiceTransaction() {
        AiChatTurnClaim claim = new AiChatTurnClaim(25L, 3);
        when(sessionMapper.selectOwnedForUpdate(25L, 7L)).thenReturn(
                AiChatSession.builder().id(25L).userId(7L)
                        .status(AiChatSessionStatus.PROCESSING)
                        .processingSequenceNo(3).build());
        when(messageMapper.selectNextSequenceNo(25L)).thenReturn(4);
        doAnswer(invocation -> {
            AiChatMessage message = invocation.getArgument(0);
            message.setId(45L);
            return 1;
        }).when(messageMapper).insert(any(AiChatMessage.class));
        when(sessionMapper.completeProcessingTurn(eq(25L), eq(7L), eq(3),
                eq(null), any())).thenReturn(1);
        doAnswer(invocation -> {
            AiChatPendingAction action = invocation.getArgument(0);
            action.setId(35L);
            return 1;
        }).when(pendingActionMapper).insert(any(AiChatPendingAction.class));

        AiPendingActionReceipt receipt =
                service.saveAssistantMessageWithPendingAction(
                        claim, 7L, "确定要取消吗？", null,
                        AiPendingActionDraft.builder()
                                .actionType(AiPendingActionType.CANCEL_ORDER)
                                .targetOrderId(99L).build());

        assertThat(receipt.getActionId()).isEqualTo(35L);
        ArgumentCaptor<AiChatPendingAction> actionCaptor =
                ArgumentCaptor.forClass(AiChatPendingAction.class);
        org.mockito.InOrder order = inOrder(
                sessionMapper, pendingActionMapper, messageMapper);
        order.verify(sessionMapper).selectOwnedForUpdate(25L, 7L);
        order.verify(pendingActionMapper).supersedePending(
                eq(25L), eq(7L), any(), any());
        order.verify(messageMapper).insert(any(AiChatMessage.class));
        order.verify(pendingActionMapper).insert(actionCaptor.capture());
        order.verify(sessionMapper).completeProcessingTurn(
                eq(25L), eq(7L), eq(3), eq(null), any());
        assertThat(actionCaptor.getValue().getAssistantMessageId()).isEqualTo(45L);
    }

    @Test
    void shouldLoadLatestOwnedHistoryAndItsRestorableAction() {
        LocalDateTime deadline = LocalDateTime.now().plusMinutes(1);
        AiChatSession session = AiChatSession.builder()
                .id(25L)
                .userId(7L)
                .status(AiChatSessionStatus.PROCESSING)
                .processingDeadline(deadline)
                .build();
        AiChatMessage message = AiChatMessage.builder()
                .id(45L)
                .sessionId(25L)
                .sequenceNo(4)
                .build();
        AiChatPendingAction action = AiChatPendingAction.builder()
                .id(35L)
                .assistantMessageId(45L)
                .build();
        when(sessionMapper.selectLatestContinuable(7L)).thenReturn(session);
        when(messageMapper.selectRecentMessages(25L, 50))
                .thenReturn(Collections.singletonList(message));
        when(pendingActionMapper.selectRestorable(eq(25L), eq(7L), any()))
                .thenReturn(action);

        AiChatHistorySnapshot result = service.loadLatestHistory(7L, 50);

        assertThat(result.getSession()).isSameAs(session);
        assertThat(result.getMessages()).containsExactly(message);
        assertThat(result.getPendingAction()).isSameAs(action);
        assertThat(result.isProcessing()).isTrue();
    }

    @Test
    void shouldCloseActiveSessionAndSupersedeItsPendingAction() {
        when(sessionMapper.selectOwnedForUpdate(25L, 7L)).thenReturn(
                AiChatSession.builder().id(25L).userId(7L)
                        .status(AiChatSessionStatus.ACTIVE).build());
        when(sessionMapper.closeSession(eq(25L), eq(7L),
                eq(AiChatSessionStatus.ACTIVE.getValue()), any())).thenReturn(1);

        service.closeForNewConversation(25L, 7L);

        org.mockito.InOrder order = inOrder(
                sessionMapper, pendingActionMapper);
        order.verify(sessionMapper).selectOwnedForUpdate(25L, 7L);
        order.verify(pendingActionMapper).supersedePending(
                eq(25L), eq(7L), any(), any());
        order.verify(sessionMapper).closeSession(eq(25L), eq(7L),
                eq(AiChatSessionStatus.ACTIVE.getValue()), any());
    }

    @Test
    void shouldRejectNewConversationWhileCurrentTurnIsStillProcessing() {
        when(sessionMapper.selectOwnedForUpdate(25L, 7L)).thenReturn(
                AiChatSession.builder().id(25L).userId(7L)
                        .status(AiChatSessionStatus.PROCESSING)
                        .processingDeadline(LocalDateTime.now().plusMinutes(1))
                        .build());

        assertThatThrownBy(() -> service.closeForNewConversation(25L, 7L))
                .isInstanceOf(com.cloudmeal.exception.BaseException.class)
                .hasMessage(com.cloudmeal.constant.MessageConstant.AI_CHAT_REPLY_PENDING);
        verify(pendingActionMapper, never()).supersedePending(
                any(), any(), any(), any());
        verify(sessionMapper, never()).closeSession(any(), any(), any(Integer.class), any());
    }

    @Test
    void shouldCloseAndHideActiveSessionAfterThirtyMinutesIdle() {
        AiChatSession expired = AiChatSession.builder()
                .id(25L).userId(7L)
                .status(AiChatSessionStatus.ACTIVE)
                .updateTime(LocalDateTime.now().minusMinutes(31))
                .build();
        when(sessionMapper.selectLatestContinuable(7L)).thenReturn(expired);
        when(sessionMapper.selectOwnedForUpdate(25L, 7L)).thenReturn(expired);
        when(sessionMapper.closeSession(eq(25L), eq(7L),
                eq(AiChatSessionStatus.ACTIVE.getValue()), any())).thenReturn(1);

        AiChatHistorySnapshot result = service.loadLatestHistory(7L, 8);

        assertThat(result.getSession()).isNull();
        assertThat(result.getMessages()).isEmpty();
        assertThat(result.isProcessing()).isFalse();
        verify(pendingActionMapper).supersedePending(
                eq(25L), eq(7L), any(), any());
        verify(messageMapper, never()).selectRecentMessages(any(), any(Integer.class));
    }

    @Test
    void shouldReplaceExpiredSessionWhenPageSubmitsItsOldSessionId() {
        AiChatSession expired = AiChatSession.builder()
                .id(25L).userId(7L)
                .status(AiChatSessionStatus.ACTIVE)
                .updateTime(LocalDateTime.now().minusMinutes(31))
                .build();
        when(sessionMapper.selectOwnedForUpdate(25L, 7L)).thenReturn(expired);
        when(sessionMapper.closeSession(eq(25L), eq(7L),
                eq(AiChatSessionStatus.ACTIVE.getValue()), any())).thenReturn(1);
        doAnswer(invocation -> {
            AiChatSession session = invocation.getArgument(0);
            session.setId(26L);
            return 1;
        }).when(sessionMapper).insert(any(AiChatSession.class));
        when(messageMapper.insert(any(AiChatMessage.class))).thenReturn(1);

        AiChatTurnClaim result = service.saveUserMessage(25L, 7L, "新的问题");

        assertThat(result.getSessionId()).isEqualTo(26L);
        assertThat(result.getProcessingSequenceNo()).isEqualTo(1);
        verify(sessionMapper, never()).claimProcessingTurn(
                eq(25L), eq(7L), any(Integer.class), any(Integer.class), any(), any());
    }
}
