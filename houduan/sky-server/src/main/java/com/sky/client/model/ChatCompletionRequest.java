package com.sky.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatCompletionRequest {

    /**
     * 本次请求使用的模型名称，例如 glm-4.7-flash
     */
    private String model;

    /**
     * 按对话顺序发送给模型的消息列表
     */
    private List<ChatMessage> messages;

    /**
     * 是否使用流式响应，餐云第一版固定为 false
     */
    private Boolean stream;

    /**
     * 模型思考模式配置
     */
    private ChatThinking thinking;

    /**
     * 模型单次回答允许生成的最大 Token 数量
     */
    @JsonProperty("max_tokens")
    private Integer maxTokens;
}
