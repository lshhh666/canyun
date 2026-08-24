package com.sky.service;

import com.sky.constant.OrderCompensationConstant;
import com.sky.entity.OrderCompensationTask;
import com.sky.entity.Orders;
import com.sky.entity.User;
import com.sky.mapper.OrderCompensationTaskMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 超时订单首次扫描与补偿任务隔离的真实数据库测试。
 */
@SpringBootTest(properties = "sky.websocket.enabled=false")
@Transactional
class OrderTimeoutSelectionIntegrationTest {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderCompensationTaskMapper orderCompensationTaskMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    void shouldSkipOrdersWithActiveCompensationAndKeepOtherTimeoutOrders() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Long userId = createUser(now);

        Orders withoutTask = createTimeoutOrder(userId, now);
        Orders pendingTask = createTimeoutOrder(userId, now);
        Orders processingTask = createTimeoutOrder(userId, now);
        Orders manualTask = createTimeoutOrder(userId, now);
        Orders successfulTask = createTimeoutOrder(userId, now);

        insertOrder(withoutTask);
        insertOrder(pendingTask);
        insertOrder(processingTask);
        insertOrder(manualTask);
        insertOrder(successfulTask);

        insertCompensation(pendingTask.getId(), OrderCompensationConstant.STATUS_PENDING, now);
        insertCompensation(processingTask.getId(), OrderCompensationConstant.STATUS_PROCESSING, now);
        insertCompensation(manualTask.getId(), OrderCompensationConstant.STATUS_MANUAL, now);
        insertCompensation(successfulTask.getId(), OrderCompensationConstant.STATUS_SUCCESS, now);

        List<Long> selectedIds = orderMapper
                .getTimeoutOrdersWithoutActiveCompensation(now.minusMinutes(15))
                .stream()
                .map(Orders::getId)
                .collect(Collectors.toList());

        assertTrue(selectedIds.contains(withoutTask.getId()));
        assertTrue(selectedIds.contains(successfulTask.getId()));
        assertFalse(selectedIds.contains(pendingTask.getId()));
        assertFalse(selectedIds.contains(processingTask.getId()));
        assertFalse(selectedIds.contains(manualTask.getId()));
    }

    private Long createUser(LocalDateTime now) {
        User user = User.builder()
                .openid(UUID.randomUUID().toString().replace("-", ""))
                .createTime(now)
                .build();
        userMapper.insert(user);
        return user.getId();
    }

    private Orders createTimeoutOrder(Long userId, LocalDateTime now) {
        return Orders.builder()
                .number("timeout-selection-" + UUID.randomUUID().toString().replace("-", ""))
                .status(Orders.PENDING_PAYMENT)
                .userId(userId)
                .addressBookId(1L)
                .orderTime(now.minusMinutes(20))
                .payMethod(1)
                .payStatus(Orders.UN_PAID)
                .amount(new BigDecimal("58.00"))
                .originalAmount(new BigDecimal("58.00"))
                .discountAmount(BigDecimal.ZERO)
                .remark("")
                .phone("13800000000")
                .address("测试地址")
                .consignee("测试用户")
                .estimatedDeliveryTime(now.plusMinutes(30))
                .deliveryStatus(1)
                .packAmount(2)
                .tablewareNumber(0)
                .tablewareStatus(0)
                .build();
    }

    private void insertOrder(Orders order) {
        orderMapper.add(order);
    }

    private void insertCompensation(Long orderId, Integer status, LocalDateTime now) {
        OrderCompensationTask task = OrderCompensationTask.builder()
                .orderId(orderId)
                .taskType(OrderCompensationConstant.TASK_TYPE_TIMEOUT_CANCEL)
                .status(status)
                .retryCount(0)
                .nextRetryTime(now.plusMinutes(5))
                .processingTime(status.equals(OrderCompensationConstant.STATUS_PROCESSING) ? now : null)
                .firstFailedTime(now)
                .lastFailedTime(now)
                .successTime(status.equals(OrderCompensationConstant.STATUS_SUCCESS) ? now : null)
                .failedReason("测试补偿状态")
                .createTime(now)
                .updateTime(now)
                .build();
        orderCompensationTaskMapper.insert(task);
    }
}
