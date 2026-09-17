package com.cloudmeal.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模型选择的函数及其JSON字符串参数。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatFunctionCall {
    private String name;
    private String arguments;
}
