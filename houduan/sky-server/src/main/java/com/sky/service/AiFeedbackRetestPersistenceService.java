package com.sky.service;

import java.time.LocalDateTime;

/** 复测任务抢占及完成状态的短事务服务。 */
public interface AiFeedbackRetestPersistenceService {

    boolean claim(Long retestId, LocalDateTime processingTime);

    boolean complete(Long retestId, LocalDateTime processingTime,
                     String answer, String usedKnowledgeIds,
                     LocalDateTime now);

    boolean recordFailure(Long retestId, LocalDateTime processingTime,
                          String lastError, LocalDateTime nextRetryTime,
                          LocalDateTime now);
}
