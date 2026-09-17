package com.cloudmeal.client.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模型可见的函数名称、用途和JSON参数约束。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatFunctionDefinition {
    private String name;
    private String description;
    private JsonNode parameters;
}
