package com.sky.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/** AI客服会话评价结果。 */
@Getter
public enum AiChatFeedbackResult {

    HELPFUL(1, "有帮助"),
    UNSOLVED(2, "没解决");

    /** 写入数据库的结果值。 */
    @EnumValue
    private final int value;

    private final String desc;

    AiChatFeedbackResult(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
