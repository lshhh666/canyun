package com.cloudmeal.service;

import com.cloudmeal.entity.Coupon;
import com.cloudmeal.enums.CouponStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "cloudmeal.websocket.enabled=false")
class CouponServiceTest {

    @Autowired
    private CouponService couponService;

    @Test
    @Transactional
    void listAvailableShouldReturnCoupons() {
        LocalDateTime now = LocalDateTime.now();
        Coupon available = new Coupon()
                .setName("可领取优惠券查询测试")
                .setThresholdAmount(new BigDecimal("20.00"))
                .setDiscountAmount(new BigDecimal("5.00"))
                .setTotalStock(2)
                .setStock(2)
                .setReceiveStartTime(now.minusMinutes(1))
                .setReceiveEndTime(now.plusMinutes(1))
                .setValidStartTime(now.minusMinutes(1))
                .setValidEndTime(now.plusDays(1))
                .setStatus(CouponStatus.DISTRIBUTING)
                .setCreateTime(now)
                .setUpdateTime(now);
        assertTrue(couponService.save(available));

        List<Coupon> coupons = couponService.listAvailable();

        assertTrue(coupons.stream()
                .anyMatch(coupon -> available.getId().equals(coupon.getId())));
    }
}
