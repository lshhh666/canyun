package com.sky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sky.enums.AiKnowledgeCategory;
import com.sky.enums.AiKnowledgeEmbeddingStatus;
import com.sky.enums.AiKnowledgeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 客服权威知识。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_knowledge")
public class AiKnowledge implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 知识记录ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 同一业务规则跨版本保持不变的标识。 */
    @TableField("knowledge_key")
    private String knowledgeKey;

    /** 知识标题。 */
    @TableField("title")
    private String title;

    /** 业务分类，例如ORDER、COUPON、DISH、DELIVERY。 */
    @TableField("category")
    private AiKnowledgeCategory category;

    /** 提供给模型的权威规则正文。 */
    @TableField("content")
    private String content;

    /** 标题和规则正文生成的语义向量，以 JSON 数组保存。 */
    @TableField("embedding")
    private String embedding;

    /** 生成该向量所使用的模型名称。 */
    @TableField("embedding_model")
    private String embeddingModel;

    /** 向量维度，用于阻止不同维度的向量参与同一次计算。 */
    @TableField("embedding_dimensions")
    private Integer embeddingDimensions;

    /** 向量生成进度；只有READY状态可以参与RAG检索。 */
    @TableField("embedding_status")
    private AiKnowledgeEmbeddingStatus embeddingStatus;

    /** 版本号。 */
    @TableField("version_no")
    private Integer versionNo;

    /** 状态：0-停用，1-启用。 */
    @TableField("status")
    private AiKnowledgeStatus status;

    /** 创建时间。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
