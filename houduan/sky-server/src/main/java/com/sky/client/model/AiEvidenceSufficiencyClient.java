package com.sky.client.model;

import com.sky.entity.AiChatMessage;
import com.sky.enums.AiEvidenceDecision;

import java.util.List;

/**
 * 判断检索知识能否直接回答当前问题，不负责生成客服答案。
 */
public interface AiEvidenceSufficiencyClient {

    /**
     * @param messages 最近对话，按消息顺序从旧到新排列
     * @param references 本轮检索到的权威知识
     * @return ANSWERABLE（证据足够）、INSUFFICIENT（证据不足）
     * 、COUPON_ELIGIBILITY_REQUIRED（需要查询当前用户优惠券可用性）
     * 或ORDER_STATUS_REQUIRED（需要查询当前用户订单状态）
     */
    AiEvidenceDecision judge(List<AiChatMessage> messages,
                             List<AiEvidenceReference> references);
}
