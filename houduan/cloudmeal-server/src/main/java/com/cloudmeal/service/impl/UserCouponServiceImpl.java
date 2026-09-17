package com.cloudmeal.service.impl;


import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.cloudmeal.constant.MessageConstant;
import com.cloudmeal.context.BaseContext;
import com.cloudmeal.entity.UserCoupon;
import com.cloudmeal.enums.UserCouponStatus;
import com.cloudmeal.exception.UserNotLoginException;
import com.cloudmeal.mapper.UserCouponMapper;
import com.cloudmeal.service.UserCouponService;
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
