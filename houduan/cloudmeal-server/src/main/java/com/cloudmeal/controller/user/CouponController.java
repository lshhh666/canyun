package com.cloudmeal.controller.user;


import com.cloudmeal.entity.Coupon;
import com.cloudmeal.entity.UserCoupon;
import com.cloudmeal.result.Result;
import com.cloudmeal.service.CouponService;
import com.cloudmeal.service.UserCouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("userCouponController")
@RequestMapping("/user/coupon")
@Slf4j
@Api(tags = "用户优惠券接口")
public class CouponController {
    @Autowired
    private CouponService couponService;
    @Autowired
    private UserCouponService userCouponService;
    @GetMapping("/list")
    @ApiOperation("查询当前可领取的优惠券")
    public Result<List<Coupon>> listAvailable() {
        return  Result.success(couponService.listAvailable());
    }

    @PostMapping("/{couponId}/receive")
    @ApiOperation("领取优惠券")
    public Result<String> receive(@PathVariable Long couponId) {
        couponService.receive(couponId);
        return Result.success();
    }

    @GetMapping("/my")
    @ApiOperation("查询我的优惠券")
    public Result<List<UserCoupon>> listMine() {
        return Result.success(userCouponService.listMine());
    }
}
