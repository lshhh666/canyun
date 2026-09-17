package com.sky.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingRequest {

    /**
     * 生成文本向量所使用的模型名称，例如 embedding-3
     */
    private String model;

    /**
     * 需要生成向量的完整文本
     */
    private String input;

    /**
     * 返回向量的维度，餐云第一版固定配置为 256
     */
    private Integer dimensions;
}
