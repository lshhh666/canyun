package com.sky.service.impl;


import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.sky.entity.UserCoupon;
import com.sky.mapper.UserCouponMapper;
import com.sky.service.UserCouponService;
import org.springframework.stereotype.Service;

@Service
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper,UserCoupon> implements UserCouponService {
}
