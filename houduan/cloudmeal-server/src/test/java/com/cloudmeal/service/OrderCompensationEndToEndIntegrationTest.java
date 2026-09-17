package com.cloudmeal.service;

import com.cloudmeal.constant.OrderCompensationConstant;
import com.cloudmeal.entity.Coupon;
import com.cloudmeal.entity.OrderCompensationTask;
import com.cloudmeal.entity.Orders;
import com.cloudmeal.entity.User;
import com.cloudmeal.entity.UserCoupon;
import com.cloudmeal.enums.CouponStatus;
import com.cloudmeal.enums.UserCouponStatus;
import com.cloudmeal.mapper.CouponMapper;
import com.cloudmeal.mapper.OrderCompensationTaskMapper;
import com.cloudmeal.mapper.OrderMapper;
import com.cloudmeal.mapper.UserCouponMapper;
import com.cloudmeal.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 订单补偿完整业务链路测试：真实执行订单取消、优惠券释放和补偿任务完成。
 * 不使用测试级事务，确保订单事务、任务抢占和任务完成都经过真实提交边界。
 */
@SpringBootTest(properties = "cloudmeal.websocket.enabled=false")
class OrderCompensationEndToEndIntegrationTest {

    @Autowired
    private OrderCompensationService orderCompensationService;

    @Autowired
    private OrderCompensationTaskMapper orderCompensationTaskMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CouponMapper couponMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbc;

    private Long createdTaskId;
    private Long createdOrderId;
    private Long createdUserCouponId;
    private Long createdCouponId;
    private Long createdUserId;

    @AfterEach
    void cleanCommittedFixtures() {
        deleteById("order_compensation_task", createdTaskId);
        deleteById("orders", createdOrderId);
        deleteById("user_coupon", createdUserCouponId);
        deleteById("coupon", createdCouponId);
        deleteById("user", createdUserId);
    }

    @Test
    void shouldCancelOrderReleaseCouponAndCompleteCompensationTask() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Long userId = insertUser(now);
        Long couponId = insertCoupon(now);
        UserCoupon userCoupon = insertLockedCoupon(userId, couponId, now);
        Orders order = insertTimeoutOrder(userId, userCoupon.getId(), now);

        userCoupon.setOrderId(order.getId());
        assertEquals(1, userCouponMapper.updateById(userCoupon));
        OrderCompensationTask task = insertDueCompensationTask(
                order.getId(), userCoupon.getId());

        orderCompensationService.processDueTasks();

        Orders cancelledOrder = orderMapper.getById(order.getId());
        UserCoupon releasedCoupon = userCouponMapper.selectById(userCoupon.getId());
        OrderCompensationTask completedTask =
                orderCompensationTaskMapper.selectById(task.getId());

        assertEquals(Orders.CANCELLED, cancelledOrder.getStatus());
        assertEquals(Orders.UN_PAID, cancelledOrder.getPayStatus());
        assertNotNull(cancelledOrder.getCancelTime());
        assertEquals(UserCouponStatus.AVAILABLE, releasedCoupon.getStatus());
        assertNull(releasedCoupon.getOrderId());
        assertNull(releasedCoupon.getLockedTime());
        assertEquals(OrderCompensationConstant.STATUS_SUCCESS, completedTask.getStatus());
        assertNotNull(completedTask.getSuccessTime());
        assertNull(completedTask.getProcessingTime());
    }

    @Test
    void shouldRecoverWhenOrderCommitSucceedsBeforeTaskSuccessUpdate() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Long userId = insertUser(now);
        Long couponId = insertCoupon(now);
        UserCoupon userCoupon = insertLockedCoupon(userId, couponId, now);
        Orders order = insertTimeoutOrder(userId, userCoupon.getId(), now);
        userCoupon.setOrderId(order.getId());
        assertEquals(1, userCouponMapper.updateById(userCoupon));
        OrderCompensationTask task = insertDueCompensationTask(
                order.getId(), userCoupon.getId());

        // 模拟工作线程已抢占任务并提交订单取消，随后在写任务成功状态前宕机。
        jdbc.update("UPDATE order_compensation_task SET status=1, processing_time=? WHERE id=?",
                LocalDateTime.of(2000, 1, 1, 0, 0), task.getId());
        orderService.cancelTimeoutOrder(order.getId());

        orderCompensationService.processDueTasks();

        assertEquals(Orders.CANCELLED, orderMapper.getById(order.getId()).getStatus());
        assertEquals(UserCouponStatus.AVAILABLE,
                userCouponMapper.selectById(userCoupon.getId()).getStatus());
        OrderCompensationTask recovered = orderCompensationTaskMapper.selectById(task.getId());
        assertEquals(OrderCompensationConstant.STATUS_SUCCESS, recovered.getStatus());
        assertNotNull(recovered.getSuccessTime());
        assertNull(recovered.getProcessingTime());
    }

    private Long insertUser(LocalDateTime now) {
        User user = User.builder()
                .openid(UUID.randomUUID().toString().replace("-", ""))
                .createTime(now)
                .build();
        userMapper.insert(user);
        createdUserId = user.getId();
        return user.getId();
    }

    private Long insertCoupon(LocalDateTime now) {
        Coupon coupon = new Coupon()
                .setName("补偿端到端测试券")
                .setThresholdAmount(new BigDecimal("50.00"))
                .setDiscountAmount(new BigDecimal("8.00"))
                .setTotalStock(1)
                .setStock(0)
                .setReceiveStartTime(now.minusDays(1))
                .setReceiveEndTime(now.plusDays(1))
                .setValidStartTime(now.minusDays(1))
                .setValidEndTime(now.plusDays(7))
                .setStatus(CouponStatus.DISTRIBUTING)
                .setCreateTime(now)
                .setUpdateTime(now);
        couponMapper.insert(coupon);
        createdCouponId = coupon.getId();
        return coupon.getId();
    }

    private UserCoupon insertLockedCoupon(Long userId, Long couponId, LocalDateTime now) {
        UserCoupon userCoupon = new UserCoupon()
                .setUserId(userId)
                .setCouponId(couponId)
                .setCouponName("补偿端到端测试券")
                .setThresholdAmount(new BigDecimal("50.00"))
                .setDiscountAmount(new BigDecimal("8.00"))
                .setStatus(UserCouponStatus.LOCKED)
                .setReceiveTime(now.minusMinutes(30))
                .setValidStartTime(now.minusDays(1))
                .setValidEndTime(now.plusDays(7))
                .setLockedTime(now.minusMinutes(20))
                .setCreateTime(now.minusMinutes(30))
                .setUpdateTime(now.minusMinutes(20));
        userCouponMapper.insert(userCoupon);
        createdUserCouponId = userCoupon.getId();
        return userCoupon;
    }

    private Orders insertTimeoutOrder(Long userId, Long userCouponId, LocalDateTime now) {
        Orders order = Orders.builder()
                .number("compensation-e2e-" + UUID.randomUUID().toString().replace("-", ""))
                .status(Orders.PENDING_PAYMENT)
                .userId(userId)
                .addressBookId(1L)
                .orderTime(now.minusMinutes(20))
                .payMethod(1)
                .payStatus(Orders.UN_PAID)
                .amount(new BigDecimal("50.00"))
                .originalAmount(new BigDecimal("58.00"))
                .discountAmount(new BigDecimal("8.00"))
                .userCouponId(userCouponId)
                .remark("")
                .phone("13800000000")
                .address("测试地址")
                .consignee("测试用户")
                .estimatedDeliveryTime(now.plusMinutes(30))
                .deliveryStatus(1)
                .packAmount(2)
                .goodsAmount(new BigDecimal("50.00"))
                .deliveryFee(new BigDecimal("6.00"))
                .tablewareNumber(0)
                .tablewareStatus(0)
                .build();
        orderMapper.add(order);
        createdOrderId = order.getId();
        return order;
    }

    private OrderCompensationTask insertDueCompensationTask(Long orderId, Long userCouponId) {
        LocalDateTime failureTime = LocalDateTime.of(2000, 1, 1, 0, 0);
        OrderCompensationTask task = OrderCompensationTask.builder()
                .orderId(orderId)
                .userCouponId(userCouponId)
                .taskType(OrderCompensationConstant.TASK_TYPE_TIMEOUT_CANCEL)
                .status(OrderCompensationConstant.STATUS_PENDING)
                .retryCount(0)
                .nextRetryTime(failureTime.plusMinutes(5))
                .firstFailedTime(failureTime)
                .lastFailedTime(failureTime)
                .failedReason("模拟首次取消失败")
                .createTime(failureTime)
                .updateTime(failureTime)
                .build();
        orderCompensationTaskMapper.insert(task);
        createdTaskId = task.getId();
        return task;
    }

    private void deleteById(String table, Long id) {
        if (id != null) {
            jdbc.update("DELETE FROM `" + table + "` WHERE id=?", id);
        }
    }
}
