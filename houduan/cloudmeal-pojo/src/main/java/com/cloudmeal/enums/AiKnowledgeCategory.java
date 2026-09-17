package com.cloudmeal.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * AI 客服知识业务分类。
 */
@Getter
public enum AiKnowledgeCategory {

    SHOP("SHOP", "门店规则"),
    ORDER("ORDER", "订单规则"),
    COUPON("COUPON", "优惠券规则"),
    DISH("DISH", "菜品信息"),
    DELIVERY("DELIVERY", "配送规则");

    /** 写入数据库category字段的值。 */
    @EnumValue
    private final String value;

    private final String desc;

    AiKnowledgeCategory(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
