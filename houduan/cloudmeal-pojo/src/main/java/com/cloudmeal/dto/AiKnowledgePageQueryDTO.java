package com.cloudmeal.dto;

import com.cloudmeal.enums.AiKnowledgeCategory;
import com.cloudmeal.enums.AiKnowledgeEmbeddingStatus;
import lombok.Data;

import java.io.Serializable;

/** 管理端当前知识版本分页查询条件。 */
@Data
public class AiKnowledgePageQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer page;
    private Integer pageSize;
    private String keyword;
    private AiKnowledgeCategory category;
    private AiKnowledgeEmbeddingStatus embeddingStatus;
}
