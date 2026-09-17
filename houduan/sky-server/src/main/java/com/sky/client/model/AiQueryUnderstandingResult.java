package com.sky.client.model;

import com.sky.enums.AiQueryAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 模型结合最近对话后返回的问题理解结果，在Client与Service之间传递，不映射数据库表。
 * 这是待校验的模型输出，后端校验通过后才能据此执行检索或返回澄清问题。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiQueryUnderstandingResult {

    /** 处理动作，不能为空：SEARCH（执行检索）或CLARIFY（先澄清）。 */
    private AiQueryAction action;

    /**
     * 补全上下文后的独立检索问题，SEARCH（执行检索）时必须有内容。
     * 仅用于本轮检索，不覆盖数据库中保存的用户原话；CLARIFY（先澄清）时可为空。
     */
    private String query;

    /**
     * 要向用户提出的澄清问题，CLARIFY（先澄清）时必须有内容。
     * 校验后作为ASSISTANT（客服消息）保存并返回；SEARCH（执行检索）时可为空。
     */
    private String clarification;
}
