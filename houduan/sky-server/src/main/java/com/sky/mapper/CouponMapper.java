package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.pagehelper.Page;
import com.sky.dto.CouponPageQueryDTO;
import com.sky.entity.Coupon;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {

    Page<Coupon> pageQuery(CouponPageQueryDTO queryDTO);

    int updateDraft(@Param("id") Long id,
                    @Param("coupon") Coupon coupon);

    @Update("UPDATE coupon SET status = 1, update_time = #{now} " +
            "WHERE id = #{id} AND status = 0 AND stock > 0 " +
            "AND receive_start_time < receive_end_time " +
            "AND receive_end_time > #{now} " +
            "AND valid_start_time < valid_end_time " +
            "AND receive_end_time <= valid_end_time")
    int startDistribution(@Param("id") Long id,
                          @Param("now") LocalDateTime now);

    @Update("UPDATE coupon SET status = 2, update_time = #{now} " +
            "WHERE id = #{id} AND status = 1")
    int stopDistribution(@Param("id") Long id,
                         @Param("now") LocalDateTime now);

    @Delete("DELETE FROM coupon WHERE id = #{id} AND status = 0")
    int deleteDraft(@Param("id") Long id);

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
