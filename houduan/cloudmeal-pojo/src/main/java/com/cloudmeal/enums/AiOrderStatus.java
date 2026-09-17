package com.cloudmeal.enums;

import lombok.Getter;

/**
 * AI客服允许对外说明的进行中订单状态。
 * 该枚举是服务层模型，不直接映射数据库。
 */
@Getter
public enum AiOrderStatus {

    PENDING_PAYMENT(1, "待付款"),
    TO_BE_CONFIRMED(2, "待接单"),
    CONFIRMED(3, "已接单"),
    DELIVERY_IN_PROGRESS(4, "配送中");

    private final int code;
    private final String desc;

    AiOrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static AiOrderStatus fromCode(Integer code) {
        if (code != null) {
            for (AiOrderStatus status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
        }
        throw new IllegalArgumentException("unsupported active order status");
    }
}
