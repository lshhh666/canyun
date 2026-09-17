package com.sky.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 第一次模型调用的工具选择结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiToolCallDecision {
    private boolean toolRequested;
    private ChatToolCall toolCall;

    public static AiToolCallDecision noTool() {
        return AiToolCallDecision.builder().toolRequested(false).build();
    }

    public static AiToolCallDecision requested(ChatToolCall toolCall) {
        return AiToolCallDecision.builder()
                .toolRequested(true)
                .toolCall(toolCall)
                .build();
    }
}
