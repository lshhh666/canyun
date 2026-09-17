package com.cloudmeal.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/** AI知识向量异步任务状态。 */
@Getter
public enum AiKnowledgeEmbeddingTaskStatus {

    PENDING(0, "待处理"),
    PROCESSING(1, "处理中"),
    SUCCEEDED(2, "成功"),
    FAILED(3, "失败"),
    OBSOLETE(4, "旧版本作废");

    @EnumValue
    private final int value;

    private final String desc;

    AiKnowledgeEmbeddingTaskStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
