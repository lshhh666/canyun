package com.sky.service;

import com.sky.constant.OrderCompensationConstant;
import com.sky.entity.OrderCompensationTask;
import com.sky.mapper.OrderCompensationTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 补偿任务 Mapper 真实数据库测试。
 * 每个测试都在事务中运行并自动回滚，不在数据库留下测试记录。
 */
@SpringBootTest(properties = "sky.websocket.enabled=false")
@Transactional
class OrderCompensationMapperIntegrationTest {

    @Autowired
    private OrderCompensationTaskMapper orderCompensationTaskMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldInsertPendingTaskAndStoreReasonLongerThanFiveHundredCharacters() {
        long orderId = uniqueOrderId();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        String longReason = repeat('异', 600);

        int affectedRows = orderCompensationTaskMapper.insertOrUpdateFailure(
                task(orderId, 2001L, now, longReason)
        );

        Map<String, Object> row = findTask(orderId);
        assertEquals(1, affectedRows);
        assertEquals(OrderCompensationConstant.STATUS_PENDING, number(row, "status"));
        assertEquals(0, number(row, "retry_count"));
        assertEquals(now.plusMinutes(OrderCompensationConstant.RETRY_INTERVAL_MINUTES),
                dateTime(row, "next_retry_time"));
        assertEquals(600, ((String) row.get("failed_reason")).length());
    }

    @Test
    void shouldOnlyRefreshLatestFailureFieldsWhenTaskAlreadyExists() {
        long orderId = uniqueOrderId();
        LocalDateTime firstFailure = LocalDateTime.now().minusMinutes(30).withNano(0);
        LocalDateTime originalNextRetry = firstFailure.plusMinutes(5);

        jdbcTemplate.update(
                "INSERT INTO order_compensation_task " +
                        "(order_id, user_coupon_id, task_type, status, retry_count, " +
                        "next_retry_time, processing_time, first_failed_time, last_failed_time, " +
                        "success_time, failed_reason, create_time, update_time) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                orderId,
                2002L,
                OrderCompensationConstant.TASK_TYPE_TIMEOUT_CANCEL,
                OrderCompensationConstant.STATUS_MANUAL,
                OrderCompensationConstant.MAX_RETRY_COUNT,
                originalNextRetry,
                null,
                firstFailure,
                firstFailure,
                null,
                "首次失败",
                firstFailure,
                firstFailure
        );

        LocalDateTime latestFailure = firstFailure.plusMinutes(20);
        OrderCompensationTask duplicate = task(orderId, 9999L, latestFailure, "最近一次失败");
        orderCompensationTaskMapper.insertOrUpdateFailure(duplicate);

        Map<String, Object> row = findTask(orderId);
        assertEquals(2002L, ((Number) row.get("user_coupon_id")).longValue());
        assertEquals(OrderCompensationConstant.STATUS_MANUAL, number(row, "status"));
        assertEquals(OrderCompensationConstant.MAX_RETRY_COUNT, number(row, "retry_count"));
        assertEquals(originalNextRetry, dateTime(row, "next_retry_time"));
        assertEquals(firstFailure, dateTime(row, "first_failed_time"));
        assertEquals("最近一次失败", row.get("failed_reason"));
        assertEquals(latestFailure, dateTime(row, "last_failed_time"));
        assertEquals(latestFailure, dateTime(row, "update_time"));
    }

    @Test
    void shouldAllowOnlyOneClaimForDuePendingTask() {
        long orderId = uniqueOrderId();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime firstFailure = now.minusMinutes(10);
        orderCompensationTaskMapper.insertOrUpdateFailure(
                task(orderId, 2003L, firstFailure, "等待补偿")
        );
        Long taskId = findTaskId(orderId);

        int firstClaim = orderCompensationTaskMapper.claimPendingTask(taskId, now);
        int secondClaim = orderCompensationTaskMapper.claimPendingTask(taskId, now);

        Map<String, Object> row = findTask(orderId);
        assertEquals(1, firstClaim);
        assertEquals(0, secondClaim);
        assertEquals(OrderCompensationConstant.STATUS_PROCESSING, number(row, "status"));
        assertEquals(now, dateTime(row, "processing_time"));
        assertEquals(now, dateTime(row, "update_time"));
    }

    @Test
    void shouldNotClaimPendingTaskBeforeNextRetryTime() {
        long orderId = uniqueOrderId();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        orderCompensationTaskMapper.insertOrUpdateFailure(
                task(orderId, 2004L, now, "尚未到重试时间")
        );
        Long taskId = findTaskId(orderId);

        int affectedRows = orderCompensationTaskMapper.claimPendingTask(taskId, now);

        Map<String, Object> row = findTask(orderId);
        assertEquals(0, affectedRows);
        assertEquals(OrderCompensationConstant.STATUS_PENDING, number(row, "status"));
        assertNull(row.get("processing_time"));
    }

    @Test
    void shouldFindOnlyDuePendingTasksInStableOrderAndRespectLimit() {
        // 使用远早于现有业务数据的固定时间，避免数据库中的真实待处理任务干扰测试结果。
        LocalDateTime now = LocalDateTime.of(2000, 1, 1, 0, 0);
        long baseOrderId = uniqueOrderId();
        long firstOrderId = baseOrderId;
        long secondOrderId = baseOrderId + 1;
        long limitedOutOrderId = baseOrderId + 2;
        long futureOrderId = baseOrderId + 3;
        long processingOrderId = baseOrderId + 4;

        insertTask(firstOrderId, OrderCompensationConstant.STATUS_PENDING,
                now.minusMinutes(10), now.minusMinutes(20));
        insertTask(secondOrderId, OrderCompensationConstant.STATUS_PENDING,
                now.minusMinutes(5), now.minusMinutes(15));
        insertTask(limitedOutOrderId, OrderCompensationConstant.STATUS_PENDING,
                now.minusMinutes(1), now.minusMinutes(10));
        insertTask(futureOrderId, OrderCompensationConstant.STATUS_PENDING,
                now.plusMinutes(5), now);
        insertTask(processingOrderId, OrderCompensationConstant.STATUS_PROCESSING,
                now.minusMinutes(30), now.minusMinutes(40));

        List<OrderCompensationTask> tasks =
                orderCompensationTaskMapper.findDuePendingTasks(
                        OrderCompensationConstant.TASK_TYPE_TIMEOUT_CANCEL, now, 2);

        assertEquals(2, tasks.size());
        assertEquals(firstOrderId, tasks.get(0).getOrderId());
        assertEquals(secondOrderId, tasks.get(1).getOrderId());
    }

    @Test
    void shouldMarkProcessingTaskAsSuccess() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        long orderId = uniqueOrderId();
        insertTask(orderId, OrderCompensationConstant.STATUS_PROCESSING,
                now.minusMinutes(5), now.minusMinutes(10));
        Long taskId = findTaskId(orderId);

        LocalDateTime processingTime = now.minusMinutes(10);
        int affectedRows = orderCompensationTaskMapper.markTaskSuccess(
                taskId, processingTime, now);

        Map<String, Object> row = findTask(orderId);
        assertEquals(1, affectedRows);
        assertEquals(OrderCompensationConstant.STATUS_SUCCESS, number(row, "status"));
        assertEquals(now.minusMinutes(5), dateTime(row, "next_retry_time"));
        assertNull(row.get("processing_time"));
        assertEquals(now, dateTime(row, "success_time"));
    }

    @Test
    void shouldRescheduleFailedProcessingTaskBeforeRetryLimit() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime nextRetryTime = now.plusMinutes(OrderCompensationConstant.RETRY_INTERVAL_MINUTES);
        long orderId = uniqueOrderId();
        insertTask(orderId, OrderCompensationConstant.STATUS_PROCESSING,
                now.minusMinutes(5), now.minusMinutes(10));
        Long taskId = findTaskId(orderId);

        int affectedRows = orderCompensationTaskMapper.markTaskRetryFailure(
                taskId, now.minusMinutes(10), "第一次自动补偿失败", now, nextRetryTime,
                OrderCompensationConstant.MAX_RETRY_COUNT);

        Map<String, Object> row = findTask(orderId);
        assertEquals(1, affectedRows);
        assertEquals(OrderCompensationConstant.STATUS_PENDING, number(row, "status"));
        assertEquals(1, number(row, "retry_count"));
        assertEquals(nextRetryTime, dateTime(row, "next_retry_time"));
        assertNull(row.get("processing_time"));
        assertEquals("第一次自动补偿失败", row.get("failed_reason"));
    }

    @Test
    void shouldMarkTaskManualOnThirdRetryFailure() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        long orderId = uniqueOrderId();
        insertTask(orderId, OrderCompensationConstant.STATUS_PROCESSING,
                now.minusMinutes(5), now.minusMinutes(10));
        jdbcTemplate.update(
                "UPDATE order_compensation_task SET retry_count = 2 WHERE order_id = ? AND task_type = ?",
                orderId, OrderCompensationConstant.TASK_TYPE_TIMEOUT_CANCEL);
        Long taskId = findTaskId(orderId);

        int affectedRows = orderCompensationTaskMapper.markTaskRetryFailure(
                taskId, now.minusMinutes(10), "第三次自动补偿失败", now,
                now.plusMinutes(OrderCompensationConstant.RETRY_INTERVAL_MINUTES),
                OrderCompensationConstant.MAX_RETRY_COUNT);

        Map<String, Object> row = findTask(orderId);
        assertEquals(1, affectedRows);
        assertEquals(OrderCompensationConstant.STATUS_MANUAL, number(row, "status"));
        assertEquals(3, number(row, "retry_count"));
        assertEquals(now.minusMinutes(5), dateTime(row, "next_retry_time"));
        assertNull(row.get("processing_time"));
        assertEquals("第三次自动补偿失败", row.get("failed_reason"));
    }

    @Test
    void shouldRecoverOnlyStaleProcessingTasksWithoutIncreasingRetryCount() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        long baseOrderId = uniqueOrderId();
        long staleOrderId = baseOrderId;
        long recentOrderId = baseOrderId + 1;

        insertTask(staleOrderId, OrderCompensationConstant.STATUS_PROCESSING,
                now.minusMinutes(20), now.minusMinutes(20));
        insertTask(recentOrderId, OrderCompensationConstant.STATUS_PROCESSING,
                now.minusMinutes(5), now.minusMinutes(5));

        int affectedRows = orderCompensationTaskMapper.recoverStaleProcessingTasks(
                OrderCompensationConstant.TASK_TYPE_TIMEOUT_CANCEL,
                now.minusMinutes(OrderCompensationConstant.PROCESSING_TIMEOUT_MINUTES),
                now,
                now,
                OrderCompensationConstant.SCAN_BATCH_SIZE
        );

        Map<String, Object> stale = findTask(staleOrderId);
        Map<String, Object> recent = findTask(recentOrderId);
        assertEquals(1, affectedRows);
        assertEquals(OrderCompensationConstant.STATUS_PENDING, number(stale, "status"));
        assertEquals(0, number(stale, "retry_count"));
        assertEquals(now, dateTime(stale, "next_retry_time"));
        assertNull(stale.get("processing_time"));
        assertEquals(OrderCompensationConstant.STATUS_PROCESSING, number(recent, "status"));
        assertEquals(now.minusMinutes(5), dateTime(recent, "processing_time"));
    }

    @Test
    void shouldRejectCompletionFromWorkerWhoseClaimHasExpired() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime currentClaimTime = now.minusMinutes(1);
        LocalDateTime expiredClaimTime = now.minusMinutes(20);
        long orderId = uniqueOrderId();
        insertTask(orderId, OrderCompensationConstant.STATUS_PROCESSING,
                now.minusMinutes(5), currentClaimTime);
        Long taskId = findTaskId(orderId);

        int affectedRows = orderCompensationTaskMapper.markTaskSuccess(
                taskId, expiredClaimTime, now);

        Map<String, Object> row = findTask(orderId);
        assertEquals(0, affectedRows);
        assertEquals(OrderCompensationConstant.STATUS_PROCESSING, number(row, "status"));
        assertEquals(currentClaimTime, dateTime(row, "processing_time"));
        assertNull(row.get("success_time"));
    }

    /** 创建一条首次失败时应保存的待处理任务。 */
    private OrderCompensationTask task(Long orderId,
                                       Long userCouponId,
                                       LocalDateTime now,
                                       String failedReason) {
        return OrderCompensationTask.builder()
                .orderId(orderId)
                .userCouponId(userCouponId)
                .taskType(OrderCompensationConstant.TASK_TYPE_TIMEOUT_CANCEL)
                .status(OrderCompensationConstant.STATUS_PENDING)
                .retryCount(0)
                .nextRetryTime(now.plusMinutes(OrderCompensationConstant.RETRY_INTERVAL_MINUTES))
                .firstFailedTime(now)
                .lastFailedTime(now)
                .failedReason(failedReason)
                .createTime(now)
                .updateTime(now)
                .build();
    }

    /** 查询当前测试订单对应的补偿任务。 */
    private Map<String, Object> findTask(long orderId) {
        return jdbcTemplate.queryForMap(
                "SELECT user_coupon_id, status, retry_count, next_retry_time, " +
                        "processing_time, first_failed_time, last_failed_time, success_time, " +
                        "failed_reason, update_time " +
                        "FROM order_compensation_task WHERE order_id = ? AND task_type = ?",
                orderId,
                OrderCompensationConstant.TASK_TYPE_TIMEOUT_CANCEL
        );
    }

    private Long findTaskId(long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM order_compensation_task WHERE order_id = ? AND task_type = ?",
                Long.class,
                orderId,
                OrderCompensationConstant.TASK_TYPE_TIMEOUT_CANCEL
        );
    }

    /** 插入指定状态和到期时间的任务，用于验证扫描条件与排序。 */
    private void insertTask(long orderId,
                            int status,
                            LocalDateTime nextRetryTime,
                            LocalDateTime firstFailedTime) {
        LocalDateTime updateTime = firstFailedTime;
        OrderCompensationTask task = OrderCompensationTask.builder()
                .orderId(orderId)
                .taskType(OrderCompensationConstant.TASK_TYPE_TIMEOUT_CANCEL)
                .status(status)
                .retryCount(0)
                .nextRetryTime(nextRetryTime)
                .processingTime(status == OrderCompensationConstant.STATUS_PROCESSING
                        ? firstFailedTime : null)
                .firstFailedTime(firstFailedTime)
                .lastFailedTime(firstFailedTime)
                .failedReason("扫描测试")
                .createTime(firstFailedTime)
                .updateTime(updateTime)
                .build();
        orderCompensationTaskMapper.insert(task);
    }

    private int number(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).intValue();
    }

    private LocalDateTime dateTime(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        return (LocalDateTime) value;
    }

    private String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private long uniqueOrderId() {
        return 8_000_000_000_000L + Math.abs(System.nanoTime() % 1_000_000_000L);
    }
}
