package com.sky.enums;

import lombok.Getter;

/**
 * AI客服查询优惠券后，由后端确定的下一步建议。
 * 该枚举不映射数据库，因此不需要MyBatis-Plus的EnumValue注解。
 */
@Getter
public enum AiCouponNextAction {

    USE_COUPON("可以使用优惠券"),
    ADD_ITEMS("向购物车添加商品"),
    ADD_MORE("继续凑单"),
    WAIT_FOR_VALIDITY("等待优惠券生效"),
    CHECK_ORDER("查看占用优惠券的订单"),
    RECEIVE_NEW_COUPON("领取新的优惠券");

    private final String desc;

    AiCouponNextAction(String desc) {
        this.desc = desc;
    }
}
