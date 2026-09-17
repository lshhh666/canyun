package com.sky.service;

import com.sky.service.model.AiOrderStatusResult;

/**
 * AI客服查询当前登录用户进行中订单的受控只读服务。
 */
public interface AiOrderStatusService {

    /**
     * @param orderNumberSuffix 用户选择的订单号尾号；首次查询时可以为空
     */
    AiOrderStatusResult queryMyActiveOrder(String orderNumberSuffix);
}
