package com.sky.service.impl;

import com.sky.entity.AiChatMessage;
import com.sky.enums.AiChatRole;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 对高置信度订单状态意图做本地路由，避免模型在无需澄清时反复追问。
 */
@Component
public class AiOrderStatusIntentRouter {

    private static final Pattern ORDER_REFERENCE = Pattern.compile("^\\d{4,}$");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s，。！？?!,.：:；;]");

    /**
     * 只匹配明确要求查询当前订单实时状态的说法；取消、退款等操作不会命中。
     */
    public boolean isExplicitOrderStatusQuestion(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String text = SEPARATORS.matcher(message.trim()).replaceAll("");
        if (isGeneralOrderKnowledgeQuestion(text) || isAnotherOrderBusinessIntent(text)) {
            return false;
        }

        boolean asksOwnOrder = text.contains("我的订单")
                || text.contains("这笔订单")
                || text.contains("当前订单");
        boolean asksTrackingState = text.contains("到哪")
                || text.contains("订单进度")
                || text.contains("订单状态")
                || text.contains("什么状态")
                || text.contains("接单了吗")
                || text.contains("有没有接单")
                || text.contains("开始配送了吗")
                || text.contains("正在配送吗")
                || text.contains("送到了吗")
                || text.contains("什么时候送到")
                || text.contains("多久送到")
                || text.contains("还要多久");

        return isOrderListQuery(text)
                || (asksOwnOrder && asksTrackingState)
                || text.contains("订单配送到哪")
                || text.contains("订单送到哪")
                || text.contains("商家接单了吗")
                || text.contains("商家有没有接单")
                || text.contains("骑手到哪");
    }

    /** 用户只说“查询订单”时，含义是查看本人进行中的订单列表。 */
    private boolean isOrderListQuery(String text) {
        String[] expressions = {
                "查询订单", "查订单", "查一下订单", "查看订单", "看看订单",
                "我的订单", "查询我的订单", "查看我的订单", "查一下我的订单",
                "我要查订单", "帮我查订单", "帮我查询订单"
        };
        for (String expression : expressions) {
            if (expression.equals(text)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 只有紧邻上一条订单工具回复时，纯数字才视为订单尾号选择。
     */
    public boolean isDisplayedOrderSelection(List<AiChatMessage> messages,
                                             String latestMessage) {
        if (!StringUtils.hasText(latestMessage)
                || !ORDER_REFERENCE.matcher(latestMessage.trim()).matches()
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
            if (message.getRole() == AiChatRole.ASSISTANT) {
                String content = message.getContent();
                boolean displayedChoices = content.contains("您有多笔进行中订单")
                        && content.contains("请回复上面显示的订单尾号");
                boolean displayedSelectedOrder = content.startsWith("订单尾号")
                        && content.contains("当前状态：");
                return displayedChoices || displayedSelectedOrder;
            }
        }
        return false;
    }

    private boolean isGeneralOrderKnowledgeQuestion(String text) {
        if (!text.contains("订单状态")) {
            return false;
        }
        String[] knowledgeMarkers = {
                "哪些", "几种", "分别", "各自", "意思",
                "代表", "含义", "规则", "流程", "流转", "变化"
        };
        for (String marker : knowledgeMarkers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAnotherOrderBusinessIntent(String text) {
        return text.contains("取消")
                || text.contains("退单")
                || text.contains("退款")
                || text.contains("退钱")
                || text.contains("售后")
                || text.contains("支付")
                || text.contains("付款")
                || text.contains("发票")
                || text.contains("评价")
                || text.contains("催单");
    }
}
