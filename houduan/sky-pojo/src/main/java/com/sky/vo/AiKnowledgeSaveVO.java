package com.sky.vo;

import com.sky.enums.AiKnowledgeEmbeddingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 知识保存后的即时结果；向量由后台继续同步。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiKnowledgeSaveVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long knowledgeId;
    private AiKnowledgeEmbeddingStatus embeddingStatus;
    private String message;
}
