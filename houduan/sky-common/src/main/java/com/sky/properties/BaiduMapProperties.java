package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sky.baidu-map")
@Data
public class BaiduMapProperties {

    private String ak;
    private String sk;
    private String shopAddress;

}