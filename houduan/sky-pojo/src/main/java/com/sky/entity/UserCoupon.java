package com.sky.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;

import com.sky.enums.UserCouponStatus;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 用户优惠券
 * </p>
 *
 * @author author
 * @since 2026-08-17
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_coupon")
@ApiModel(value="UserCoupon对象", description="用户优惠券")
public class UserCoupon implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "用户优惠券ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "用户ID")
    @TableField("user_id")
    private Long userId;

    @ApiModelProperty(value = "优惠券模板ID")
    @TableField("coupon_id")
    private Long couponId;

    @ApiModelProperty(value = "领取时的优惠券名称快照")
    @TableField("coupon_name")
    private String couponName;

    @ApiModelProperty(value = "领取时的门槛金额快照")
    @TableField("threshold_amount")
    private BigDecimal thresholdAmount;

    @ApiModelProperty(value = "领取时的优惠金额快照")
    @TableField("discount_amount")
    private BigDecimal discountAmount;

    @ApiModelProperty(value = "状态：0可使用，1已锁定，2已使用，3已过期")
    @TableField("status")
    private UserCouponStatus status;

    @ApiModelProperty(value = "锁定或使用该券的订单ID")
    @TableField("order_id")
    private Long orderId;

    @ApiModelProperty(value = "领取时间")
    @TableField("receive_time")
    private LocalDateTime receiveTime;

    @ApiModelProperty(value = "使用开始时间")
    @TableField("valid_start_time")
    private LocalDateTime validStartTime;

    @ApiModelProperty(value = "使用截止时间")
    @TableField("valid_end_time")
    private LocalDateTime validEndTime;

    @ApiModelProperty(value = "锁定时间")
    @TableField("locked_time")
    private LocalDateTime lockedTime;

    @ApiModelProperty(value = "核销时间")
    @TableField("used_time")
    private LocalDateTime usedTime;

    @ApiModelProperty(value = "创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    @TableField("update_time")
    private LocalDateTime updateTime;


}
