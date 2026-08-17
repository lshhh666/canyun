package com.sky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sky.context.BaseContext;
import com.sky.entity.Coupon;
import com.sky.entity.User;
import com.sky.entity.UserCoupon;
import com.sky.enums.CouponStatus;
import com.sky.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
