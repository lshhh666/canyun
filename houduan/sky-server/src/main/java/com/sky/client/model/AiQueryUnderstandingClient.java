package com.sky.client.model;

import com.sky.entity.AiChatMessage;

import java.util.List;

/**
 * 调用外部模型理解用户问题，并返回后端可判断的结构化结果。
 * 传入的消息必须已经由Service完成用户鉴权和会话归属校验。
 */
public interface AiQueryUnderstandingClient {

    /**
     * 结合最近对话判断本轮应该执行检索还是先澄清。
     *
     * @param messages 当前会话最近的消息，按消息顺序从旧到新排列
     * @return 模型返回的问题理解结果
     */
    AiQueryUnderstandingResult understand(List<AiChatMessage> messages);
}
