package com.cloudmeal.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingResponse {

    /**
     * 各输入文本对应的向量结果列表；餐云目前只读取第一条结果
     */
    private List<EmbeddingData> data;
}
