package com.sky.enums;

import lombok.Getter;

/**
 * AI客服查询当前用户进行中订单后的确定性结果。
 * 该枚举不映射数据库，因此不需要MyBatis-Plus的EnumValue注解。
 */
@Getter
public enum AiOrderQueryOutcome {

    FOUND("已定位一笔订单"),
    SELECTION_REQUIRED("需要用户选择订单"),
    ORDER_NOT_FOUND("未找到用户选择的进行中订单"),
    NO_ACTIVE_ORDER("没有进行中的订单");

    private final String desc;

    AiOrderQueryOutcome(String desc) {
        this.desc = desc;
    }
}
