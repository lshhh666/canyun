package com.cloudmeal.service.impl;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/** 对明确指向会话已选订单的详情问题做后端快速路由。 */
@Component
public class AiOrderDetailIntentRouter {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s，。！？?!,.：:；;]");

    public boolean isSelectedOrderDetailQuestion(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String text = SEPARATORS.matcher(message.trim()).replaceAll("");
        boolean selectedOrderReference = text.contains("这单")
                || text.contains("这笔订单")
                || text.contains("这个订单")
                || text.contains("刚才那单")
                || text.contains("刚才的订单")
                || text.contains("当前订单");
        boolean asksDetail = text.contains("买了什么")
                || text.contains("点了什么")
                || text.contains("有哪些菜")
                || text.contains("订单明细")
                || text.contains("订单详情")
                || text.contains("一共多少钱")
                || text.contains("总共多少钱")
                || text.contains("订单金额")
                || text.contains("实付金额")
                || text.contains("打包费")
                || text.contains("配送费")
                || text.contains("优惠了多少")
                || text.contains("订单备注")
                || text.contains("备注");
        return selectedOrderReference && asksDetail;
    }
}
