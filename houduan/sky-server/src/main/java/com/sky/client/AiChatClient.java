package com.sky.client;

import com.sky.entity.AiChatMessage;
import com.sky.client.model.AiToolCallDecision;
import com.sky.client.model.ChatTool;

import java.util.List;

public interface AiChatClient {

    String chat(String systemPrompt, List<AiChatMessage> messages);

    /**
     * 要求模型返回一个JSON对象，供后端完成结构化解析和业务校验。
     */
    String chatAsJson(String systemPrompt, List<AiChatMessage> messages);

    /**
     * 让模型判断是否需要调用白名单工具；此阶段的普通文本不会直接返回给用户。
     */
    AiToolCallDecision requestToolCall(String systemPrompt,
                                       List<AiChatMessage> messages,
                                       List<ChatTool> tools);
}
