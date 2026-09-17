package com.sky.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/** 管理端AI回答复测的执行状态。 */
@Getter
public enum AiFeedbackRetestExecutionStatus {

    PENDING(0, "待处理"),
    PROCESSING(1, "处理中"),
    SUCCEEDED(2, "成功"),
    FAILED(3, "失败"),
    OBSOLETE(4, "已失效");

    @EnumValue
    private final int value;

    private final String desc;

    AiFeedbackRetestExecutionStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
