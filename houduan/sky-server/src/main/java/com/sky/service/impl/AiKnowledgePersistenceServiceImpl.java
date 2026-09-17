package com.sky.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sky.constant.MessageConstant;
import com.sky.entity.AiKnowledge;
import com.sky.entity.AiKnowledgeEmbeddingTask;
import com.sky.enums.AiKnowledgeEmbeddingStatus;
import com.sky.enums.AiKnowledgeEmbeddingTaskStatus;
import com.sky.enums.AiKnowledgeStatus;
import com.sky.exception.BaseException;
import com.sky.mapper.AiKnowledgeEmbeddingTaskMapper;
import com.sky.mapper.AiKnowledgeMapper;
import com.sky.service.AiKnowledgePersistenceService;
import com.sky.service.model.AiKnowledgeRetryOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** 以短事务保存知识版本及其向量任务，不调用外部模型。 */
@Service
@RequiredArgsConstructor
public class AiKnowledgePersistenceServiceImpl implements AiKnowledgePersistenceService {

    private static final int FIRST_VERSION = 1;
    private static final int MAX_RETRY_COUNT = 3;

    private final AiKnowledgeMapper knowledgeMapper;
    private final AiKnowledgeEmbeddingTaskMapper embeddingTaskMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveFirstVersion(AiKnowledge knowledge) {
        validatePendingKnowledge(knowledge);

        Long existingCount = knowledgeMapper.selectCount(
                new LambdaQueryWrapper<AiKnowledge>()
                        .eq(AiKnowledge::getKnowledgeKey, knowledge.getKnowledgeKey()));
        if (existingCount != null && existingCount > 0) {
            throw new BaseException(MessageConstant.AI_KNOWLEDGE_ALREADY_EXISTS);
        }

        try {
            if (knowledgeMapper.insert(knowledge) != 1 || knowledge.getId() == null) {
                throw new BaseException(MessageConstant.AI_KNOWLEDGE_SAVE_FAILED);
            }
            insertEmbeddingTask(knowledge.getId(), knowledge.getCreateTime());
            return knowledge.getId();
        } catch (DuplicateKeyException ex) {
            throw new BaseException(MessageConstant.AI_KNOWLEDGE_ALREADY_EXISTS, ex);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveNextVersion(Long sourceId, AiKnowledge next) {
        validateId(sourceId);
        validatePendingVersionContent(next);

        // 所有同时访问两张表的事务统一按“任务行 -> 知识行”加锁。
        embeddingTaskMapper.selectByKnowledgeIdForUpdate(sourceId);
        AiKnowledge source = knowledgeMapper.selectByIdForUpdate(sourceId);
        if (source == null || source.getStatus() != AiKnowledgeStatus.ENABLED
                || source.getVersionNo() == null || source.getVersionNo() < FIRST_VERSION) {
            throw new BaseException(MessageConstant.AI_KNOWLEDGE_UPDATE_CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now().withNano(0);
        if (knowledgeMapper.disableEnabled(sourceId, now) != 1) {
            throw new BaseException(MessageConstant.AI_KNOWLEDGE_UPDATE_CONFLICT);
        }
        embeddingTaskMapper.markActiveTasksObsoleteByKnowledgeId(sourceId, now);

        next.setId(null);
        next.setKnowledgeKey(source.getKnowledgeKey());
        next.setEmbedding(null);
        next.setEmbeddingModel(null);
        next.setEmbeddingDimensions(null);
        next.setEmbeddingStatus(AiKnowledgeEmbeddingStatus.PENDING);
        next.setVersionNo(source.getVersionNo() + 1);
        next.setStatus(AiKnowledgeStatus.ENABLED);
        next.setCreateTime(now);
        next.setUpdateTime(now);
        try {
            if (knowledgeMapper.insert(next) != 1 || next.getId() == null) {
                throw new BaseException(MessageConstant.AI_KNOWLEDGE_SAVE_FAILED);
            }
            insertEmbeddingTask(next.getId(), now);
            return next.getId();
        } catch (DuplicateKeyException ex) {
            throw new BaseException(MessageConstant.AI_KNOWLEDGE_UPDATE_CONFLICT, ex);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKnowledgeRetryOutcome retryFailedEmbedding(Long knowledgeId) {
        validateId(knowledgeId);

        // 与向量结果回写保持“任务行 -> 知识行”的加锁顺序，降低死锁风险。
        AiKnowledgeEmbeddingTask task =
                embeddingTaskMapper.selectByKnowledgeIdForUpdate(knowledgeId);
        AiKnowledge knowledge = knowledgeMapper.selectByIdForUpdate(knowledgeId);
        if (knowledge == null || knowledge.getStatus() != AiKnowledgeStatus.ENABLED) {
            throw new BaseException(MessageConstant.AI_KNOWLEDGE_UNAVAILABLE);
        }

        if (knowledge.getEmbeddingStatus() == AiKnowledgeEmbeddingStatus.READY) {
            return AiKnowledgeRetryOutcome.READY;
        }
        if (knowledge.getEmbeddingStatus() == AiKnowledgeEmbeddingStatus.PENDING) {
            if (task == null
                    || (task.getStatus() != AiKnowledgeEmbeddingTaskStatus.PENDING
                    && task.getStatus() != AiKnowledgeEmbeddingTaskStatus.PROCESSING)) {
                throw new BaseException(
                        MessageConstant.AI_KNOWLEDGE_EMBEDDING_RETRY_CONFLICT);
            }
            return AiKnowledgeRetryOutcome.PROCESSING;
        }
        if (knowledge.getEmbeddingStatus() != AiKnowledgeEmbeddingStatus.FAILED
                || task == null
                || task.getStatus() != AiKnowledgeEmbeddingTaskStatus.FAILED) {
            throw new BaseException(MessageConstant.AI_KNOWLEDGE_EMBEDDING_RETRY_CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now().withNano(0);
        if (embeddingTaskMapper.resetFailedForManualRetry(
                task.getId(), MAX_RETRY_COUNT, now) != 1
                || knowledgeMapper.markEmbeddingPendingForManualRetry(
                knowledgeId, now) != 1) {
            throw new BaseException(MessageConstant.AI_KNOWLEDGE_EMBEDDING_RETRY_CONFLICT);
        }
        return AiKnowledgeRetryOutcome.QUEUED;
    }

    private void insertEmbeddingTask(Long knowledgeId, LocalDateTime now) {
        AiKnowledgeEmbeddingTask task = AiKnowledgeEmbeddingTask.builder()
                .knowledgeId(knowledgeId)
                .status(AiKnowledgeEmbeddingTaskStatus.PENDING)
                .retryCount(0)
                .maxRetryCount(MAX_RETRY_COUNT)
                .nextRetryTime(now)
                .createTime(now)
                .updateTime(now)
                .build();
        if (embeddingTaskMapper.insert(task) != 1) {
            throw new BaseException(MessageConstant.AI_KNOWLEDGE_SAVE_FAILED);
        }
    }

    private void validatePendingKnowledge(AiKnowledge knowledge) {
        if (knowledge == null
                || !StringUtils.hasText(knowledge.getKnowledgeKey())
                || !StringUtils.hasText(knowledge.getTitle())
                || knowledge.getCategory() == null
                || !StringUtils.hasText(knowledge.getContent())
                || knowledge.getEmbedding() != null
                || knowledge.getEmbeddingModel() != null
                || knowledge.getEmbeddingDimensions() != null
                || knowledge.getEmbeddingStatus() != AiKnowledgeEmbeddingStatus.PENDING
                || !Integer.valueOf(FIRST_VERSION).equals(knowledge.getVersionNo())
                || knowledge.getStatus() != AiKnowledgeStatus.ENABLED
                || knowledge.getCreateTime() == null
                || knowledge.getUpdateTime() == null) {
            throw new BaseException(MessageConstant.AI_KNOWLEDGE_INVALID);
        }
    }

    private void validatePendingVersionContent(AiKnowledge knowledge) {
        if (knowledge == null
                || !StringUtils.hasText(knowledge.getTitle())
                || knowledge.getCategory() == null
                || !StringUtils.hasText(knowledge.getContent())
                || knowledge.getEmbedding() != null
                || knowledge.getEmbeddingModel() != null
                || knowledge.getEmbeddingDimensions() != null
                || knowledge.getEmbeddingStatus() != AiKnowledgeEmbeddingStatus.PENDING) {
            throw new BaseException(MessageConstant.AI_KNOWLEDGE_INVALID);
        }
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new BaseException(MessageConstant.AI_KNOWLEDGE_UNAVAILABLE);
        }
    }
}
