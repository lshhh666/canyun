package com.cloudmeal.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.cloudmeal.entity.UserCoupon;

import java.util.List;

public interface UserCouponService extends IService<UserCoupon> {
    //查看我的优惠券
    List<UserCoupon> listMine();
}
