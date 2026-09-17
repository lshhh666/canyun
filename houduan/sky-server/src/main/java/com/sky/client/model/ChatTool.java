package com.sky.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 发送给模型的函数工具定义。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatTool {
    private String type;
    private ChatFunctionDefinition function;
}
