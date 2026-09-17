package com.sky.vo;

import com.sky.enums.AiChatFeedbackHandleStatus;
import com.sky.enums.AiFeedbackRetestExecutionStatus;
import com.sky.enums.AiFeedbackRetestReviewResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 管理端查看并人工确认的最新复测详情。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiFeedbackRetestDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long feedbackId;
    private Long retestId;
    private String question;
    private String answer;
    private String usedKnowledgeIds;
    private AiFeedbackRetestExecutionStatus executionStatus;
    private String executionStatusDesc;
    private AiFeedbackRetestReviewResult reviewResult;
    private String reviewResultDesc;
    private Integer retryCount;
    private Integer maxRetryCount;
    private LocalDateTime nextRetryTime;
    private String lastError;
    private AiChatFeedbackHandleStatus handleStatus;
    private String handleStatusDesc;
    private LocalDateTime updateTime;
    private String message;
}
