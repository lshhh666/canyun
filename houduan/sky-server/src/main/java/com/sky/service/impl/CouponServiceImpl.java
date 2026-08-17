package com.sky.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.sky.context.BaseContext;
import com.sky.entity.Coupon;
import com.sky.entity.UserCoupon;
import com.sky.enums.CouponStatus;
import com.sky.enums.UserCouponStatus;
import com.sky.exception.CouponBusinessException;
import com.sky.mapper.CouponMapper;
import com.sky.service.CouponService;
import com.sky.service.UserCouponService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.util.List;

@Service
public class CouponServiceImpl extends ServiceImpl<CouponMapper,Coupon> implements CouponService {
    @Autowired
    private CouponMapper couponMapper;
    @Autowired
    private UserCouponService userCouponService;
    @Override
    public List<Coupon> listAvailable() {
        LocalDateTime now = LocalDateTime.now();

        LambdaQueryWrapper<Coupon> queryWrapper =
                new LambdaQueryWrapper<Coupon>()
                        .eq(Coupon::getStatus, CouponStatus.DISTRIBUTING)
                        .gt(Coupon::getStock, 0)
                        .le(Coupon::getReceiveStartTime, now)
                        .ge(Coupon::getReceiveEndTime, now)
                        .orderByDesc(Coupon::getCreateTime);


        return couponMapper.selectList(queryWrapper);
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receive(Long couponId) {
        Long userId = BaseContext.getCurrentId();
        LocalDateTime now = LocalDateTime.now();

        // 友好提示；真正的并发防重还要依靠数据库唯一索引
        long count = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, couponId)
                .count();

        if (count > 0) {
            throw new CouponBusinessException("你已经领取过该优惠券");
        }

        // 查询优惠券，用于生成用户领取时的快照
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new CouponBusinessException("优惠券不存在");
        }

        // 数据库原子判断活动状态、领取时间和库存并扣减
        int rows = couponMapper.decrementStock(couponId, now);
        if (rows == 0) {
            throw new CouponBusinessException("优惠券已停发、未到领取时间或库存不足");
        }

        UserCoupon userCoupon = new UserCoupon()
                .setUserId(userId)
                .setCouponId(couponId)
                .setCouponName(coupon.getName())
                .setThresholdAmount(coupon.getThresholdAmount())
                .setDiscountAmount(coupon.getDiscountAmount())
                .setStatus(UserCouponStatus.AVAILABLE)
                .setReceiveTime(now)
                .setValidStartTime(coupon.getValidStartTime())
                .setValidEndTime(coupon.getValidEndTime())
                .setCreateTime(now)
                .setUpdateTime(now);

        try {
            boolean saved = userCouponService.save(userCoupon);
            if (!saved) {
                throw new CouponBusinessException("优惠券领取失败");
            }
        } catch (DuplicateKeyException ex) {
            // 并发请求可能同时通过前置查询，最终由唯一索引兜底。
            // 抛出业务异常后，本次事务中的库存扣减会一并回滚。
            throw new CouponBusinessException("你已经领取过该优惠券");
        }
    }
}
