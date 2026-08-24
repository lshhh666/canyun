package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sky.entity.UserCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


import java.math.BigDecimal;
import java.time.LocalDateTime;


@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {
    int lockForOrder(@Param("userCouponId") Long userCouponId,
                     @Param("userId") Long userId,
                     @Param("orderId") Long orderId,
                     @Param("goodsAmount") BigDecimal goodsAmount,
                     @Param("now") LocalDateTime now);
    //核销优惠券
    int markUsedByOrder(@Param("userCouponId") Long userCouponId,
                        @Param("orderId") Long orderId,
                        @Param("now") LocalDateTime now);
    //取消订单的优惠券返回
    int releaseByOrder(@Param("userCouponId") Long userCouponId,
                       @Param("orderId") Long orderId,
                       @Param("now") LocalDateTime now);
}
