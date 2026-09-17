package com.sky.exception;

import lombok.Getter;

/**
 * AI客服本轮对话失败异常。
 * 在保留原始异常原因的同时，将已经创建的会话ID交给前端。
 */
@Getter
public class AiChatTurnException extends BaseException {

    /** 已经保存用户消息的会话ID。 */
    private final Long sessionId;

    public AiChatTurnException(String message, Long sessionId, Throwable cause) {
        super(message, cause);
        this.sessionId = sessionId;
    }
}
