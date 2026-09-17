package com.sky.service.model;

import lombok.Value;

/**
 * 后端持有的本轮处理凭据，由保存用户消息的短事务生成，不接收前端提供的轮次。
 * sessionId标识会话，processingSequenceNo标识本轮USER（用户消息）的顺序号。
 * 回答保存和失败恢复必须携带同一份凭据，避免旧请求影响接替它的新请求。
 */
@Value
public class AiChatTurnClaim {
    Long sessionId;
    Integer processingSequenceNo;
}
