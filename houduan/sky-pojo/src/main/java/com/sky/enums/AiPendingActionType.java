package com.sky.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/** AI客服需要用户明确确认后才能执行的动作类型。 */
@Getter
public enum AiPendingActionType {

    CANCEL_ORDER(1, "取消订单");

    @EnumValue
    private final int value;
    private final String desc;

    AiPendingActionType(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
