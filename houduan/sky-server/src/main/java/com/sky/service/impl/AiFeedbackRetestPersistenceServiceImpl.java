package com.sky.service.impl;

import com.sky.mapper.AiFeedbackRetestMapper;
import com.sky.service.AiFeedbackRetestPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 每个方法返回前提交事务，模型调用期间不占用数据库连接和行锁。 */
@Service
@RequiredArgsConstructor
public class AiFeedbackRetestPersistenceServiceImpl
        implements AiFeedbackRetestPersistenceService {

    private final AiFeedbackRetestMapper retestMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean claim(Long retestId, LocalDateTime processingTime) {
        return retestMapper.claimPendingRetest(retestId, processingTime) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean complete(Long retestId, LocalDateTime processingTime,
                            String answer, String usedKnowledgeIds,
                            LocalDateTime now) {
        return retestMapper.markExecutionSucceeded(
                retestId, processingTime, answer, usedKnowledgeIds, now) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean recordFailure(Long retestId, LocalDateTime processingTime,
                                 String lastError, LocalDateTime nextRetryTime,
                                 LocalDateTime now) {
        return retestMapper.markRetryFailure(
                retestId, processingTime, lastError, nextRetryTime, now) == 1;
    }
}
