package com.cloudmeal.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingData {

    /**
     * 本条结果在请求文本列表中的位置；餐云目前一次只发送一条文本，因此应为 0
     */
    private Integer index;

    /**
     * 模型返回的浮点数向量，用于计算文本之间的余弦相似度
     */
    private List<Double> embedding;
}
