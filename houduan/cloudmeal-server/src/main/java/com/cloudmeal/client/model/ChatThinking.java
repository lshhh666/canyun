package com.cloudmeal.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatThinking {

    /**
     * 模型思考模式，disabled 表示关闭深度思考并直接生成回答
     */
    private String type;
}
