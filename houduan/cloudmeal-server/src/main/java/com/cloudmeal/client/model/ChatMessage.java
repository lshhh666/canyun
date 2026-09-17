package com.cloudmeal.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {

    /**
     * 消息角色，例如 system、user、assistant 或 tool
     */
    private String role;

    /**
     * 消息的文本内容
     */
    private String content;

    /** assistant消息请求执行的工具列表。 */
    @JsonProperty("tool_calls")
    private java.util.List<ChatToolCall> toolCalls;

    /** tool消息对应的工具调用ID。 */
    @JsonProperty("tool_call_id")
    private String toolCallId;
}
