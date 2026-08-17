package com.sky.service;

import com.sky.entity.Coupon;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(properties = "sky.websocket.enabled=false")
class CouponServiceTest {

    @Autowired
    private CouponService couponService;

    @Test
    void listAvailableShouldReturnCoupons() {
        List<Coupon> coupons = couponService.listAvailable();

        coupons.forEach(System.out::println);

        assertFalse(coupons.isEmpty());
    }
}
