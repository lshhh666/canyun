package com.sky.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.entity.AiFeedbackRetest;
import com.sky.entity.AiKnowledge;
import com.sky.mapper.AiFeedbackKnowledgeRelationMapper;
import com.sky.mapper.AiFeedbackRetestMapper;
import com.sky.properties.AiProperties;
import com.sky.service.AiFeedbackRetestPersistenceService;
import com.sky.service.AiGroundedAnswerService;
import com.sky.service.model.AiKnowledgeMatch;
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
class AiFeedbackRetestServiceImplTest {

    @Mock
    private AiFeedbackRetestMapper retestMapper;
    @Mock
    private AiFeedbackKnowledgeRelationMapper relationMapper;
    @Mock
    private AiFeedbackRetestPersistenceService persistenceService;
    @Mock
    private AiGroundedAnswerService groundedAnswerService;

    private AiFeedbackRetestServiceImpl service;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        properties.setEmbeddingModel("embedding-test");
        properties.setEmbeddingDimensions(3);
        service = new AiFeedbackRetestServiceImpl(
                retestMapper, relationMapper, persistenceService,
                groundedAnswerService, properties, new ObjectMapper());
    }

    @Test
    void shouldClaimGenerateAndCompleteWithKnowledgeSnapshot() {
        AiFeedbackRetest task = pendingTask(11L, 21L, 0);
        AiKnowledge first = readyKnowledge(31L, "取消范围", "待付款订单可以取消。");
        AiKnowledge second = readyKnowledge(32L, "取消确认", "确认后才执行取消。");
        when(retestMapper.findDuePendingRetests(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(task));
        when(persistenceService.claim(eq(11L), any(LocalDateTime.class)))
                .thenReturn(true);
        when(relationMapper.selectReadyKnowledge(21L, "embedding-test", 3))
                .thenReturn(List.of(first, second));
        when(relationMapper.countByFeedbackId(21L)).thenReturn(2);
        when(groundedAnswerService.generate(eq(task.getQuestion()), any()))
                .thenReturn("待付款订单可以取消，确认后执行。");
        when(persistenceService.complete(
                eq(11L), any(LocalDateTime.class), any(), any(), any(LocalDateTime.class)))
                .thenReturn(true);

        service.processDueTasks();

        verify(retestMapper).recoverStaleProcessingRetests(
                any(LocalDateTime.class), any(LocalDateTime.class),
                any(LocalDateTime.class), anyInt());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiKnowledgeMatch>> matches = ArgumentCaptor.forClass(List.class);
        verify(groundedAnswerService).generate(eq(task.getQuestion()), matches.capture());
        assertThat(matches.getValue()).extracting(AiKnowledgeMatch::getId)
                .containsExactly(31L, 32L);
        verify(persistenceService).complete(
                eq(11L), any(LocalDateTime.class),
                eq("待付款订单可以取消，确认后执行。"), eq("[31,32]"),
                any(LocalDateTime.class));
        verify(persistenceService, never()).recordFailure(
                any(), any(), any(), any(), any());
    }

    @Test
    void shouldScheduleFirstRetryOneMinuteAfterModelFailure() {
        AiFeedbackRetest task = pendingTask(11L, 21L, 0);
        when(retestMapper.findDuePendingRetests(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(task));
        when(persistenceService.claim(eq(11L), any(LocalDateTime.class)))
                .thenReturn(true);
        when(relationMapper.selectReadyKnowledge(21L, "embedding-test", 3))
                .thenReturn(List.of(readyKnowledge(31L, "取消范围", "待付款订单可以取消。")));
        when(relationMapper.countByFeedbackId(21L)).thenReturn(1);
        when(groundedAnswerService.generate(any(), any()))
                .thenThrow(new IllegalStateException("provider timeout"));

        service.processDueTasks();

        ArgumentCaptor<LocalDateTime> retryTime = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> failureTime = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(persistenceService).recordFailure(
                eq(11L), any(LocalDateTime.class),
                eq("IllegalStateException: provider timeout"),
                retryTime.capture(), failureTime.capture());
        assertThat(ChronoUnit.MINUTES.between(
                failureTime.getValue(), retryTime.getValue())).isEqualTo(1);
        verify(persistenceService, never()).complete(
                any(), any(), any(), any(), any());
    }

    @Test
    void shouldRetryWithoutCallingModelWhenAnyLinkedKnowledgeIsNotReady() {
        AiFeedbackRetest task = pendingTask(11L, 21L, 1);
        when(retestMapper.findDuePendingRetests(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(task));
        when(persistenceService.claim(eq(11L), any(LocalDateTime.class)))
                .thenReturn(true);
        when(relationMapper.selectReadyKnowledge(21L, "embedding-test", 3))
                .thenReturn(List.of(readyKnowledge(31L, "取消范围", "待付款订单可以取消。")));
        when(relationMapper.countByFeedbackId(21L)).thenReturn(2);

        service.processDueTasks();

        verify(groundedAnswerService, never()).generate(any(), any());
        verify(persistenceService).recordFailure(
                eq(11L), any(LocalDateTime.class),
                eq("IllegalStateException: 关联知识尚未全部可用"),
                any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void shouldDoNothingWhenAnotherWorkerAlreadyClaimedTask() {
        AiFeedbackRetest task = pendingTask(11L, 21L, 0);
        when(retestMapper.findDuePendingRetests(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(task));
        when(persistenceService.claim(eq(11L), any(LocalDateTime.class)))
                .thenReturn(false);

        service.processDueTasks();

        verify(relationMapper, never()).selectReadyKnowledge(any(), any(), anyInt());
        verify(groundedAnswerService, never()).generate(any(), any());
        verify(persistenceService, never()).complete(any(), any(), any(), any(), any());
        verify(persistenceService, never()).recordFailure(any(), any(), any(), any(), any());
    }

    private AiFeedbackRetest pendingTask(Long id, Long feedbackId, int retryCount) {
        return AiFeedbackRetest.builder()
                .id(id)
                .feedbackId(feedbackId)
                .question("待付款订单能取消吗？")
                .retryCount(retryCount)
                .maxRetryCount(3)
                .nextRetryTime(LocalDateTime.now().minusMinutes(1))
                .build();
    }

    private AiKnowledge readyKnowledge(Long id, String title, String content) {
        return AiKnowledge.builder()
                .id(id)
                .title(title)
                .content(content)
                .build();
    }
}
