package com.sky.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * AI 客服知识状态。
 */
@Getter
public enum AiKnowledgeStatus {

    DISABLED(0, "停用"),
    ENABLED(1, "启用");

    /** 写入数据库的状态值。 */
    @EnumValue
    private final int value;

    private final String desc;

    AiKnowledgeStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
