package com.sky.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 提供给AI客服的单张优惠券最小必要判断结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCouponEligibilityItem {

    private String couponName;
    private BigDecimal thresholdAmount;
    private BigDecimal discountAmount;
    private String validEndTime;
    private Boolean eligible;
    private BigDecimal amountNeeded;
    private String reason;
}
