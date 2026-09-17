package com.cloudmeal.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * AI 客服会话状态。
 */
@Getter
public enum AiChatSessionStatus {

    CLOSED(0, "已关闭"),
    ACTIVE(1, "可以发送消息"),
    PROCESSING(2, "AI正在回答");

    /** 写入数据库的状态值。 */
    @EnumValue
    private final int value;

    private final String desc;

    AiChatSessionStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
