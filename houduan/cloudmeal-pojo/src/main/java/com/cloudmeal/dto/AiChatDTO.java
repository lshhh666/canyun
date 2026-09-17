package com.cloudmeal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatDTO implements Serializable {

    /** 会话ID，首次聊天时为空。 */
    private Long sessionId;

    /** 用户消息。 */
    private String message;
}
