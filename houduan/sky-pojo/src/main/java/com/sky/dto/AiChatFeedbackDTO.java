package com.sky.dto;

import com.sky.enums.AiChatFeedbackResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** AI客服会话结束后的评价请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatFeedbackDTO implements Serializable {

    /** 被评价的会话ID。 */
    private Long sessionId;

    /** HELPFUL（有帮助）或UNSOLVED（没解决）。 */
    private AiChatFeedbackResult result;
}
