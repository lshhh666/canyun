package com.sky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sky.enums.CouponStatus;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("coupon")
@ApiModel(value = "Coupon对象", description = "优惠券模板")
public class Coupon implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "优惠券ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "优惠券名称")
    @TableField("name")
    private String name;

    @ApiModelProperty(value = "使用门槛金额")
    @TableField("threshold_amount")
    private BigDecimal thresholdAmount;

    @ApiModelProperty(value = "优惠金额")
    @TableField("discount_amount")
    private BigDecimal discountAmount;

    @ApiModelProperty(value = "发行总量")
    @TableField("total_stock")
    private Integer totalStock;

    @ApiModelProperty(value = "剩余库存")
    @TableField("stock")
    private Integer stock;

    @ApiModelProperty(value = "领取开始时间")
    @TableField("receive_start_time")
    private LocalDateTime receiveStartTime;

    @ApiModelProperty(value = "领取结束时间")
    @TableField("receive_end_time")
    private LocalDateTime receiveEndTime;

    @ApiModelProperty(value = "使用开始时间")
    @TableField("valid_start_time")
    private LocalDateTime validStartTime;

    @ApiModelProperty(value = "使用结束时间")
    @TableField("valid_end_time")
    private LocalDateTime validEndTime;

    @ApiModelProperty(value = "状态：0草稿，1发放中，2已停用")
    @TableField("status")
    private CouponStatus status;

    @ApiModelProperty(value = "创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    @TableField("update_time")
    private LocalDateTime updateTime;
}