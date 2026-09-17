package com.sky.vo;

import com.sky.enums.AiKnowledgeCategory;
import com.sky.enums.AiKnowledgeEmbeddingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 管理端知识管理列表项，不暴露向量内容。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiKnowledgePageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String knowledgeKey;
    private String title;
    private AiKnowledgeCategory category;
    private String categoryDesc;
    private String content;
    private AiKnowledgeEmbeddingStatus embeddingStatus;
    private String embeddingStatusDesc;
    private Integer versionNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
