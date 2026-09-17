package com.cloudmeal.vo;

import com.cloudmeal.enums.AiChatFeedbackHandleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 管理端未解决评价列表项。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatFeedbackPageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long feedbackId;
    private Long sessionId;
    private Long userId;
    private String userQuestion;
    private String aiAnswer;
    private AiChatFeedbackHandleStatus handleStatus;
    private String handleStatusDesc;
    private LocalDateTime feedbackTime;
}
