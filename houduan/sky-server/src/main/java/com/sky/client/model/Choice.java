package com.sky.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Choice {

    /**
     * 候选回答在 choices 数组中的位置
     */
    private Integer index;

    /**
     * 模型生成的候选消息
     */
    private ChatMessage message;

    /**
     * 模型停止生成的原因，例如 stop、length 或 tool_calls
     */
    @JsonProperty("finish_reason")
    private String finishReason;
}
