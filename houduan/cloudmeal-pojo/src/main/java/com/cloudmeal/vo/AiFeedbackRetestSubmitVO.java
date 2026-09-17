package com.cloudmeal.vo;

import com.cloudmeal.enums.AiChatFeedbackHandleStatus;
import com.cloudmeal.enums.AiFeedbackRetestExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 发起复测后的即时状态；模型由后台任务异步调用。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiFeedbackRetestSubmitVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long feedbackId;
    private Long retestId;
    private AiChatFeedbackHandleStatus handleStatus;
    private AiFeedbackRetestExecutionStatus executionStatus;
    private String message;
}
