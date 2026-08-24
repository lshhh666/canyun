package com.sky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sky.constant.MessageConstant;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.context.BaseContext;
import com.sky.entity.Coupon;
import com.sky.entity.Orders;
import com.sky.entity.User;
import com.sky.entity.UserCoupon;
import com.sky.enums.CouponStatus;
import com.sky.enums.UserCouponStatus;
import com.sky.mapper.UserMapper;
import com.sky.mapper.UserCouponMapper;
import com.sky.mapper.OrderMapper;
import com.sky.exception.CouponBusinessException;
import com.sky.exception.OrderBusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "sky.websocket.enabled=false")
class CouponReceiveIntegrationTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private UserCouponService userCouponService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void sameUserCanOnlyReceiveOnceUnderConcurrency() throws Exception {
        Coupon coupon = createCoupon(20);
        List<Long> userIds = createUsers(1);

        try {
            int successes = runConcurrentReceives(coupon.getId(),
                    Collections.nCopies(12, userIds.get(0)));

            assertEquals(1, successes);
            assertEquals(19, couponService.getById(coupon.getId()).getStock());
            assertEquals(1L, receivedCount(coupon.getId()));
        } finally {
            cleanup(coupon.getId(), userIds);
        }
    }

    @Test
    void concurrentReceiveDoesNotOversell() throws Exception {
        Coupon coupon = createCoupon(3);
        List<Long> userIds = createUsers(10);

        try {
            int successes = runConcurrentReceives(coupon.getId(), userIds);

            assertEquals(3, successes);
            assertEquals(0, couponService.getById(coupon.getId()).getStock());
            assertEquals(3L, receivedCount(coupon.getId()));
        } finally {
            cleanup(coupon.getId(), userIds);
        }
    }

    @Test
    @Transactional
    void listMineExpiresOnlyCurrentUsersAvailableCoupons() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> userIds = createUsers(2);
        Long currentUserId = userIds.get(0);
        Long otherUserId = userIds.get(1);

        Coupon couponA = createCoupon(1);
        Coupon couponB = createCoupon(1);
        Coupon couponC = createCoupon(1);
        Coupon couponD = createCoupon(1);

        UserCoupon expiredAvailable = createUserCoupon(
                currentUserId, couponA.getId(), UserCouponStatus.AVAILABLE,
                now.minusDays(1), now.minusMinutes(1));
        UserCoupon futureAvailable = createUserCoupon(
                currentUserId, couponB.getId(), UserCouponStatus.AVAILABLE,
                now.plusDays(1), now.minusMinutes(2));
        UserCoupon expiredLocked = createUserCoupon(
                currentUserId, couponC.getId(), UserCouponStatus.LOCKED,
                now.minusDays(1), now.minusMinutes(3));
        UserCoupon otherUsersExpiredAvailable = createUserCoupon(
                otherUserId, couponD.getId(), UserCouponStatus.AVAILABLE,
                now.minusDays(1), now.minusMinutes(4));

        assertTrue(userCouponService.saveBatch(java.util.Arrays.asList(
                expiredAvailable,
                futureAvailable,
                expiredLocked,
                otherUsersExpiredAvailable)));

        BaseContext.setCurrentId(currentUserId);
        try {
            List<UserCoupon> result = userCouponService.listMine();

            assertEquals(3, result.size());
            assertEquals(expiredAvailable.getId(), result.get(0).getId());
            assertEquals(futureAvailable.getId(), result.get(1).getId());
            assertEquals(expiredLocked.getId(), result.get(2).getId());
            assertEquals(UserCouponStatus.EXPIRED, result.get(0).getStatus());
            assertEquals(UserCouponStatus.AVAILABLE, result.get(1).getStatus());
            assertEquals(UserCouponStatus.LOCKED, result.get(2).getStatus());

            UserCoupon otherUsersCoupon = userCouponService.getById(
                    otherUsersExpiredAvailable.getId());
            assertEquals(UserCouponStatus.AVAILABLE, otherUsersCoupon.getStatus());
        } finally {
            BaseContext.removeCurrentId();
        }
    }

    @Test
    @Transactional
    void lockForOrderChecksOwnershipAndCanOnlySucceedOnce() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        List<Long> userIds = createUsers(2);
        Coupon coupon = createCoupon(1);
        UserCoupon userCoupon = createUserCoupon(
                userIds.get(0), coupon.getId(), UserCouponStatus.AVAILABLE,
                now.plusDays(1), now.minusMinutes(1));
        assertTrue(userCouponService.save(userCoupon));

        long firstOrderId = 900_000_000_000L + coupon.getId();
        long secondOrderId = firstOrderId + 1;

        int wrongUserResult = userCouponMapper.lockForOrder(
                userCoupon.getId(), userIds.get(1), firstOrderId,
                new BigDecimal("60.00"), now);
        int firstResult = userCouponMapper.lockForOrder(
                userCoupon.getId(), userIds.get(0), firstOrderId,
                new BigDecimal("60.00"), now);
        int secondResult = userCouponMapper.lockForOrder(
                userCoupon.getId(), userIds.get(0), secondOrderId,
                new BigDecimal("60.00"), now);

        assertEquals(0, wrongUserResult);
        assertEquals(1, firstResult);
        assertEquals(0, secondResult);

        UserCoupon locked = userCouponMapper.selectById(userCoupon.getId());
        assertEquals(UserCouponStatus.LOCKED, locked.getStatus());
        assertEquals(firstOrderId, locked.getOrderId());
        assertEquals(now, locked.getLockedTime());
    }

    @Test
    @Transactional
    void markUsedByOrderChecksBindingAndCanOnlySucceedOnce() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        List<Long> userIds = createUsers(1);
        Coupon coupon = createCoupon(1);
        UserCoupon userCoupon = createUserCoupon(
                userIds.get(0), coupon.getId(), UserCouponStatus.AVAILABLE,
                now.plusDays(1), now.minusMinutes(1));
        assertTrue(userCouponService.save(userCoupon));

        long orderId = 910_000_000_000L + coupon.getId();
        assertEquals(1, userCouponMapper.lockForOrder(
                userCoupon.getId(), userIds.get(0), orderId,
                new BigDecimal("60.00"), now));

        int wrongOrderResult = userCouponMapper.markUsedByOrder(
                userCoupon.getId(), orderId + 1, now);
        int firstResult = userCouponMapper.markUsedByOrder(
                userCoupon.getId(), orderId, now);
        int repeatedResult = userCouponMapper.markUsedByOrder(
                userCoupon.getId(), orderId, now);

        assertEquals(0, wrongOrderResult);
        assertEquals(1, firstResult);
        assertEquals(0, repeatedResult);

        UserCoupon used = userCouponMapper.selectById(userCoupon.getId());
        assertEquals(UserCouponStatus.USED, used.getStatus());
        assertEquals(orderId, used.getOrderId());
        assertEquals(now, used.getUsedTime());
    }

    @Test
    @Transactional
    void releaseByOrderRestoresValidCouponAndExpiresPastCoupon() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Long userId = createUsers(1).get(0);
        Coupon validTemplate = createCoupon(1);
        Coupon expiredTemplate = createCoupon(1);
        long validOrderId = 920_000_000_000L + validTemplate.getId();
        long expiredOrderId = 930_000_000_000L + expiredTemplate.getId();

        UserCoupon validCoupon = createUserCoupon(
                userId, validTemplate.getId(), UserCouponStatus.LOCKED,
                now.plusDays(1), now.minusDays(1))
                .setOrderId(validOrderId)
                .setLockedTime(now.minusMinutes(5));
        UserCoupon expiredCoupon = createUserCoupon(
                userId, expiredTemplate.getId(), UserCouponStatus.LOCKED,
                now.minusSeconds(1), now.minusDays(2))
                .setOrderId(expiredOrderId)
                .setLockedTime(now.minusDays(1));
        assertTrue(userCouponService.saveBatch(java.util.Arrays.asList(validCoupon, expiredCoupon)));

        assertEquals(0, userCouponMapper.releaseByOrder(
                validCoupon.getId(), validOrderId + 1, now));
        assertEquals(1, userCouponMapper.releaseByOrder(
                validCoupon.getId(), validOrderId, now));
        assertEquals(1, userCouponMapper.releaseByOrder(
                expiredCoupon.getId(), expiredOrderId, now));

        UserCoupon releasedValid = userCouponMapper.selectById(validCoupon.getId());
        assertEquals(UserCouponStatus.AVAILABLE, releasedValid.getStatus());
        assertEquals(null, releasedValid.getOrderId());
        assertEquals(null, releasedValid.getLockedTime());

        UserCoupon releasedExpired = userCouponMapper.selectById(expiredCoupon.getId());
        assertEquals(UserCouponStatus.EXPIRED, releasedExpired.getStatus());
        assertEquals(null, releasedExpired.getOrderId());
        assertEquals(null, releasedExpired.getLockedTime());
    }

    @Test
    void cancellationRollsBackWhenCouponCannotBeReleased() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        List<Long> userIds = createUsers(1);
        Long userId = userIds.get(0);
        Coupon coupon = createCoupon(1);
        UserCoupon userCoupon = createUserCoupon(
                userId, coupon.getId(), UserCouponStatus.LOCKED,
                now.plusDays(1), now.minusMinutes(1))
                .setLockedTime(now.minusMinutes(1));
        assertTrue(userCouponService.save(userCoupon));

        Orders order = Orders.builder()
                .number("rollback-" + UUID.randomUUID().toString().replace("-", ""))
                .status(Orders.PENDING_PAYMENT)
                .userId(userId)
                .addressBookId(1L)
                .orderTime(now)
                .payMethod(1)
                .payStatus(Orders.UN_PAID)
                .amount(new BigDecimal("58.00"))
                .originalAmount(new BigDecimal("68.00"))
                .discountAmount(new BigDecimal("10.00"))
                .userCouponId(userCoupon.getId())
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
        orderMapper.add(order);

        // Deliberately bind the coupon to a different order so releaseByOrder returns 0.
        userCoupon.setOrderId(order.getId() + 1_000_000L);
        assertEquals(1, userCouponMapper.updateById(userCoupon));

        BaseContext.setCurrentId(userId);
        try {
            assertThrows(CouponBusinessException.class,
                    () -> orderService.cancelByOrderId(order.getId()));
            assertEquals(Orders.PENDING_PAYMENT,
                    orderMapper.getById(order.getId()).getStatus());
        } finally {
            BaseContext.removeCurrentId();
            jdbcTemplate.update("DELETE FROM orders WHERE id = ?", order.getId());
            userCouponService.removeById(userCoupon.getId());
            couponService.removeById(coupon.getId());
            jdbcTemplate.update("DELETE FROM user WHERE id = ?", userId);
        }
    }

    @Test
    @Transactional
    void cancelIfPendingOnlyCancelsUnpaidOrder() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Long userId = createUsers(1).get(0);
        Orders pendingOrder = createOrder(userId, Orders.PENDING_PAYMENT, Orders.UN_PAID, now);
        Orders paidOrder = createOrder(userId, Orders.TO_BE_CONFIRMED, Orders.PAID, now);
        orderMapper.add(pendingOrder);
        orderMapper.add(paidOrder);

        int cancelledRows = orderMapper.cancelIfPending(
                pendingOrder.getId(), now, MessageConstant.ORDER_TIME_OUT);
        int rejectedRows = orderMapper.cancelIfPending(
                paidOrder.getId(), now, MessageConstant.ORDER_TIME_OUT);

        assertEquals(1, cancelledRows);
        assertEquals(0, rejectedRows);

        Orders cancelled = orderMapper.getById(pendingOrder.getId());
        assertEquals(Orders.CANCELLED, cancelled.getStatus());
        assertEquals(MessageConstant.ORDER_TIME_OUT, cancelled.getCancelReason());
        assertEquals(now, cancelled.getCancelTime());

        Orders stillPaid = orderMapper.getById(paidOrder.getId());
        assertEquals(Orders.TO_BE_CONFIRMED, stillPaid.getStatus());
        assertEquals(Orders.PAID, stillPaid.getPayStatus());
        assertEquals(null, stillPaid.getCancelTime());
        assertEquals(null, stillPaid.getCancelReason());
    }

    @Test
    @Transactional
    void markPaidIfPendingOnlyPaysOwnedUnpaidOrder() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        List<Long> userIds = createUsers(2);
        Long ownerId = userIds.get(0);
        Long anotherUserId = userIds.get(1);
        Orders payableOrder = createOrder(
                ownerId, Orders.PENDING_PAYMENT, Orders.UN_PAID, now);
        Orders cancelledOrder = createOrder(
                ownerId, Orders.CANCELLED, Orders.UN_PAID, now);
        Orders otherOwnedOrder = createOrder(
                ownerId, Orders.PENDING_PAYMENT, Orders.UN_PAID, now);
        orderMapper.add(payableOrder);
        orderMapper.add(cancelledOrder);
        orderMapper.add(otherOwnedOrder);

        int paidRows = orderMapper.markPaidIfPending(
                payableOrder.getId(), ownerId, 1, now);
        int wrongUserRows = orderMapper.markPaidIfPending(
                otherOwnedOrder.getId(), anotherUserId, 1, now);
        int cancelledRows = orderMapper.markPaidIfPending(
                cancelledOrder.getId(), ownerId, 1, now);

        assertEquals(1, paidRows);
        assertEquals(0, wrongUserRows);
        assertEquals(0, cancelledRows);

        Orders paid = orderMapper.getById(payableOrder.getId());
        assertEquals(Orders.TO_BE_CONFIRMED, paid.getStatus());
        assertEquals(Orders.PAID, paid.getPayStatus());
        assertEquals(1, paid.getPayMethod());
        assertEquals(now, paid.getCheckoutTime());

        Orders stillCancelled = orderMapper.getById(cancelledOrder.getId());
        assertEquals(Orders.CANCELLED, stillCancelled.getStatus());
        assertEquals(Orders.UN_PAID, stillCancelled.getPayStatus());
        assertEquals(null, stillCancelled.getCheckoutTime());

        Orders stillOwnedByOther = orderMapper.getById(otherOwnedOrder.getId());
        assertEquals(Orders.PENDING_PAYMENT, stillOwnedByOther.getStatus());
        assertEquals(Orders.UN_PAID, stillOwnedByOther.getPayStatus());
    }

    @Test
    void paymentRollsBackWhenCouponCannotBeWrittenOff() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Long userId = createUsers(1).get(0);
        Coupon coupon = createCoupon(1);
        UserCoupon userCoupon = createUserCoupon(
                userId, coupon.getId(), UserCouponStatus.LOCKED,
                now.plusDays(1), now.minusMinutes(1))
                .setLockedTime(now.minusMinutes(1));
        assertTrue(userCouponService.save(userCoupon));

        Orders order = createOrder(
                userId, Orders.PENDING_PAYMENT, Orders.UN_PAID, now);
        order.setUserCouponId(userCoupon.getId());
        orderMapper.add(order);

        // Bind the locked coupon to a different order so markUsedByOrder returns 0.
        userCoupon.setOrderId(order.getId() + 1_000_000L);
        assertEquals(1, userCouponMapper.updateById(userCoupon));

        OrdersPaymentDTO paymentDTO = new OrdersPaymentDTO();
        paymentDTO.setOrderNumber(order.getNumber());
        paymentDTO.setPayMethod(1);
        BaseContext.setCurrentId(userId);
        try {
            assertThrows(CouponBusinessException.class,
                    () -> orderService.orderpayment(paymentDTO));
            Orders rolledBack = orderMapper.getById(order.getId());
            assertEquals(Orders.PENDING_PAYMENT, rolledBack.getStatus());
            assertEquals(Orders.UN_PAID, rolledBack.getPayStatus());
            assertEquals(null, rolledBack.getCheckoutTime());
        } finally {
            BaseContext.removeCurrentId();
            jdbcTemplate.update("DELETE FROM orders WHERE id = ?", order.getId());
            userCouponService.removeById(userCoupon.getId());
            couponService.removeById(coupon.getId());
            jdbcTemplate.update("DELETE FROM user WHERE id = ?", userId);
        }
    }

    @Test
    void paymentAndTimeoutCancellationNeverProduceMixedState() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            runPaymentAndTimeoutCancellationRace();
        }
    }

    @Test
    void paymentAndUserCancellationNeverProduceCancelledPaidOrder() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            runPaymentAndUserCancellationRace();
        }
    }

    private void runPaymentAndUserCancellationRace() throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Long userId = createUsers(1).get(0);
        Orders order = createOrder(userId, Orders.PENDING_PAYMENT, Orders.UN_PAID, now);
        orderMapper.add(order);

        OrdersPaymentDTO paymentDTO = new OrdersPaymentDTO();
        paymentDTO.setOrderNumber(order.getNumber());
        paymentDTO.setPayMethod(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> payment = executor.submit(() -> {
                BaseContext.setCurrentId(userId);
                ready.countDown();
                start.await();
                try {
                    orderService.orderpayment(paymentDTO);
                    return true;
                } catch (OrderBusinessException lostRace) {
                    return false;
                } finally {
                    BaseContext.removeCurrentId();
                }
            });
            Future<Boolean> cancellation = executor.submit(() -> {
                BaseContext.setCurrentId(userId);
                ready.countDown();
                start.await();
                try {
                    orderService.cancelByOrderId(order.getId());
                    return true;
                } catch (OrderBusinessException lostRace) {
                    return false;
                } finally {
                    BaseContext.removeCurrentId();
                }
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            boolean paymentSucceeded = payment.get(10, TimeUnit.SECONDS);
            boolean cancellationSucceeded = cancellation.get(10, TimeUnit.SECONDS);

            Orders finalOrder = orderMapper.getById(order.getId());
            boolean paidState = Orders.TO_BE_CONFIRMED.equals(finalOrder.getStatus())
                    && Orders.PAID.equals(finalOrder.getPayStatus());
            boolean cancelledState = Orders.CANCELLED.equals(finalOrder.getStatus())
                    && Orders.UN_PAID.equals(finalOrder.getPayStatus());

            assertTrue(paidState || cancelledState, "主动取消与支付竞争产生了已取消+已支付状态");
            assertEquals(paymentSucceeded, paidState);
            assertEquals(cancellationSucceeded, cancelledState);
        } finally {
            start.countDown();
            executor.shutdownNow();
            jdbcTemplate.update("DELETE FROM orders WHERE id = ?", order.getId());
            jdbcTemplate.update("DELETE FROM user WHERE id = ?", userId);
        }
    }

    private void runPaymentAndTimeoutCancellationRace() throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Long userId = createUsers(1).get(0);
        Coupon coupon = createCoupon(1);
        UserCoupon userCoupon = createUserCoupon(
                userId, coupon.getId(), UserCouponStatus.LOCKED,
                now.plusDays(1), now.minusMinutes(1))
                .setLockedTime(now.minusMinutes(1));
        assertTrue(userCouponService.save(userCoupon));

        Orders order = createOrder(
                userId, Orders.PENDING_PAYMENT, Orders.UN_PAID,
                now.minusMinutes(20));
        order.setUserCouponId(userCoupon.getId());
        orderMapper.add(order);
        userCoupon.setOrderId(order.getId());
        assertEquals(1, userCouponMapper.updateById(userCoupon));

        OrdersPaymentDTO paymentDTO = new OrdersPaymentDTO();
        paymentDTO.setOrderNumber(order.getNumber());
        paymentDTO.setPayMethod(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> payment = executor.submit(() -> {
                BaseContext.setCurrentId(userId);
                ready.countDown();
                start.await();
                try {
                    orderService.orderpayment(paymentDTO);
                    return true;
                } catch (OrderBusinessException lostRace) {
                    return false;
                } finally {
                    BaseContext.removeCurrentId();
                }
            });
            Future<?> timeoutCancellation = executor.submit(() -> {
                ready.countDown();
                start.await();
                orderService.cancelTimeoutOrder(order.getId());
                return null;
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            boolean paymentSucceeded = payment.get(10, TimeUnit.SECONDS);
            timeoutCancellation.get(10, TimeUnit.SECONDS);

            Orders finalOrder = orderMapper.getById(order.getId());
            UserCoupon finalCoupon = userCouponMapper.selectById(userCoupon.getId());
            boolean paidState = Orders.TO_BE_CONFIRMED.equals(finalOrder.getStatus())
                    && Orders.PAID.equals(finalOrder.getPayStatus())
                    && UserCouponStatus.USED.equals(finalCoupon.getStatus())
                    && Objects.equals(order.getId(), finalCoupon.getOrderId());
            boolean cancelledState = Orders.CANCELLED.equals(finalOrder.getStatus())
                    && Orders.UN_PAID.equals(finalOrder.getPayStatus())
                    && UserCouponStatus.AVAILABLE.equals(finalCoupon.getStatus())
                    && finalCoupon.getOrderId() == null;

            assertTrue(paidState || cancelledState,
                    "订单和优惠券出现混合状态");
            assertEquals(paymentSucceeded, paidState);
        } finally {
            start.countDown();
            executor.shutdownNow();
            jdbcTemplate.update("DELETE FROM orders WHERE id = ?", order.getId());
            userCouponService.removeById(userCoupon.getId());
            couponService.removeById(coupon.getId());
            jdbcTemplate.update("DELETE FROM user WHERE id = ?", userId);
        }
    }

    private Orders createOrder(Long userId, Integer status, Integer payStatus,
                               LocalDateTime now) {
        return Orders.builder()
                .number("atomic-cancel-" + UUID.randomUUID().toString().replace("-", ""))
                .status(status)
                .userId(userId)
                .addressBookId(1L)
                .orderTime(now)
                .payMethod(1)
                .payStatus(payStatus)
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

    private Coupon createCoupon(int stock) {
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon()
                .setName("并发测试券-" + UUID.randomUUID().toString().replace("-", ""))
                .setThresholdAmount(new BigDecimal("50.00"))
                .setDiscountAmount(new BigDecimal("8.00"))
                .setTotalStock(stock)
                .setStock(stock)
                .setReceiveStartTime(now.minusMinutes(1))
                .setReceiveEndTime(now.plusMinutes(10))
                .setValidStartTime(now)
                .setValidEndTime(now.plusDays(7))
                .setStatus(CouponStatus.DISTRIBUTING)
                .setCreateTime(now)
                .setUpdateTime(now);

        assertTrue(couponService.save(coupon));
        return coupon;
    }

    private List<Long> createUsers(int count) {
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            User user = User.builder()
                    .openid("ct" + UUID.randomUUID().toString().replace("-", ""))
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
            userIds.add(user.getId());
        }
        return userIds;
    }

    private UserCoupon createUserCoupon(Long userId,
                                        Long couponId,
                                        UserCouponStatus status,
                                        LocalDateTime validEndTime,
                                        LocalDateTime receiveTime) {
        return new UserCoupon()
                .setUserId(userId)
                .setCouponId(couponId)
                .setCouponName("列表测试券")
                .setThresholdAmount(new BigDecimal("50.00"))
                .setDiscountAmount(new BigDecimal("8.00"))
                .setStatus(status)
                .setReceiveTime(receiveTime)
                .setValidStartTime(receiveTime)
                .setValidEndTime(validEndTime)
                .setCreateTime(receiveTime)
                .setUpdateTime(receiveTime);
    }

    private int runConcurrentReceives(Long couponId, List<Long> userIds) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(userIds.size());
        CountDownLatch ready = new CountDownLatch(userIds.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (Long userId : userIds) {
                futures.add(executor.submit(() -> {
                    BaseContext.setCurrentId(userId);
                    ready.countDown();
                    try {
                        start.await();
                        couponService.receive(couponId);
                        return true;
                    } catch (RuntimeException ex) {
                        return false;
                    } finally {
                        BaseContext.removeCurrentId();
                    }
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            int successes = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(20, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            return successes;
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private long receivedCount(Long couponId) {
        return userCouponService.lambdaQuery()
                .eq(UserCoupon::getCouponId, couponId)
                .count();
    }

    private void cleanup(Long couponId, List<Long> userIds) {
        userCouponService.remove(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getCouponId, couponId));
        couponService.removeById(couponId);
        for (Long userId : userIds) {
            jdbcTemplate.update("DELETE FROM user WHERE id = ?", userId);
        }
    }
}
