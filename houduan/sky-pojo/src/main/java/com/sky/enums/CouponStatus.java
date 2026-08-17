package com.sky.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum CouponStatus {
    DRAFT(0,"草稿"),
    DISTRIBUTING(1,"发放中"),
    DISABLED(2,"已停用")
    ;
    @EnumValue
    private final int value;
    private final String desc;

    CouponStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
