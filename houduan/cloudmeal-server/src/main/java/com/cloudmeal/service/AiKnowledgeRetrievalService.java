package com.cloudmeal.service;

import com.cloudmeal.service.model.AiKnowledgeMatch;

import java.util.List;

/**
 * 在内部权威知识库中进行语义检索。
 */
public interface AiKnowledgeRetrievalService {

    /**
     * 检索与用户问题最相关的知识。
     *
     * @param question 完整的用户原始问题
     * @return 按相似度排序的内部知识匹配结果
     */
    List<AiKnowledgeMatch> retrieve(String question);
}
