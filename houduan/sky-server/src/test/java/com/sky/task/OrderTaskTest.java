package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.service.OrderCompensationService;
import com.sky.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 订单定时任务的补偿接入测试
 */
class OrderTaskTest {

    private OrderMapper orderMapper;
    private OrderService orderService;
    private OrderCompensationService orderCompensationService;
    private OrderTask orderTask;

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        orderService = mock(OrderService.class);
        orderCompensationService = mock(OrderCompensationService.class);
        orderTask = new OrderTask();

        ReflectionTestUtils.setField(orderTask, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(orderTask, "orderService", orderService);
        ReflectionTestUtils.setField(orderTask, "orderCompensationService", orderCompensationService);
    }

    @Test
    void shouldNotCreateCompensationWhenCancellationFinishesNormally() {
        Orders order = order(1001L, 2001L);
        when(orderMapper.getTimeoutOrdersWithoutActiveCompensation(any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(order));

        orderTask.processTimeoutOrder();

        verify(orderService).cancelTimeoutOrder(1001L);
        verifyNoInteractions(orderCompensationService);
    }

    @Test
    void shouldRecordCompensationAndContinueAfterCancellationFailure() {
        Orders failedOrder = order(1002L, 2002L);
        Orders nextOrder = order(1003L, null);
        RuntimeException cancelFailure = new RuntimeException("释放优惠券失败");
        when(orderMapper.getTimeoutOrdersWithoutActiveCompensation(any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(failedOrder, nextOrder));
        doThrow(cancelFailure).when(orderService).cancelTimeoutOrder(1002L);

        assertDoesNotThrow(orderTask::processTimeoutOrder);

        verify(orderCompensationService).recordTimeoutCancelFailure(
                1002L,
                2002L,
                cancelFailure.toString()
        );
        verify(orderService).cancelTimeoutOrder(1003L);
    }

    @Test
    void shouldContinueAfterCompensationRecordAlsoFails() {
        Orders failedOrder = order(1004L, 2004L);
        Orders nextOrder = order(1005L, null);
        RuntimeException cancelFailure = new RuntimeException("订单取消事务失败");
        when(orderMapper.getTimeoutOrdersWithoutActiveCompensation(any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(failedOrder, nextOrder));
        doThrow(cancelFailure).when(orderService).cancelTimeoutOrder(1004L);
        doThrow(new RuntimeException("补偿表不可用"))
                .when(orderCompensationService)
                .recordTimeoutCancelFailure(1004L, 2004L, cancelFailure.toString());

        assertDoesNotThrow(orderTask::processTimeoutOrder);

        verify(orderService).cancelTimeoutOrder(1005L);
    }

    /** 创建测试订单，只设置定时任务关心的字段。 */
    private Orders order(Long orderId, Long userCouponId) {
        Orders order = new Orders();
        order.setId(orderId);
        order.setUserCouponId(userCouponId);
        return order;
    }
}
