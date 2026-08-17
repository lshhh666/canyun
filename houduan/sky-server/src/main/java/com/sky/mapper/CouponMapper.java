package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sky.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {
    @Update("UPDATE coupon " +
            "SET stock = stock - 1, update_time = #{now} " +
            "WHERE id = #{couponId} " +
            "AND stock > 0 " +
            "AND status = 1 " +
            "AND receive_start_time <= #{now} " +
            "AND receive_end_time >= #{now}")
    int decrementStock(@Param("couponId") Long couponId,
                       @Param("now") LocalDateTime now);
}
