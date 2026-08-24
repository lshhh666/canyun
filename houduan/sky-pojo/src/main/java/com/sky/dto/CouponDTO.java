package com.sky.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理端创建或编辑优惠券时提交的业务字段。
 * 状态、剩余库存和审计时间由服务端维护，不能由前端指定。
 */
@Data
public class CouponDTO implements Serializable {

    private String name;
    private BigDecimal thresholdAmount;
    private BigDecimal discountAmount;
    private Integer totalStock;
    private LocalDateTime receiveStartTime;
    private LocalDateTime receiveEndTime;
    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;
}
