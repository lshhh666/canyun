package com.sky.service.impl;


import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.entity.UserCoupon;
import com.sky.enums.UserCouponStatus;
import com.sky.exception.UserNotLoginException;
import com.sky.mapper.UserCouponMapper;
import com.sky.service.UserCouponService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper,UserCoupon> implements UserCouponService {
    @Transactional(rollbackFor = Exception.class)
    @Override
    public List<UserCoupon> listMine() {
        Long userId=BaseContext.getCurrentId();
        if (userId == null) {
            throw new UserNotLoginException(MessageConstant.USER_NOT_LOGIN);
        }
        LocalDateTime now = LocalDateTime.now();
        lambdaUpdate().eq(UserCoupon::getUserId,userId)
                .eq(UserCoupon::getStatus, UserCouponStatus.AVAILABLE)
                .le(UserCoupon::getValidEndTime,now)
                .set(UserCoupon::getStatus,UserCouponStatus.EXPIRED)
                .set(UserCoupon::getUpdateTime, now)
                .update();
        return lambdaQuery().eq(UserCoupon::getUserId,userId)
                .orderByDesc(UserCoupon::getReceiveTime)
                .list();
    }
}
