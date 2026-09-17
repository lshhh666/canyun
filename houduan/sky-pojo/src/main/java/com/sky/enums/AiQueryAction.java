package com.sky.enums;

import lombok.Getter;

@Getter
public enum AiQueryAction {
    SEARCH("SEARCH", "执行检索"),
    CLARIFY("CLARIFY", "先澄清");
    private final String code;
    private final String desc;

    AiQueryAction(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
