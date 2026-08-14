package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopInfoVO implements Serializable {
    private Long shopId;
    private String shopName;
    private String shopAddress;
    private String phone;
    private BigDecimal deliveryFee;
    private Integer status;
}
