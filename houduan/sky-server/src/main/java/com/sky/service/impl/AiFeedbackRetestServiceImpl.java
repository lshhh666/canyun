package com.sky.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.entity.AiFeedbackRetest;
import com.sky.entity.AiKnowledge;
import com.sky.mapper.AiFeedbackKnowledgeRelationMapper;
import com.sky.mapper.AiFeedbackRetestMapper;
import com.sky.properties.AiProperties;
import com.sky.service.AiFeedbackRetestPersistenceService;
import com.sky.service.AiFeedbackRetestService;
import com.sky.service.AiGroundedAnswerService;
import com.sky.service.model.AiKnowledgeMatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** 抢占和结果落库使用短事务，模型调用明确位于事务之外。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiFeedbackRetestServiceImpl implements AiFeedbackRetestService {

    private static final int SCAN_BATCH_SIZE = 10;
    private static final int PROCESSING_TIMEOUT_MINUTES = 2;
    private static final int MAX_ERROR_LENGTH = 500;

    private final AiFeedbackRetestMapper retestMapper;
    private final AiFeedbackKnowledgeRelationMapper relationMapper;
    private final AiFeedbackRetestPersistenceService persistenceService;
    private final AiGroundedAnswerService groundedAnswerService;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    @Override
    public void processDueTasks() {
        LocalDateTime scanTime = LocalDateTime.now().withNano(0);
        int recovered = retestMapper.recoverStaleProcessingRetests(
                scanTime.minusMinutes(PROCESSING_TIMEOUT_MINUTES),
                scanTime, scanTime, SCAN_BATCH_SIZE);
        if (recovered > 0) {
            log.warn("恢复超时未完成的AI反馈复测任务，数量={}", recovered);
        }

        List<AiFeedbackRetest> tasks =
                retestMapper.findDuePendingRetests(scanTime, SCAN_BATCH_SIZE);
        for (AiFeedbackRetest task : tasks == null
                ? Collections.<AiFeedbackRetest>emptyList() : tasks) {
            try {
                processOne(task);
            } catch (RuntimeException ex) {
                log.error("AI反馈复测任务处理异常，retestId={}，feedbackId={}",
                        task == null ? null : task.getId(),
                        task == null ? null : task.getFeedbackId(), ex);
            }
        }
    }

    private void processOne(AiFeedbackRetest task) {
        if (task == null || task.getId() == null || task.getFeedbackId() == null
                || !StringUtils.hasText(task.getQuestion())) {
            throw new IllegalStateException("复测任务数据不完整");
        }
        LocalDateTime processingTime = LocalDateTime.now().withNano(0);
        // 独立短事务在返回前已经提交；从这里开始不再持有抢占事务的连接或行锁。
        if (!persistenceService.claim(task.getId(), processingTime)) {
            return;
        }

        try {
            List<AiKnowledge> knowledge = relationMapper.selectReadyKnowledge(
                    task.getFeedbackId(), aiProperties.getEmbeddingModel(),
                    aiProperties.getEmbeddingDimensions());
            int relationCount = relationMapper.countByFeedbackId(task.getFeedbackId());
            if (knowledge == null || knowledge.isEmpty()
                    || knowledge.size() != relationCount) {
                throw new IllegalStateException("关联知识尚未全部可用");
            }
            List<AiKnowledgeMatch> matches = knowledge.stream()
                    .map(item -> AiKnowledgeMatch.builder()
                            .id(item.getId())
                            .title(item.getTitle())
                            .content(item.getContent())
                            .category(item.getCategory())
                            .score(1D)
                            .build())
                    .collect(Collectors.toList());
            String answer = groundedAnswerService.generate(task.getQuestion(), matches);
            String usedKnowledgeIds = serializeKnowledgeIds(knowledge);
            LocalDateTime now = LocalDateTime.now().withNano(0);
            if (!persistenceService.complete(
                    task.getId(), processingTime, answer, usedKnowledgeIds, now)) {
                log.warn("忽略已失效的AI反馈复测结果，retestId={}", task.getId());
            }
        } catch (RuntimeException ex) {
            LocalDateTime now = LocalDateTime.now().withNano(0);
            persistenceService.recordFailure(
                    task.getId(), processingTime, normalizeError(ex),
                    nextRetryTime(now, task.getRetryCount()), now);
            log.warn("AI反馈复测失败，retestId={}，retryCount={}",
                    task.getId(), task.getRetryCount());
        }
    }

    private String serializeKnowledgeIds(List<AiKnowledge> knowledge) {
        try {
            return objectMapper.writeValueAsString(knowledge.stream()
                    .map(AiKnowledge::getId)
                    .collect(Collectors.toList()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("知识版本快照序列化失败", ex);
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
