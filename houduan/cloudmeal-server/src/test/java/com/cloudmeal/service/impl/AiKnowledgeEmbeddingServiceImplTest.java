package com.cloudmeal.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cloudmeal.client.AiEmbeddingClient;
import com.cloudmeal.entity.AiKnowledge;
import com.cloudmeal.entity.AiKnowledgeEmbeddingTask;
import com.cloudmeal.enums.AiKnowledgeCategory;
import com.cloudmeal.enums.AiKnowledgeEmbeddingStatus;
import com.cloudmeal.enums.AiKnowledgeEmbeddingTaskStatus;
import com.cloudmeal.enums.AiKnowledgeStatus;
import com.cloudmeal.mapper.AiKnowledgeEmbeddingTaskMapper;
import com.cloudmeal.mapper.AiKnowledgeMapper;
import com.cloudmeal.properties.AiProperties;
import com.cloudmeal.service.AiKnowledgeEmbeddingPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiKnowledgeEmbeddingServiceImplTest {

    @Mock
    private AiKnowledgeEmbeddingTaskMapper taskMapper;
    @Mock
    private AiKnowledgeMapper knowledgeMapper;
    @Mock
    private AiEmbeddingClient embeddingClient;
    @Mock
    private AiKnowledgeEmbeddingPersistenceService persistenceService;

    private AiKnowledgeEmbeddingServiceImpl service;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        properties.setEmbeddingModel("embedding-3");
        properties.setEmbeddingDimensions(2);
        service = new AiKnowledgeEmbeddingServiceImpl(
                taskMapper, knowledgeMapper, embeddingClient,
                persistenceService, properties, new ObjectMapper());
    }

    @Test
    void shouldClaimThenGenerateAndCompleteOutsideClaimTransaction() {
        AiKnowledgeEmbeddingTask task = pendingTask(11L, 88L, 0);
        when(taskMapper.findDuePendingTasks(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(task));
        when(taskMapper.claimPendingTask(eq(11L), any(LocalDateTime.class))).thenReturn(1);
        when(knowledgeMapper.selectById(88L)).thenReturn(pendingKnowledge(88L));
        when(embeddingClient.embed("营业时间：餐云每天09:00至21:00营业。"))
                .thenReturn(List.of(0.25D, 0D));

        service.processDueTasks();

        verify(taskMapper).recoverStaleProcessingTasks(
                any(LocalDateTime.class), any(LocalDateTime.class),
                any(LocalDateTime.class), anyInt());
        verify(persistenceService).complete(
                eq(11L), eq(88L), any(LocalDateTime.class),
                eq("[0.25,0.0]"), eq("embedding-3"), eq(2), any(LocalDateTime.class));
        verify(persistenceService, never()).recordFailure(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldScheduleFirstRetryOneMinuteAfterFailure() {
        AiKnowledgeEmbeddingTask task = pendingTask(11L, 88L, 0);
        when(taskMapper.findDuePendingTasks(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(task));
        when(taskMapper.claimPendingTask(eq(11L), any(LocalDateTime.class))).thenReturn(1);
        when(knowledgeMapper.selectById(88L)).thenReturn(pendingKnowledge(88L));
        when(embeddingClient.embed(any(String.class)))
                .thenThrow(new IllegalStateException("provider timeout"));

        service.processDueTasks();

        ArgumentCaptor<LocalDateTime> retryTime = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> failureTime = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(persistenceService).recordFailure(
                eq(11L), eq(88L), any(LocalDateTime.class),
                eq("IllegalStateException: provider timeout"), retryTime.capture(), failureTime.capture());
        assertThat(ChronoUnit.MINUTES.between(failureTime.getValue(), retryTime.getValue()))
                .isEqualTo(1);
        verify(persistenceService, never()).complete(
                any(), any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void shouldObsoleteTaskWhenKnowledgeIsNoLongerCurrent() {
        AiKnowledgeEmbeddingTask task = pendingTask(11L, 88L, 0);
        AiKnowledge disabled = pendingKnowledge(88L);
        disabled.setStatus(AiKnowledgeStatus.DISABLED);
        when(taskMapper.findDuePendingTasks(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(task));
        when(taskMapper.claimPendingTask(eq(11L), any(LocalDateTime.class))).thenReturn(1);
        when(knowledgeMapper.selectById(88L)).thenReturn(disabled);

        service.processDueTasks();

        verify(persistenceService).markObsolete(
                eq(11L), eq(88L), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(embeddingClient, never()).embed(any(String.class));
    }

    @Test
    void shouldDoNothingWhenAnotherWorkerAlreadyClaimedTask() {
        AiKnowledgeEmbeddingTask task = pendingTask(11L, 88L, 0);
        when(taskMapper.findDuePendingTasks(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(task));
        when(taskMapper.claimPendingTask(eq(11L), any(LocalDateTime.class))).thenReturn(0);

        service.processDueTasks();

        verify(knowledgeMapper, never()).selectById(any());
        verify(embeddingClient, never()).embed(any(String.class));
    }

    private AiKnowledgeEmbeddingTask pendingTask(Long id, Long knowledgeId, int retryCount) {
        return AiKnowledgeEmbeddingTask.builder()
                .id(id)
                .knowledgeId(knowledgeId)
                .status(AiKnowledgeEmbeddingTaskStatus.PENDING)
                .retryCount(retryCount)
                .maxRetryCount(3)
                .nextRetryTime(LocalDateTime.now().minusMinutes(1))
                .build();
    }

    private AiKnowledge pendingKnowledge(Long id) {
        return AiKnowledge.builder()
                .id(id)
                .knowledgeKey("SHOP_HOURS")
                .title("营业时间")
                .category(AiKnowledgeCategory.SHOP)
                .content("餐云每天09:00至21:00营业。")
                .embeddingStatus(AiKnowledgeEmbeddingStatus.PENDING)
                .versionNo(1)
                .status(AiKnowledgeStatus.ENABLED)
                .build();
    }
}
