package com.cloudmeal.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/** AI知识向量生成状态。 */
@Getter
public enum AiKnowledgeEmbeddingStatus {

    PENDING(0, "待生成"),
    READY(1, "可用"),
    FAILED(2, "生成失败");

    @EnumValue
    private final int value;

    private final String desc;

    AiKnowledgeEmbeddingStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
