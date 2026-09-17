package com.sky.service.impl;

import com.sky.entity.AiKnowledge;
import com.sky.entity.AiKnowledgeEmbeddingTask;
import com.sky.enums.AiKnowledgeEmbeddingStatus;
import com.sky.enums.AiKnowledgeEmbeddingTaskStatus;
import com.sky.enums.AiKnowledgeStatus;
import com.sky.mapper.AiKnowledgeEmbeddingTaskMapper;
import com.sky.mapper.AiKnowledgeMapper;
import com.sky.service.AiKnowledgeEmbeddingPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/** 通过任务行锁和processingTime令牌拦截迟到结果。 */
@Service
@RequiredArgsConstructor
public class AiKnowledgeEmbeddingPersistenceServiceImpl
        implements AiKnowledgeEmbeddingPersistenceService {

    private final AiKnowledgeEmbeddingTaskMapper taskMapper;
    private final AiKnowledgeMapper knowledgeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean complete(Long taskId, Long knowledgeId, LocalDateTime processingTime,
                            String embedding, String embeddingModel, int embeddingDimensions,
                            LocalDateTime now) {
        AiKnowledgeEmbeddingTask task = requireCurrentClaim(taskId, knowledgeId, processingTime);
        if (task == null) {
            return false;
        }
        AiKnowledge knowledge = knowledgeMapper.selectByIdForUpdate(knowledgeId);
        if (!isCurrentPendingVersion(knowledge)) {
            requireSingleRow(taskMapper.markObsolete(taskId, now));
            return false;
        }
        requireSingleRow(knowledgeMapper.markEmbeddingReady(
                knowledgeId, embedding, embeddingModel, embeddingDimensions, now));
        requireSingleRow(taskMapper.markSucceeded(taskId, processingTime, now));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordFailure(Long taskId, Long knowledgeId, LocalDateTime processingTime,
                              String lastError, LocalDateTime nextRetryTime, LocalDateTime now) {
        AiKnowledgeEmbeddingTask task = requireCurrentClaim(taskId, knowledgeId, processingTime);
        if (task == null) {
            return;
        }
        AiKnowledge knowledge = knowledgeMapper.selectByIdForUpdate(knowledgeId);
        if (!isCurrentPendingVersion(knowledge)) {
            requireSingleRow(taskMapper.markObsolete(taskId, now));
            return;
        }

        boolean finalFailure = task.getRetryCount() != null
                && task.getMaxRetryCount() != null
                && task.getRetryCount() >= task.getMaxRetryCount();
        requireSingleRow(taskMapper.markRetryFailure(
                taskId, processingTime, lastError, nextRetryTime, now));
        if (finalFailure) {
            requireSingleRow(knowledgeMapper.markEmbeddingFailed(knowledgeId, now));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markObsolete(Long taskId, Long knowledgeId,
                             LocalDateTime processingTime, LocalDateTime now) {
        AiKnowledgeEmbeddingTask task = requireCurrentClaim(taskId, knowledgeId, processingTime);
        if (task != null) {
            requireSingleRow(taskMapper.markObsolete(taskId, now));
        }
    }

    private AiKnowledgeEmbeddingTask requireCurrentClaim(
            Long taskId, Long knowledgeId, LocalDateTime processingTime) {
        AiKnowledgeEmbeddingTask task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null
                || task.getStatus() != AiKnowledgeEmbeddingTaskStatus.PROCESSING
                || !Objects.equals(knowledgeId, task.getKnowledgeId())
                || !Objects.equals(processingTime, task.getProcessingTime())) {
            return null;
        }
        return task;
    }

    private boolean isCurrentPendingVersion(AiKnowledge knowledge) {
        return knowledge != null
                && knowledge.getStatus() == AiKnowledgeStatus.ENABLED
                && knowledge.getEmbeddingStatus() == AiKnowledgeEmbeddingStatus.PENDING;
    }

    private void requireSingleRow(int affectedRows) {
        if (affectedRows != 1) {
            throw new IllegalStateException("AI知识向量任务状态更新冲突");
        }
    }
}
