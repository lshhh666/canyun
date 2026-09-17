package com.cloudmeal.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cloudmeal.client.AiEmbeddingClient;
import com.cloudmeal.entity.AiKnowledge;
import com.cloudmeal.entity.AiKnowledgeEmbeddingTask;
import com.cloudmeal.enums.AiKnowledgeEmbeddingStatus;
import com.cloudmeal.enums.AiKnowledgeStatus;
import com.cloudmeal.mapper.AiKnowledgeEmbeddingTaskMapper;
import com.cloudmeal.mapper.AiKnowledgeMapper;
import com.cloudmeal.properties.AiProperties;
import com.cloudmeal.service.AiKnowledgeEmbeddingPersistenceService;
import com.cloudmeal.service.AiKnowledgeEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 在数据库事务之外调用嵌入模型，逐条处理低频知识任务。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiKnowledgeEmbeddingServiceImpl implements AiKnowledgeEmbeddingService {

    private static final int SCAN_BATCH_SIZE = 10;
    private static final int PROCESSING_TIMEOUT_MINUTES = 2;
    private static final int MAX_ERROR_LENGTH = 500;

    private final AiKnowledgeEmbeddingTaskMapper taskMapper;
    private final AiKnowledgeMapper knowledgeMapper;
    private final AiEmbeddingClient embeddingClient;
    private final AiKnowledgeEmbeddingPersistenceService persistenceService;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    @Override
    public void processDueTasks() {
        LocalDateTime scanTime = LocalDateTime.now().withNano(0);
        int recovered = taskMapper.recoverStaleProcessingTasks(
                scanTime.minusMinutes(PROCESSING_TIMEOUT_MINUTES),
                scanTime, scanTime, SCAN_BATCH_SIZE);
        if (recovered > 0) {
            log.warn("恢复超时未完成的知识向量任务，数量={}", recovered);
        }

        List<AiKnowledgeEmbeddingTask> tasks =
                taskMapper.findDuePendingTasks(scanTime, SCAN_BATCH_SIZE);
        for (AiKnowledgeEmbeddingTask task : tasks) {
            try {
                processOne(task);
            } catch (RuntimeException ex) {
                log.error("知识向量任务处理异常，taskId={}，knowledgeId={}",
                        task.getId(), task.getKnowledgeId(), ex);
            }
        }
    }

    private void processOne(AiKnowledgeEmbeddingTask task) {
        LocalDateTime processingTime = LocalDateTime.now().withNano(0);
        if (taskMapper.claimPendingTask(task.getId(), processingTime) != 1) {
            return;
        }

        AiKnowledge knowledge = knowledgeMapper.selectById(task.getKnowledgeId());
        if (!isCurrentPendingVersion(knowledge)) {
            persistenceService.markObsolete(
                    task.getId(), task.getKnowledgeId(), processingTime, processingTime);
            return;
        }

        try {
            List<Double> embedding = embeddingClient.embed(
                    knowledge.getTitle() + "：" + knowledge.getContent());
            validateEmbedding(embedding);
            LocalDateTime now = LocalDateTime.now().withNano(0);
            persistenceService.complete(
                    task.getId(), task.getKnowledgeId(), processingTime,
                    serializeEmbedding(embedding), aiProperties.getEmbeddingModel(),
                    aiProperties.getEmbeddingDimensions(), now);
        } catch (RuntimeException ex) {
            LocalDateTime now = LocalDateTime.now().withNano(0);
            persistenceService.recordFailure(
                    task.getId(), task.getKnowledgeId(), processingTime,
                    normalizeError(ex), nextRetryTime(now, task.getRetryCount()), now);
            log.warn("知识向量生成失败，taskId={}，knowledgeId={}，retryCount={}",
                    task.getId(), task.getKnowledgeId(), task.getRetryCount());
        }
    }

    private boolean isCurrentPendingVersion(AiKnowledge knowledge) {
        return knowledge != null
                && knowledge.getStatus() == AiKnowledgeStatus.ENABLED
                && knowledge.getEmbeddingStatus() == AiKnowledgeEmbeddingStatus.PENDING;
    }

    private void validateEmbedding(List<Double> embedding) {
        int dimensions = aiProperties.getEmbeddingDimensions();
        if (dimensions <= 0 || embedding == null || embedding.size() != dimensions) {
            throw new IllegalStateException("嵌入向量维度不正确");
        }
        boolean nonZero = false;
        for (Double value : embedding) {
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalStateException("嵌入向量包含无效值");
            }
            nonZero = nonZero || value != 0D;
        }
        if (!nonZero) {
            throw new IllegalStateException("嵌入向量范数为零");
        }
    }

    private String serializeEmbedding(List<Double> embedding) {
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("嵌入向量序列化失败", ex);
        }
    }

    private LocalDateTime nextRetryTime(LocalDateTime now, Integer retryCount) {
        int completedRetries = retryCount == null ? 0 : retryCount;
        if (completedRetries == 0) {
            return now.plusMinutes(1);
        }
        if (completedRetries == 1) {
            return now.plusMinutes(5);
        }
        return now.plusMinutes(15);
    }

    private String normalizeError(RuntimeException ex) {
        String message = ex.getClass().getSimpleName() + ": "
                + (ex.getMessage() == null ? "未提供异常信息" : ex.getMessage());
        return message.length() <= MAX_ERROR_LENGTH
                ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
