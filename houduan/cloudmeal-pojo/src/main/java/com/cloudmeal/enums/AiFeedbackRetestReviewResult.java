package com.cloudmeal.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/** 管理员对一次成功复测回答的人工判断。 */
@Getter
public enum AiFeedbackRetestReviewResult {

    INCORRECT(0, "回答错误"),
    CORRECT(1, "回答正确");

    @EnumValue
    private final int value;

    private final String desc;

    AiFeedbackRetestReviewResult(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
