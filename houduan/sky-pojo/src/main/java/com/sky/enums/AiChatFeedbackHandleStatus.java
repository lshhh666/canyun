package com.sky.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/** 管理端对“没解决”评价的处理进度。 */
@Getter
public enum AiChatFeedbackHandleStatus {

    PENDING(0, "待处理"),
    PENDING_RETEST(1, "待复测"),
    HANDLED(2, "已处理");

    @EnumValue
    private final int value;

    private final String desc;

    AiChatFeedbackHandleStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
