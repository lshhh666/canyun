package com.cloudmeal.service;

import com.cloudmeal.service.model.AiToolAnswer;

/**
 * 在证据层确认需要实时数据后，执行白名单内的受控只读工具。
 */
public interface AiToolCallingService {

    /**
     * @return Java根据后端实时数据生成的确定性客服答案。
     */
    String answerCouponEligibility();

    /**
     * 查询当前用户进行中订单；多笔订单时返回可选择的安全摘要。
     *
     * @param latestMessage 当前用户本轮原始消息，用于读取明确回复的订单尾号
     */
    String answerOrderStatus(String latestMessage);

    /** 与订单状态回答一起返回需要原子写入会话的选中订单ID。 */
    AiToolAnswer answerOrderStatusWithContext(String latestMessage);

    /** 查询当前会话已经选中的订单详情。 */
    String answerSelectedOrderDetail(Long sessionId, String latestMessage);
}
