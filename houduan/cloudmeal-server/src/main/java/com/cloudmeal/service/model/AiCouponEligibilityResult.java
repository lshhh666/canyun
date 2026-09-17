package com.cloudmeal.service.model;

import com.cloudmeal.enums.AiCouponNextAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 当前登录用户的购物车金额与优惠券可用性汇总。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCouponEligibilityResult {

    private BigDecimal goodsAmount;
    private Boolean cartEmpty;
    private AiCouponNextAction nextAction;
    private BigDecimal amountNeeded;
    private List<AiCouponEligibilityItem> coupons;
}
