package com.sky.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "sky.ai")
public class AiProperties {

    private String baseUrl;
    private String model;
    private String apiKey;
    private int connectTimeout;
    private int readTimeout;
    private int maxTokens;
}
