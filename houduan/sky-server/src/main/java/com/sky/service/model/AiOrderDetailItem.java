package com.sky.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 提供给AI客服格式化层的安全订单明细。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiOrderDetailItem {

    private String name;
    private String flavor;
    private Integer quantity;
    private BigDecimal unitAmount;
}
