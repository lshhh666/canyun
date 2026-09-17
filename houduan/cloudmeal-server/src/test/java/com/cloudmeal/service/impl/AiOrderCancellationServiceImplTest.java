package com.cloudmeal.service.impl;

import com.cloudmeal.entity.AiChatPendingAction;
import com.cloudmeal.entity.AiChatSession;
import com.cloudmeal.entity.Orders;
import com.cloudmeal.enums.AiOrderCancellationOutcome;
import com.cloudmeal.enums.AiPendingActionStatus;
import com.cloudmeal.enums.AiPendingActionType;
import com.cloudmeal.mapper.AiChatPendingActionMapper;
import com.cloudmeal.mapper.AiChatSessionMapper;
import com.cloudmeal.mapper.OrderMapper;
import com.cloudmeal.service.OrderService;
import com.cloudmeal.service.model.AiToolAnswer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiOrderCancellationServiceImplTest {

    @Mock private AiChatSessionMapper sessionMapper;
    @Mock private AiChatPendingActionMapper pendingActionMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private OrderService orderService;
    @InjectMocks private AiOrderCancellationServiceImpl service;

    @Test
    void shouldFreezeSelectedOrderIdIntoPendingAction() {
        when(sessionMapper.selectOwned(25L, 7L)).thenReturn(
                AiChatSession.builder().id(25L).userId(7L).selectedOrderId(99L).build());
        when(orderMapper.getAiCancellationByIdAndUserId(99L, 7L)).thenReturn(
                Orders.builder().id(99L).number("202609093346")
                        .status(Orders.PENDING_PAYMENT).payStatus(Orders.UN_PAID).build());

        AiToolAnswer result = service.prepareCancellation(25L, 7L);

        assertThat(result.getAnswer()).contains("尾号3346").contains("确定要取消");
        assertThat(result.getPendingAction().getActionType())
                .isEqualTo(AiPendingActionType.CANCEL_ORDER);
        assertThat(result.getPendingAction().getTargetOrderId()).isEqualTo(99L);
        verify(orderService, never()).cancelPendingForAi(any(), any());
    }

    @Test
    void shouldSignalOrderSelectionWhenSessionHasNoSelectedOrder() {
        when(sessionMapper.selectOwned(25L, 7L)).thenReturn(
                AiChatSession.builder().id(25L).userId(7L).build());

        AiToolAnswer result = service.prepareCancellation(25L, 7L);

        assertThat(result.isOrderSelectionRequired()).isTrue();
        assertThat(result.getPendingAction()).isNull();
        verify(orderMapper, never()).getAiCancellationByIdAndUserId(any(), any());
    }

    @Test
    void shouldPrepareCancellationForOrderResolvedFromCurrentUsersList() {
        when(orderMapper.getAiCancellationByIdAndUserId(99L, 7L)).thenReturn(
                Orders.builder().id(99L).number("202609105412")
                        .status(Orders.PENDING_PAYMENT).payStatus(Orders.UN_PAID).build());

        AiToolAnswer result = service.prepareOrderCancellation(99L, 7L);

        assertThat(result.getAnswer()).contains("尾号5412").contains("确定要取消");
        assertThat(result.getSelectedOrderId()).isEqualTo(99L);
        assertThat(result.getPendingAction().getTargetOrderId()).isEqualTo(99L);
    }

    @Test
    void shouldCheckExplicitOrderWithoutCreatingPendingAction() {
        when(orderMapper.getAiCancellationByIdAndUserId(99L, 7L)).thenReturn(
                Orders.builder().id(99L).number("202609093651")
                        .status(Orders.PENDING_PAYMENT).payStatus(Orders.UN_PAID).build());

        AiToolAnswer result = service.checkOrderCancellation(99L, 7L);

        assertThat(result.getAnswer()).contains("尾号3651").contains("可以取消");
        assertThat(result.getPendingAction()).isNull();
        verify(orderService, never()).cancelPendingForAi(any(), any());
    }

    @Test
    void shouldRecheckAndCancelOnlyWhenButtonIsConfirmed() {
        AiChatPendingAction action = pendingAction(AiPendingActionStatus.PENDING_CONFIRMATION,
                LocalDateTime.now().plusMinutes(5), null);
        when(pendingActionMapper.selectOwnedForUpdate(35L, 7L)).thenReturn(action);
        when(orderMapper.getAiCancellationByIdAndUserId(99L, 7L)).thenReturn(
                Orders.builder().id(99L).number("202609093346").build());
        when(orderService.cancelPendingForAi(99L, 7L))
                .thenReturn(AiOrderCancellationOutcome.CANCELLED);
        when(pendingActionMapper.completePending(eq(35L), eq(7L), eq(2),
                eq("订单尾号3346已取消。"), any(LocalDateTime.class))).thenReturn(1);

        String result = service.confirmCancellation(35L, 7L);

        assertThat(result).isEqualTo("订单尾号3346已取消。");
        verify(orderService).cancelPendingForAi(99L, 7L);
    }

    @Test
    void shouldReturnStoredResultForRepeatedConfirmation() {
        AiChatPendingAction action = pendingAction(AiPendingActionStatus.SUCCEEDED,
                LocalDateTime.now().minusMinutes(1), "订单尾号3346已取消。");
        when(pendingActionMapper.selectOwnedForUpdate(35L, 7L)).thenReturn(action);

        assertThat(service.confirmCancellation(35L, 7L))
                .isEqualTo("订单尾号3346已取消。");
        verify(orderService, never()).cancelPendingForAi(any(), any());
    }

    @Test
    void shouldExpireWithoutTouchingOrder() {
        AiChatPendingAction action = pendingAction(AiPendingActionStatus.PENDING_CONFIRMATION,
                LocalDateTime.now().minusSeconds(1), null);
        when(pendingActionMapper.selectOwnedForUpdate(35L, 7L)).thenReturn(action);
        when(pendingActionMapper.completePending(eq(35L), eq(7L), eq(4),
                eq("本次取消确认已过期，请重新发起取消。"), any(LocalDateTime.class)))
                .thenReturn(1);

        assertThat(service.confirmCancellation(35L, 7L)).contains("已过期");
        verify(orderService, never()).cancelPendingForAi(any(), any());
    }

    private AiChatPendingAction pendingAction(AiPendingActionStatus status,
                                              LocalDateTime expireTime,
                                              String result) {
        return AiChatPendingAction.builder()
                .id(35L).userId(7L).sessionId(25L)
                .actionType(AiPendingActionType.CANCEL_ORDER)
                .targetOrderId(99L).status(status)
                .expireTime(expireTime).resultMessage(result)
                .build();
    }
}
