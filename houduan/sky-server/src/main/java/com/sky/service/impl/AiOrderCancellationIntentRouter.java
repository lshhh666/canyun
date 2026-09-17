package com.sky.service.impl;

import com.sky.entity.AiChatMessage;
import com.sky.enums.AiChatRole;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/** 识别明确取消意图及其订单选择回复，规则咨询不会命中。 */
@Component
public class AiOrderCancellationIntentRouter {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s，。！？?!,.：:；;]");
    private static final Pattern ORDER_NUMBER_SUFFIX =
            Pattern.compile("(?<!\\d)(\\d{4,})(?!\\d)");
    private static final Pattern ORDER_NUMBER_SUFFIX_ONLY =
            Pattern.compile("^\\d{4,}$");

    public boolean isCancellationRequest(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String text = SEPARATORS.matcher(message.trim()).replaceAll("");
        boolean cancellation = text.contains("取消") || text.contains("退单")
                || text.contains("退掉");
        boolean orderReference = text.contains("这单") || text.contains("这笔订单")
                || text.contains("这个订单") || text.contains("刚才那单")
                || text.contains("当前订单") || text.contains("我的订单")
                || text.contains("它");
        boolean imperative = text.contains("帮我") || text.contains("请帮")
                || text.contains("给我取消") || text.startsWith("取消")
                || text.contains("退掉") || text.contains("想取消")
                || text.contains("要取消") || text.contains("想退单")
                || text.contains("要退单");
        boolean negated = text.contains("不想取消") || text.contains("不要取消")
                || text.contains("不用取消") || text.contains("别取消")
                || text.contains("不想退单") || text.contains("不要退单");
        boolean knowledgeQuestion = isCancellationEligibilityQuestion(text)
                || text.contains("怎么取消") || text.contains("如何取消")
                || text.contains("取消规则") || text.contains("取消流程")
                || text.contains("取消后") || text.contains("退款");
        return cancellation && (orderReference || imperative)
                && !negated && !knowledgeQuestion;
    }

    /**
     * 只有紧邻“请选择要取消的订单尾号”的回复时，纯数字才延续取消流程。
     * 普通订单查询后的尾号选择仍只查询状态，不会误创建取消动作。
     */
    public boolean isDisplayedCancellationSelection(List<AiChatMessage> messages,
                                                     String latestMessage) {
        if (!StringUtils.hasText(latestMessage)
                || !ORDER_NUMBER_SUFFIX_ONLY.matcher(latestMessage.trim()).matches()
                || messages == null || messages.isEmpty()) {
            return false;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            AiChatMessage message = messages.get(i);
            if (message == null || message.getRole() == null
                    || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            if (message.getRole() == AiChatRole.USER) {
                if (message.getContent().trim().equals(latestMessage.trim())) {
                    continue;
                }
                return false;
            }
            return message.getRole() == AiChatRole.ASSISTANT
                    && message.getContent().contains("请选择要取消的订单尾号");
        }
        return false;
    }

    /** 识别“这单/某尾号能不能取消”一类资格咨询，不创建待确认动作。 */
    public boolean isCancellationEligibilityQuestion(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String text = SEPARATORS.matcher(message.trim()).replaceAll("");
        boolean cancellation = text.contains("取消") || text.contains("退单");
        boolean asksEligibility = text.contains("能取消") || text.contains("可以取消")
                || text.contains("可取消") || text.contains("能不能取消")
                || text.contains("可不可以取消") || text.contains("是否能取消")
                || text.contains("是否可以取消");
        if (!cancellation || !asksEligibility) {
            return false;
        }
        boolean selectedOrderReference = text.contains("这单")
                || text.contains("这笔订单") || text.contains("这个订单")
                || text.contains("当前订单") || text.contains("刚才那单")
                || text.contains("我的订单") || text.contains("它")
                || ORDER_NUMBER_SUFFIX.matcher(text).find();
        if (selectedOrderReference) {
            return true;
        }
        if (isGeneralCancellationRuleQuestion(text)) {
            return false;
        }
        // “能取消吗”通常是对刚刚已选订单的追问；带“订单”但没有具体指代时，
        // 则按规则咨询处理，避免无意义地要求用户选择尾号。
        boolean contextualFollowUp = !text.contains("订单") && !text.contains("单");
        return selectedOrderReference || contextualFollowUp;
    }

    private boolean isGeneralCancellationRuleQuestion(String text) {
        String[] markers = {
                "什么订单", "哪些订单", "哪种订单", "什么情况", "哪些情况",
                "待付款", "待接单", "已接单", "配送中", "已完成", "已取消"
        };
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /** 只在取消资格问题中提取用户明确写出的订单尾号。 */
    public String extractOrderNumberSuffix(String message) {
        if (!isCancellationEligibilityQuestion(message)) {
            return null;
        }
        Matcher matcher = ORDER_NUMBER_SUFFIX.matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }
}
