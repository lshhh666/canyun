package com.sky.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.sky.entity.Coupon;

import java.util.List;

public interface CouponService extends IService<Coupon> {
    //查询当前可领取优惠券
    List<Coupon> listAvailable();
    /**
     * 当前登录用户领取优惠券
     */
    void receive(Long couponId);
}
