package com.cloudmeal.service;

import java.time.LocalDateTime;

/** 向量任务完成阶段的短事务状态落库。 */
public interface AiKnowledgeEmbeddingPersistenceService {

    /** 返回false表示知识版本或任务抢占令牌已经失效。 */
    boolean complete(Long taskId, Long knowledgeId, LocalDateTime processingTime,
                     String embedding, String embeddingModel, int embeddingDimensions,
                     LocalDateTime now);

    void recordFailure(Long taskId, Long knowledgeId, LocalDateTime processingTime,
                       String lastError, LocalDateTime nextRetryTime, LocalDateTime now);

    void markObsolete(Long taskId, Long knowledgeId,
                      LocalDateTime processingTime, LocalDateTime now);
}
