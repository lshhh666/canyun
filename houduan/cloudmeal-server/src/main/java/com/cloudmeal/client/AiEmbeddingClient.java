package com.cloudmeal.client;

import java.util.List;

/**
 * 将文本转换为语义向量的受控客户端。
 */
public interface AiEmbeddingClient {

    /**
     * 对完整文本生成向量。知识入库时传入“标题 + 规则”，检索时传入用户原问题。
     *
     * @param text 需要理解语义的完整文本
     * @return 固定维度的文本向量
     */
    List<Double> embed(String text);
}
