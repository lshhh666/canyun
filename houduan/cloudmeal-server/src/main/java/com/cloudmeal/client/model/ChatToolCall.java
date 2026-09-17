package com.cloudmeal.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模型响应中的一次工具调用请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatToolCall {
    private String id;
    private String type;
    private ChatFunctionCall function;
}
