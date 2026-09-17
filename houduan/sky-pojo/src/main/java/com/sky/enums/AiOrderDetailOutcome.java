package com.sky.enums;

import lombok.Getter;

/** AI客服查询会话已选订单详情后的确定性结果，不映射数据库。 */
@Getter
public enum AiOrderDetailOutcome {

    FOUND("已找到选中订单详情"),
    NO_SELECTED_ORDER("当前会话尚未选择订单"),
    ORDER_UNAVAILABLE("选中订单已无法查询");

    private final String desc;

    AiOrderDetailOutcome(String desc) {
        this.desc = desc;
    }
}
