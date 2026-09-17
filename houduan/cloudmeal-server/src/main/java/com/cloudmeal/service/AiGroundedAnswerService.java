package com.cloudmeal.service;

import com.cloudmeal.service.model.AiKnowledgeMatch;

import java.util.List;

/** 使用后端指定的权威知识生成回答，不执行任何业务工具。 */
public interface AiGroundedAnswerService {

    String NO_KNOWLEDGE_ANSWER = "暂时无法回答这个问题，请联系人工客服。";

    String generate(String question, List<AiKnowledgeMatch> knowledge);
}
