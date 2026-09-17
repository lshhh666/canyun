package com.cloudmeal.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionRequest {

    /**
     * 本次请求使用的模型名称，例如 glm-4.5-air
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
     * 响应格式；普通问答不传，结构化理解请求传json_object。
     */
    @JsonProperty("response_format")
    private ChatResponseFormat responseFormat;

    /**
     * 采样随机性；结构化分类使用较低值以提高结果稳定性。
     */
    private Double temperature;

    /**
     * 模型单次回答允许生成的最大 Token 数量
     */
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    /** 可由模型选择的受控函数工具。 */
    private List<ChatTool> tools;

    /** 工具选择策略，智谱当前仅支持auto。 */
    @JsonProperty("tool_choice")
    private String toolChoice;
}
