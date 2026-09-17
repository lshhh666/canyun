package com.sky.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智谱对话接口的响应格式配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseFormat {

    /** 响应类型，例如json_object。 */
    private String type;
}
