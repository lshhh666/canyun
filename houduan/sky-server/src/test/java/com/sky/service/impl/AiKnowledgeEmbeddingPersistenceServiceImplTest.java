package com.sky.service.impl;

import com.sky.entity.AiKnowledge;
import com.sky.entity.AiKnowledgeEmbeddingTask;
import com.sky.enums.AiKnowledgeEmbeddingStatus;
import com.sky.enums.AiKnowledgeEmbeddingTaskStatus;
import com.sky.enums.AiKnowledgeStatus;
import com.sky.mapper.AiKnowledgeEmbeddingTaskMapper;
import com.sky.mapper.AiKnowledgeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiKnowledgeEmbeddingPersistenceServiceImplTest {

    @Mock
    private AiKnowledgeEmbeddingTaskMapper taskMapper;
    @Mock
    private AiKnowledgeMapper knowledgeMapper;

    private AiKnowledgeEmbeddingPersistenceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiKnowledgeEmbeddingPersistenceServiceImpl(taskMapper, knowledgeMapper);
    }

    @Test
    void shouldCompleteOnlyCurrentProcessingClaim() {
        LocalDateTime processingTime = LocalDateTime.of(2026, 9, 12, 20, 0);
        LocalDateTime now = processingTime.plusSeconds(3);
        when(taskMapper.selectByIdForUpdate(11L))
                .thenReturn(processingTask(processingTime, 0, 3));
        when(knowledgeMapper.selectByIdForUpdate(88L)).thenReturn(pendingKnowledge());
        when(knowledgeMapper.markEmbeddingReady(
                88L, "[0.25,0.0]", "embedding-3", 2, now)).thenReturn(1);
        when(taskMapper.markSucceeded(11L, processingTime, now)).thenReturn(1);

        boolean completed = service.complete(
                11L, 88L, processingTime, "[0.25,0.0]", "embedding-3", 2, now);

        assertThat(completed).isTrue();
        verify(knowledgeMapper).markEmbeddingReady(
                88L, "[0.25,0.0]", "embedding-3", 2, now);
        verify(taskMapper).markSucceeded(11L, processingTime, now);
    }

    @Test
    void shouldIgnoreLateResultFromExpiredClaim() {
        LocalDateTime currentClaim = LocalDateTime.of(2026, 9, 12, 20, 2);
        LocalDateTime oldClaim = currentClaim.minusMinutes(2);
        when(taskMapper.selectByIdForUpdate(11L))
                .thenReturn(processingTask(currentClaim, 0, 3));

        boolean completed = service.complete(
                11L, 88L, oldClaim, "[0.25,0.0]", "embedding-3", 2,
                currentClaim.plusSeconds(1));

        assertThat(completed).isFalse();
        verifyNoInteractions(knowledgeMapper);
        verify(taskMapper, never()).markSucceeded(any(), any(), any());
    }

    @Test
    void shouldKeepKnowledgePendingWhenAutomaticRetryRemains() {
        LocalDateTime processingTime = LocalDateTime.of(2026, 9, 12, 20, 0);
        LocalDateTime now = processingTime.plusSeconds(3);
        LocalDateTime nextRetryTime = now.plusMinutes(15);
        when(taskMapper.selectByIdForUpdate(11L))
                .thenReturn(processingTask(processingTime, 2, 3));
        when(knowledgeMapper.selectByIdForUpdate(88L)).thenReturn(pendingKnowledge());
        when(taskMapper.markRetryFailure(
                11L, processingTime, "timeout", nextRetryTime, now)).thenReturn(1);

        service.recordFailure(11L, 88L, processingTime, "timeout", nextRetryTime, now);

        verify(taskMapper).markRetryFailure(11L, processingTime, "timeout", nextRetryTime, now);
        verify(knowledgeMapper, never()).markEmbeddingFailed(any(), any());
    }

    @Test
    void shouldMarkKnowledgeFailedAfterThirdAutomaticRetryFails() {
        LocalDateTime processingTime = LocalDateTime.of(2026, 9, 12, 20, 0);
        LocalDateTime now = processingTime.plusSeconds(3);
        when(taskMapper.selectByIdForUpdate(11L))
                .thenReturn(processingTask(processingTime, 3, 3));
        when(knowledgeMapper.selectByIdForUpdate(88L)).thenReturn(pendingKnowledge());
        when(taskMapper.markRetryFailure(
                eq(11L), eq(processingTime), eq("timeout"), any(LocalDateTime.class), eq(now)))
                .thenReturn(1);
        when(knowledgeMapper.markEmbeddingFailed(88L, now)).thenReturn(1);

        service.recordFailure(
                11L, 88L, processingTime, "timeout", now.plusMinutes(15), now);

        verify(knowledgeMapper).markEmbeddingFailed(88L, now);
    }

    @Test
    void shouldObsoleteClaimIfKnowledgeWasDisabledWhileModelWasRunning() {
        LocalDateTime processingTime = LocalDateTime.of(2026, 9, 12, 20, 0);
        LocalDateTime now = processingTime.plusSeconds(3);
        AiKnowledge disabled = pendingKnowledge();
        disabled.setStatus(AiKnowledgeStatus.DISABLED);
        when(taskMapper.selectByIdForUpdate(11L))
                .thenReturn(processingTask(processingTime, 0, 3));
        when(knowledgeMapper.selectByIdForUpdate(88L)).thenReturn(disabled);
        when(taskMapper.markObsolete(11L, now)).thenReturn(1);

        boolean completed = service.complete(
                11L, 88L, processingTime, "[0.25,0.0]", "embedding-3", 2, now);

        assertThat(completed).isFalse();
        verify(taskMapper).markObsolete(11L, now);
        verify(knowledgeMapper, never()).markEmbeddingReady(
                any(), anyString(), anyString(), anyInt(), any());
    }

    private AiKnowledgeEmbeddingTask processingTask(
            LocalDateTime processingTime, int retryCount, int maxRetryCount) {
        return AiKnowledgeEmbeddingTask.builder()
                .id(11L)
                .knowledgeId(88L)
                .status(AiKnowledgeEmbeddingTaskStatus.PROCESSING)
                .retryCount(retryCount)
                .maxRetryCount(maxRetryCount)
                .processingTime(processingTime)
                .build();
    }

    private AiKnowledge pendingKnowledge() {
        return AiKnowledge.builder()
                .id(88L)
                .status(AiKnowledgeStatus.ENABLED)
                .embeddingStatus(AiKnowledgeEmbeddingStatus.PENDING)
                .build();
    }
}
