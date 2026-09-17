package com.sky.service;

import com.sky.service.model.AiOrderDetailResult;

/** 查询当前登录用户在指定会话中已经选中的订单详情。 */
public interface AiOrderDetailService {

    AiOrderDetailResult querySelectedOrderDetail(Long sessionId);
}
