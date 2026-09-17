package com.cloudmeal.service.model;

/** 人工重新同步知识时的幂等结果。 */
public enum AiKnowledgeRetryOutcome {
    /** 失败任务已重置并重新进入等待队列。 */
    QUEUED,
    /** 任务原本已经在等待或处理中。 */
    PROCESSING,
    /** 知识向量已经可用，无需重新执行。 */
    READY
}
