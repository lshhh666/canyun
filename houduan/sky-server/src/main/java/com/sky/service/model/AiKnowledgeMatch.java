package com.sky.service.model;

import com.sky.enums.AiKnowledgeCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 内部知识检索结果，不包含数据库中保存的文本向量。
 */
@Getter
@Builder
@AllArgsConstructor
public class AiKnowledgeMatch {

    private final Long id;
    private final String title;
    private final String content;
    private final AiKnowledgeCategory category;
    private final double score;
}
