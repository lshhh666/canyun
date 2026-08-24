package com.sky.service.impl;

import com.sky.constant.OrderCompensationConstant;
import com.sky.entity.OrderCompensationTask;
import com.sky.mapper.OrderCompensationTaskMapper;
import com.sky.service.OrderCompensationService;
import com.sky.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单补偿任务服务实现
 */
@Service
@Slf4j
public class OrderCompensationServiceImpl implements OrderCompensationService {

    private final OrderCompensationTaskMapper orderCompensationTaskMapper;
    private final OrderService orderService;

    public OrderCompensationServiceImpl(OrderCompensationTaskMapper orderCompensationTaskMapper,
                                        OrderService orderService) {
        this.orderCompensationTaskMapper = orderCompensationTaskMapper;
        this.orderService = orderService;
    }

    /**
     * 使用独立事务保存失败记录，避免订单取消事务回滚时把补偿记录一起回滚。
     * 同一订单的同类任务已存在时，Mapper 只刷新最近失败信息，不重置重试状态。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordTimeoutCancelFailure(Long orderId, Long userCouponId, String failedReason) {
        if (orderId == null) {
            throw new IllegalArgumentException("记录订单补偿任务时，订单ID不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        String reason = normalizeFailedReason(failedReason);

        OrderCompensationTask task = OrderCompensationTask.builder()
                .orderId(orderId)
                .userCouponId(userCouponId)
                .taskType(OrderCompensationConstant.TASK_TYPE_TIMEOUT_CANCEL)
                .status(OrderCompensationConstant.STATUS_PENDING)
                .retryCount(0)
                .nextRetryTime(now.plusMinutes(OrderCompensationConstant.RETRY_INTERVAL_MINUTES))
                .firstFailedTime(now)
                .lastFailedTime(now)
                .failedReason(reason)
                .createTime(now)
                .updateTime(now)
                .build();

        orderCompensationTaskMapper.insertOrUpdateFailure(task);
    }

    /**
     * 扫描一小批到期任务并逐条处理。
     * 本方法不使用批量事务，每次订单取消仍由OrderService自己的事务独立提交。
     */
    @Override
    public void processDueTasks() {
        LocalDateTime scanTime = LocalDateTime.now().withNano(0);
        int recovered = orderCompensationTaskMapper.recoverStaleProcessingTasks(
                OrderCompensationConstant.TASK_TYPE_TIMEOUT_CANCEL,
                scanTime.minusMinutes(OrderCompensationConstant.PROCESSING_TIMEOUT_MINUTES),
                scanTime,
                scanTime,
                OrderCompensationConstant.SCAN_BATCH_SIZE
        );
        if (recovered > 0) {
            log.warn("恢复超时未完成的订单补偿任务，数量={}", recovered);
        }

        List<OrderCompensationTask> tasks = orderCompensationTaskMapper.findDuePendingTasks(
                OrderCompensationConstant.TASK_TYPE_TIMEOUT_CANCEL,
                scanTime,
                OrderCompensationConstant.SCAN_BATCH_SIZE
        );

        for (OrderCompensationTask task : tasks) {
            try {
                processOneTask(task);
            } catch (RuntimeException ex) {
                // 单个任务的查询或状态更新异常不能阻塞同批其他任务
                log.error("订单补偿任务处理异常，taskId={}, orderId={}",
                        task.getId(), task.getOrderId(), ex);
            }
        }
    }

    /** 处理一条候选任务：先抢占，抢占成功后再执行业务。 */
    private void processOneTask(OrderCompensationTask task) {
        LocalDateTime processingTime = LocalDateTime.now().withNano(0);
        int claimed = orderCompensationTaskMapper.claimPendingTask(task.getId(), processingTime);
        if (claimed != 1) {
            return;
        }

        try {
            // 重做完整取消流程，不能只释放优惠券；已支付或已取消时该方法会幂等返回
            orderService.cancelTimeoutOrder(task.getOrderId());
        } catch (RuntimeException ex) {
            recordRetryFailure(task, processingTime, ex);
            return;
        }

        LocalDateTime successTime = LocalDateTime.now().withNano(0);
        int updated = orderCompensationTaskMapper.markTaskSuccess(
                task.getId(), processingTime, successTime);
        if (updated != 1) {
            throw new IllegalStateException("补偿成功后更新任务状态失败，taskId=" + task.getId());
        }
        log.info("订单补偿任务处理成功，taskId={}, orderId={}", task.getId(), task.getOrderId());
    }

    /** 记录一次实际补偿失败，并根据累计次数决定继续等待还是转人工。 */
    private void recordRetryFailure(OrderCompensationTask task,
                                    LocalDateTime processingTime,
                                    RuntimeException failure) {
        LocalDateTime failedTime = LocalDateTime.now().withNano(0);
        String reason = normalizeFailedReason(failure.toString());
        int updated = orderCompensationTaskMapper.markTaskRetryFailure(
                task.getId(),
                processingTime,
                reason,
                failedTime,
                failedTime.plusMinutes(OrderCompensationConstant.RETRY_INTERVAL_MINUTES),
                OrderCompensationConstant.MAX_RETRY_COUNT
        );
        if (updated != 1) {
            throw new IllegalStateException("补偿失败后更新任务状态失败，taskId=" + task.getId(), failure);
        }
        log.warn("订单补偿任务执行失败，taskId={}, orderId={}",
                task.getId(), task.getOrderId(), failure);
    }

    /**
     * 数据库失败原因不能为空；异常没有消息时保留一个可识别的兜底描述。
     */
    private String normalizeFailedReason(String failedReason) {
        if (failedReason == null || failedReason.trim().isEmpty()) {
            return "未提供失败原因";
        }
        return failedReason;
    }
}
