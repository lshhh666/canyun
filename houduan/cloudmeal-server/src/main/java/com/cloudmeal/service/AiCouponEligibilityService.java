package com.cloudmeal.service;

import com.cloudmeal.service.model.AiCouponEligibilityResult;

/**
 * AI客服查询当前登录用户优惠券可用性的受控业务服务。
 */
public interface AiCouponEligibilityService {

    /**
     * 使用后端登录上下文、购物车和优惠券快照计算可用性，不接受用户ID等身份参数。
     */
    AiCouponEligibilityResult checkMyCouponEligibility();
}
