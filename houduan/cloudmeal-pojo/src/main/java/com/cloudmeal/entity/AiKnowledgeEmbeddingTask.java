package com.cloudmeal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmeal.enums.AiKnowledgeEmbeddingTaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 一个知识版本对应的一次异步向量生成任务。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_knowledge_embedding_task")
public class AiKnowledgeEmbeddingTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("knowledge_id")
    private Long knowledgeId;

    @TableField("status")
    private AiKnowledgeEmbeddingTaskStatus status;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("max_retry_count")
    private Integer maxRetryCount;

    @TableField("next_retry_time")
    private LocalDateTime nextRetryTime;

    @TableField("processing_time")
    private LocalDateTime processingTime;

    @TableField("success_time")
    private LocalDateTime successTime;

    /** 仅供内部排查，不得直接返回用户端。 */
    @TableField("last_error")
    private String lastError;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
