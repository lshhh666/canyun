package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 一批被评价会话各自最后一轮完整问答。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatFeedbackTurnVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long sessionId;
    private String userQuestion;
    private String aiAnswer;
}
