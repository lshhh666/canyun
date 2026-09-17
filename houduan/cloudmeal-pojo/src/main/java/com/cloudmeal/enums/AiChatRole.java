package com.cloudmeal.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * AI 客服消息角色。
 */
@Getter
public enum AiChatRole {

    USER("USER", "用户"),
    ASSISTANT("ASSISTANT", "AI客服");

    /** 写入数据库role字段的值。 */
    @EnumValue
    private final String value;

    private final String desc;

    AiChatRole(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
