package com.cloudmeal.enums;

import lombok.Getter;

/**
 * 检索到的权威知识是否足以回答用户当前问题。
 */
@Getter
public enum AiEvidenceDecision {
    ANSWERABLE("ANSWERABLE", "证据足够"),
    INSUFFICIENT("INSUFFICIENT", "证据不足"),
    COUPON_ELIGIBILITY_REQUIRED(
            "COUPON_ELIGIBILITY_REQUIRED",
            "需要查询当前用户优惠券可用性"),
    ORDER_STATUS_REQUIRED(
            "ORDER_STATUS_REQUIRED",
            "需要查询当前用户订单状态"),
    ORDER_DETAIL_REQUIRED(
            "ORDER_DETAIL_REQUIRED",
            "需要查询当前会话已选订单详情");

    private final String code;
    private final String desc;

    AiEvidenceDecision(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
