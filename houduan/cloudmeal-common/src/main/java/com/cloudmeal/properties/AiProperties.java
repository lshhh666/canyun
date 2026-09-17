package com.cloudmeal.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "cloudmeal.ai")
public class AiProperties {

    private String baseUrl;
    private String model;
    private String embeddingModel;
    private int embeddingDimensions;
    private int retrievalTopK;
    private double retrievalMinScore;
    private String apiKey;
    private int connectTimeout;
    private int readTimeout;
    // 一轮处理的占用期限（秒）；过期后允许下一次请求接替，不会主动中断外部HTTP调用。
    private int turnTimeoutSeconds = 150;
    // 取消等敏感动作等待前端按钮确认的有效期（秒）。
    private int actionConfirmTimeoutSeconds = 300;
    // 用户多久没有继续对话后，不再恢复该会话（分钟）。
    private int sessionIdleTimeoutMinutes = 30;
    private int maxTokens;
}
