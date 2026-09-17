package com.sky.enums;

import lombok.Getter;

/** 后端执行未支付订单取消时的确定性结果，不直接映射数据库字段。 */
@Getter
public enum AiOrderCancellationOutcome {

    CANCELLED("取消成功"),
    ALREADY_CANCELLED("订单已经取消"),
    NOT_CANCELLABLE("当前状态不允许取消"),
    ORDER_UNAVAILABLE("订单不存在或无权访问");

    private final String desc;

    AiOrderCancellationOutcome(String desc) {
        this.desc = desc;
    }
}
