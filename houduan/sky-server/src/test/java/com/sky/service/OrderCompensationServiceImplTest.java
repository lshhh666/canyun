package com.sky.service;

import com.sky.constant.OrderCompensationConstant;
import com.sky.entity.OrderCompensationTask;
import com.sky.mapper.OrderCompensationTaskMapper;
import com.sky.service.impl.OrderCompensationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

/**
 * 订单补偿失败记录服务测试
 */
class OrderCompensationServiceImplTest {

    private OrderCompensationTaskMapper orderCompensationTaskMapper;
    private OrderService orderService;
    private OrderCompensationServiceImpl orderCompensationService;

    @BeforeEach
    void setUp() {
        orderCompensationTaskMapper = mock(OrderCompensationTaskMapper.class);
        orderService = mock(OrderService.class);
        orderCompensationService = new OrderCompensationServiceImpl(
                orderCompensationTaskMapper, orderService);
    }

    @Test
    void shouldBuildPendingTaskForTimeoutCancelFailure() {
        orderCompensationService.recordTimeoutCancelFailure(1001L, 2001L, "释放优惠券失败");

        ArgumentCaptor<OrderCompensationTask> captor = ArgumentCaptor.forClass(OrderCompensationTask.class);
        verify(orderCompensationTaskMapper).insertOrUpdateFailure(captor.capture());

        OrderCompensationTask task = captor.getValue();
        assertEquals(1001L, task.getOrderId());
        assertEquals(2001L, task.getUserCouponId());
        assertEquals(OrderCompensationConstant.TASK_TYPE_TIMEOUT_CANCEL, task.getTaskType());
        assertEquals(OrderCompensationConstant.STATUS_PENDING, task.getStatus());
        assertEquals(0, task.getRetryCount());
        assertEquals("释放优惠券失败", task.getFailedReason());
        assertEquals(task.getFirstFailedTime(), task.getLastFailedTime());
        assertEquals(task.getFirstFailedTime(), task.getCreateTime());
        assertEquals(task.getFirstFailedTime(), task.getUpdateTime());
        assertEquals(
                task.getFirstFailedTime().plusMinutes(OrderCompensationConstant.RETRY_INTERVAL_MINUTES),
                task.getNextRetryTime()
        );
        assertNull(task.getProcessingTime());
        assertNull(task.getSuccessTime());
    }

    @Test
    void shouldUseFallbackReasonWhenReasonIsBlank() {
        orderCompensationService.recordTimeoutCancelFailure(1002L, null, "   ");

        ArgumentCaptor<OrderCompensationTask> captor = ArgumentCaptor.forClass(OrderCompensationTask.class);
        verify(orderCompensationTaskMapper).insertOrUpdateFailure(captor.capture());

        assertEquals("未提供失败原因", captor.getValue().getFailedReason());
        assertNull(captor.getValue().getUserCouponId());
    }

    @Test
    void shouldRejectMissingOrderIdBeforeCallingMapper() {
        assertThrows(
                IllegalArgumentException.class,
                () -> orderCompensationService.recordTimeoutCancelFailure(null, 2003L, "测试异常")
        );

        verifyNoInteractions(orderCompensationTaskMapper);
    }

    @Test
    void shouldDeclareRequiresNewTransaction() throws NoSuchMethodException {
        Method method = OrderCompensationServiceImpl.class.getMethod(
                "recordTimeoutCancelFailure",
                Long.class,
                Long.class,
                String.class
        );

        Transactional transactional = method.getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
        assertArrayEquals(new Class<?>[]{Exception.class}, transactional.rollbackFor());
    }

    @Test
    void shouldClaimAndCompleteDueTask() {
        OrderCompensationTask task = dueTask(3001L, 4001L);
        when(orderCompensationTaskMapper.findDuePendingTasks(
                eq(OrderCompensationConstant.TASK_TYPE_TIMEOUT_CANCEL),
                any(LocalDateTime.class),
                eq(OrderCompensationConstant.SCAN_BATCH_SIZE)))
                .thenReturn(Collections.singletonList(task));
        when(orderCompensationTaskMapper.recoverStaleProcessingTasks(
                anyInt(), any(), any(), any(), anyInt())).thenReturn(0);
        when(orderCompensationTaskMapper.claimPendingTask(eq(3001L), any(LocalDateTime.class)))
                .thenReturn(1);
        when(orderCompensationTaskMapper.markTaskSuccess(
                eq(3001L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);

        orderCompensationService.processDueTasks();

        verify(orderService).cancelTimeoutOrder(4001L);
        verify(orderCompensationTaskMapper).markTaskSuccess(
                eq(3001L), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void shouldSkipCandidateWhenAnotherInstanceClaimsItFirst() {
        OrderCompensationTask task = dueTask(3002L, 4002L);
        when(orderCompensationTaskMapper.findDuePendingTasks(anyInt(), any(), anyInt()))
                .thenReturn(Collections.singletonList(task));
        when(orderCompensationTaskMapper.claimPendingTask(eq(3002L), any(LocalDateTime.class)))
                .thenReturn(0);

        orderCompensationService.processDueTasks();

        verifyNoInteractions(orderService);
    }

    @Test
    void shouldRecordRetryFailureWhenCancellationFails() {
        OrderCompensationTask task = dueTask(3003L, 4003L);
        RuntimeException failure = new RuntimeException("释放优惠券失败");
        when(orderCompensationTaskMapper.findDuePendingTasks(anyInt(), any(), anyInt()))
                .thenReturn(Collections.singletonList(task));
        when(orderCompensationTaskMapper.claimPendingTask(eq(3003L), any(LocalDateTime.class)))
                .thenReturn(1);
        doThrow(failure).when(orderService).cancelTimeoutOrder(4003L);
        when(orderCompensationTaskMapper.markTaskRetryFailure(
                eq(3003L), any(LocalDateTime.class), eq(failure.toString()), any(LocalDateTime.class),
                any(LocalDateTime.class), eq(OrderCompensationConstant.MAX_RETRY_COUNT)))
                .thenReturn(1);

        orderCompensationService.processDueTasks();

        verify(orderCompensationTaskMapper).markTaskRetryFailure(
                eq(3003L), any(LocalDateTime.class), eq(failure.toString()), any(LocalDateTime.class),
                any(LocalDateTime.class), eq(OrderCompensationConstant.MAX_RETRY_COUNT));
    }

    private OrderCompensationTask dueTask(Long taskId, Long orderId) {
        return OrderCompensationTask.builder()
                .id(taskId)
                .orderId(orderId)
                .taskType(OrderCompensationConstant.TASK_TYPE_TIMEOUT_CANCEL)
                .status(OrderCompensationConstant.STATUS_PENDING)
                .retryCount(0)
                .build();
    }
}
