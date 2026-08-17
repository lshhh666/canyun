package com.sky.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum UserCouponStatus {
    AVAILABLE(0,"可使用"),
    LOCKED(1,"下单时已锁定"),
    USED(2,"已使用"),
    EXPIRED(3,"已过期");
    @EnumValue
    private final int value;
    private final String desc;

    UserCouponStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
