package com.sky.controller.admin;

import com.sky.dto.CouponDTO;
import com.sky.dto.CouponPageQueryDTO;
import com.sky.entity.Coupon;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.CouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端优惠券模板管理。
 */
@RestController("adminCouponController")
@RequestMapping("/admin/coupon")
@Api(tags = "管理端优惠券接口")
@Slf4j
public class CouponController {

    @Autowired
    private CouponService couponService;

    @PostMapping
    @ApiOperation("创建草稿优惠券")
    public Result<String> create(@RequestBody CouponDTO couponDTO) {
        log.info("创建优惠券草稿：{}", couponDTO);
        couponService.createCoupon(couponDTO);
        return Result.success();
    }

    @GetMapping("/page")
    @ApiOperation("分页查询优惠券")
    public Result<PageResult> page(CouponPageQueryDTO queryDTO) {
        return Result.success(couponService.pageQuery(queryDTO));
    }

    @GetMapping("/{id}")
    @ApiOperation("查询优惠券详情")
    public Result<Coupon> detail(@PathVariable Long id) {
        return Result.success(couponService.getCouponById(id));
    }

    @PutMapping("/{id}")
    @ApiOperation("编辑草稿优惠券")
    public Result<String> update(@PathVariable Long id, @RequestBody CouponDTO couponDTO) {
        log.info("编辑优惠券草稿，id={}，内容={}", id, couponDTO);
        couponService.updateDraft(id, couponDTO);
        return Result.success();
    }

    @PutMapping("/{id}/start")
    @ApiOperation("开始发放优惠券")
    public Result<String> start(@PathVariable Long id) {
        couponService.startDistribution(id);
        return Result.success();
    }

    @PutMapping("/{id}/stop")
    @ApiOperation("停止发放优惠券")
    public Result<String> stop(@PathVariable Long id) {
        couponService.stopDistribution(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除草稿优惠券")
    public Result<String> delete(@PathVariable Long id) {
        couponService.deleteDraft(id);
        return Result.success();
    }
}
