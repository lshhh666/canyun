package com.sky.service.model;

import com.sky.enums.AiOrderDetailOutcome;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** 当前会话已选订单的安全详情，不包含地址、手机号和收货人。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiOrderDetailResult {

    private AiOrderDetailOutcome outcome;
    private String orderNumberSuffix;
    private List<AiOrderDetailItem> items;
    private BigDecimal goodsAmount;
    private BigDecimal packAmount;
    private BigDecimal deliveryFee;
    private BigDecimal originalAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private String remark;
}
