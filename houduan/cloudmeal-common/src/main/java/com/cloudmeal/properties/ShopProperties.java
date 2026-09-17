package com.cloudmeal.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Data
@Component
@ConfigurationProperties(prefix = "cloudmeal.shop")
public class ShopProperties {
    private Long id;
    private String name;
    private String phone;
    private BigDecimal deliveryFee;
    private BigDecimal packFeePerItem;
    private Integer estimatedDeliveryMinutes;
    private Integer maxDeliveryDistanceMeters;
}
