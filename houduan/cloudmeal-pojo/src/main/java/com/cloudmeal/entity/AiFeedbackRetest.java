package com.cloudmeal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmeal.enums.AiFeedbackRetestExecutionStatus;
import com.cloudmeal.enums.AiFeedbackRetestReviewResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 管理端对一条未解决评价执行的一次回答复测。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_feedback_retest")
public class AiFeedbackRetest implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("feedback_id")
    private Long feedbackId;

    /** 本次实际测试的问题快照，不覆盖用户原问题。 */
    @TableField("question")
    private String question;

    @TableField("answer")
    private String answer;

    /** JSON数组，保存生成回答时实际使用的知识版本ID。 */
    @TableField("used_knowledge_ids")
    private String usedKnowledgeIds;

    @TableField("execution_status")
    private AiFeedbackRetestExecutionStatus executionStatus;

    /** 模型调用成功后才允许人工填写；未确认时为null。 */
    @TableField("review_result")
    private AiFeedbackRetestReviewResult reviewResult;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("max_retry_count")
    private Integer maxRetryCount;

    @TableField("next_retry_time")
    private LocalDateTime nextRetryTime;

    @TableField("processing_time")
    private LocalDateTime processingTime;

    @TableField("initiator_id")
    private Long initiatorId;

    @TableField("reviewer_id")
    private Long reviewerId;

    @TableField("review_time")
    private LocalDateTime reviewTime;

    /** 内部错误摘要，不得直接透传到用户端。 */
    @TableField("last_error")
    private String lastError;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
