package com.sky.service;

import com.sky.constant.OrderCompensationConstant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证补偿记录的独立事务边界。
 */
@SpringBootTest(properties = "sky.websocket.enabled=false")
class OrderCompensationTransactionIntegrationTest {

    @Autowired
    private OrderCompensationService orderCompensationService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void failureRecordRemainsCommittedAfterOuterTransactionRollsBack() {
        long orderId = 8_500_000_000_000L + Math.abs(System.nanoTime() % 1_000_000_000L);
        TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);

        try {
            assertThrows(IllegalStateException.class, () ->
                    outerTransaction.executeWithoutResult(status -> {
                        orderCompensationService.recordTimeoutCancelFailure(
                                orderId, null, "验证独立事务");
                        throw new IllegalStateException("模拟外层业务事务失败");
                    })
            );

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM order_compensation_task " +
                            "WHERE order_id = ? AND task_type = ? AND status = ?",
                    Integer.class,
                    orderId,
                    OrderCompensationConstant.TASK_TYPE_TIMEOUT_CANCEL,
                    OrderCompensationConstant.STATUS_PENDING
            );
            assertEquals(1, count);
        } finally {
            jdbcTemplate.update(
                    "DELETE FROM order_compensation_task WHERE order_id = ? AND task_type = ?",
                    orderId,
                    OrderCompensationConstant.TASK_TYPE_TIMEOUT_CANCEL
            );
        }
    }
}
