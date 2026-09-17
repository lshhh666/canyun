package com.cloudmeal.service.model;

import com.cloudmeal.enums.AiOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI客服可使用的订单最小信息，不包含手机号、地址和收货人等敏感字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiOrderStatusItem {

    /** 后端内部订单主键，不会拼入客服回答。 */
    private Long orderId;
    private String orderNumberSuffix;
    private AiOrderStatus status;
    private LocalDateTime orderTime;
    private LocalDateTime estimatedDeliveryTime;
}
