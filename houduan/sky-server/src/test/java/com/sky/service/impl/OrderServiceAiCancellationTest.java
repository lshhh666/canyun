package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.enums.AiOrderCancellationOutcome;
import com.sky.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceAiCancellationTest {

    @Mock private OrderMapper orderMapper;
    @InjectMocks private OrderServiceImpl service;

    @Test
    void shouldCancelOnlyUnpaidPendingOwnedOrder() {
        Orders order = Orders.builder().id(99L).userId(7L)
                .status(Orders.PENDING_PAYMENT).payStatus(Orders.UN_PAID).build();
        when(orderMapper.getById(99L)).thenReturn(order);
        when(orderMapper.cancelIfPending(org.mockito.ArgumentMatchers.eq(99L),
                any(), isNull())).thenReturn(1);

        assertThat(service.cancelPendingForAi(99L, 7L))
                .isEqualTo(AiOrderCancellationOutcome.CANCELLED);
    }

    @Test
    void shouldRejectPaidOrChangedOrderWithoutUpdating() {
        Orders order = Orders.builder().id(99L).userId(7L)
                .status(Orders.TO_BE_CONFIRMED).payStatus(Orders.PAID).build();
        when(orderMapper.getById(99L)).thenReturn(order);

        assertThat(service.cancelPendingForAi(99L, 7L))
                .isEqualTo(AiOrderCancellationOutcome.NOT_CANCELLABLE);
        verify(orderMapper, never()).cancelIfPending(any(), any(), any());
    }

    @Test
    void shouldTreatConcurrentSchedulerCancellationAsNormalResult() {
        Orders initial = Orders.builder().id(99L).userId(7L)
                .status(Orders.PENDING_PAYMENT).payStatus(Orders.UN_PAID).build();
        Orders cancelled = Orders.builder().id(99L).userId(7L)
                .status(Orders.CANCELLED).payStatus(Orders.UN_PAID).build();
        when(orderMapper.getById(99L)).thenReturn(initial, cancelled);
        when(orderMapper.cancelIfPending(org.mockito.ArgumentMatchers.eq(99L),
                any(), isNull())).thenReturn(0);

        assertThat(service.cancelPendingForAi(99L, 7L))
                .isEqualTo(AiOrderCancellationOutcome.ALREADY_CANCELLED);
    }
}
