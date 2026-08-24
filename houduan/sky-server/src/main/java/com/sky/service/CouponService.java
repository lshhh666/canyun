package com.sky.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.sky.dto.CouponDTO;
import com.sky.dto.CouponPageQueryDTO;
import com.sky.entity.Coupon;
import com.sky.result.PageResult;

import java.util.List;

public interface CouponService extends IService<Coupon> {
    //查询当前可领取优惠券
    List<Coupon> listAvailable();
    /**
     * 当前登录用户领取优惠券
     */
    void receive(Long couponId);

    /** 管理端创建草稿优惠券。 */
    void createCoupon(CouponDTO couponDTO);

    /** 管理端分页查询优惠券模板。 */
    PageResult pageQuery(CouponPageQueryDTO queryDTO);

    /** 管理端查询优惠券详情。 */
    Coupon getCouponById(Long id);

    /** 仅允许编辑草稿。 */
    void updateDraft(Long id, CouponDTO couponDTO);

    /** 草稿开始发放。 */
    void startDistribution(Long id);

    /** 发放中的优惠券停止发放。 */
    void stopDistribution(Long id);

    /** 仅允许删除草稿。 */
    void deleteDraft(Long id);
}
