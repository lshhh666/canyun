package com.cloudmeal.service;

import com.cloudmeal.service.model.AiToolAnswer;

/** AI客服取消订单的准备与确认服务。 */
public interface AiOrderCancellationService {

    AiToolAnswer prepareCancellation(Long sessionId, Long userId);

    /** 对刚从当前用户订单列表中解析出的订单创建取消确认。 */
    AiToolAnswer prepareOrderCancellation(Long orderId, Long userId);

    AiToolAnswer checkSelectedOrderCancellation(Long sessionId, Long userId);

    AiToolAnswer checkOrderCancellation(Long orderId, Long userId);

    Long getOwnedActionSessionId(Long actionId, Long userId);

    String confirmCancellation(Long actionId, Long userId);
}
