package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPreviewVO implements Serializable {
    private BigDecimal goodsAmount;
    private BigDecimal packAmount;
    private BigDecimal deliveryFee;
    private BigDecimal totalAmount;
    private LocalDateTime estimatedDeliveryTime;
}
