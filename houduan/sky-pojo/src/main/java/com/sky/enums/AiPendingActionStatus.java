package com.sky.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/** AI客服待确认动作状态。 */
@Getter
public enum AiPendingActionStatus {

    PENDING_CONFIRMATION(1, "等待用户确认"),
    SUCCEEDED(2, "执行成功"),
    REJECTED(3, "状态变化，拒绝执行"),
    EXPIRED(4, "确认已过期"),
    SUPERSEDED(5, "已被新的确认动作替代");

    @EnumValue
    private final int value;
    private final String desc;

    AiPendingActionStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
