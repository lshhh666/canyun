package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatVO implements Serializable {

    /** 本次聊天实际使用的会话ID。 */
    private Long sessionId;

    /** AI客服回答。 */
    private String answer;

    /** 需要用户通过前端按钮确认的动作；普通回答时为空。 */
    private AiChatActionVO action;
}
