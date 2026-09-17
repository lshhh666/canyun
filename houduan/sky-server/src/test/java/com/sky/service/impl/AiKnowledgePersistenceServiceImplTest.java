package com.sky.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sky.constant.MessageConstant;
import com.sky.entity.AiKnowledge;
import com.sky.entity.AiKnowledgeEmbeddingTask;
import com.sky.enums.AiKnowledgeCategory;
import com.sky.enums.AiKnowledgeEmbeddingStatus;
import com.sky.enums.AiKnowledgeEmbeddingTaskStatus;
import com.sky.enums.AiKnowledgeStatus;
import com.sky.exception.BaseException;
import com.sky.mapper.AiKnowledgeEmbeddingTaskMapper;
import com.sky.mapper.AiKnowledgeMapper;
import com.sky.service.model.AiKnowledgeRetryOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiKnowledgePersistenceServiceImplTest {

    @Mock
    private AiKnowledgeMapper knowledgeMapper;
    @Mock
    private AiKnowledgeEmbeddingTaskMapper taskMapper;

    private AiKnowledgePersistenceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiKnowledgePersistenceServiceImpl(knowledgeMapper, taskMapper);
    }

    @Test
    void shouldAtomicallySavePendingFirstVersionAndTask() {
        AiKnowledge knowledge = pendingKnowledge();
        when(knowledgeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(knowledgeMapper.insert(knowledge)).thenAnswer(invocation -> {
            knowledge.setId(88L);
            return 1;
        });
        when(taskMapper.insert(any(AiKnowledgeEmbeddingTask.class))).thenReturn(1);

        Long result = service.saveFirstVersion(knowledge);

        ArgumentCaptor<AiKnowledgeEmbeddingTask> taskCaptor =
                ArgumentCaptor.forClass(AiKnowledgeEmbeddingTask.class);
        InOrder order = inOrder(knowledgeMapper, taskMapper);
        order.verify(knowledgeMapper).insert(knowledge);
        order.verify(taskMapper).insert(taskCaptor.capture());
        AiKnowledgeEmbeddingTask task = taskCaptor.getValue();
        assertThat(result).isEqualTo(88L);
        assertThat(task.getKnowledgeId()).isEqualTo(88L);
        assertThat(task.getStatus()).isEqualTo(AiKnowledgeEmbeddingTaskStatus.PENDING);
        assertThat(task.getRetryCount()).isZero();
        assertThat(task.getMaxRetryCount()).isEqualTo(3);
        assertThat(task.getNextRetryTime()).isEqualTo(knowledge.getCreateTime());
    }

    @Test
    void shouldRejectExistingKnowledgeKey() {
        AiKnowledge knowledge = pendingKnowledge();
        when(knowledgeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.saveFirstVersion(knowledge))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_KNOWLEDGE_ALREADY_EXISTS);

        verify(knowledgeMapper, never()).insert(any(AiKnowledge.class));
        verify(taskMapper, never()).insert(any(AiKnowledgeEmbeddingTask.class));
    }

    @Test
    void shouldRejectKnowledgeThatAlreadyContainsEmbedding() {
        AiKnowledge knowledge = pendingKnowledge();
        knowledge.setEmbedding("[0.25,0.0]");

        assertThatThrownBy(() -> service.saveFirstVersion(knowledge))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_KNOWLEDGE_INVALID);

        verify(knowledgeMapper, never()).selectCount(any(LambdaQueryWrapper.class));
        verify(knowledgeMapper, never()).insert(any(AiKnowledge.class));
    }

    @Test
    void shouldDisableOldVersionObsoleteOldTaskAndCreatePendingNextVersion() {
        AiKnowledge source = readyKnowledge();
        source.setId(7L);
        source.setVersionNo(3);
        AiKnowledge next = nextVersionDraft();
        when(knowledgeMapper.selectByIdForUpdate(7L)).thenReturn(source);
        when(knowledgeMapper.disableEnabled(eq(7L), any(LocalDateTime.class))).thenReturn(1);
        when(taskMapper.markActiveTasksObsoleteByKnowledgeId(eq(7L), any(LocalDateTime.class)))
                .thenReturn(1);
        when(knowledgeMapper.insert(next)).thenAnswer(invocation -> {
            next.setId(8L);
            return 1;
        });
        when(taskMapper.insert(any(AiKnowledgeEmbeddingTask.class))).thenReturn(1);

        Long result = service.saveNextVersion(7L, next);

        InOrder order = inOrder(knowledgeMapper, taskMapper);
        order.verify(taskMapper).selectByKnowledgeIdForUpdate(7L);
        order.verify(knowledgeMapper).selectByIdForUpdate(7L);
        order.verify(knowledgeMapper).disableEnabled(eq(7L), any(LocalDateTime.class));
        order.verify(taskMapper).markActiveTasksObsoleteByKnowledgeId(eq(7L), any(LocalDateTime.class));
        order.verify(knowledgeMapper).insert(next);
        order.verify(taskMapper).insert(any(AiKnowledgeEmbeddingTask.class));
        assertThat(result).isEqualTo(8L);
        assertThat(next.getKnowledgeKey()).isEqualTo("SHOP_HOURS");
        assertThat(next.getVersionNo()).isEqualTo(4);
        assertThat(next.getStatus()).isEqualTo(AiKnowledgeStatus.ENABLED);
        assertThat(next.getEmbeddingStatus()).isEqualTo(AiKnowledgeEmbeddingStatus.PENDING);
        assertThat(next.getEmbedding()).isNull();
    }

    @Test
    void shouldRejectUpdateWhenSourceIsNoLongerEnabled() {
        AiKnowledge source = readyKnowledge();
        source.setStatus(AiKnowledgeStatus.DISABLED);
        when(knowledgeMapper.selectByIdForUpdate(7L)).thenReturn(source);

        assertThatThrownBy(() -> service.saveNextVersion(7L, nextVersionDraft()))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_KNOWLEDGE_UPDATE_CONFLICT);

        verify(knowledgeMapper, never()).disableEnabled(any(Long.class), any(LocalDateTime.class));
        verify(knowledgeMapper, never()).insert(any(AiKnowledge.class));
        verify(taskMapper, never()).insert(any(AiKnowledgeEmbeddingTask.class));
    }

    @Test
    void shouldResetFailedKnowledgeAndTaskForManualRetry() {
        AiKnowledge knowledge = pendingKnowledge();
        knowledge.setId(88L);
        knowledge.setEmbeddingStatus(AiKnowledgeEmbeddingStatus.FAILED);
        AiKnowledgeEmbeddingTask task = AiKnowledgeEmbeddingTask.builder()
                .id(99L)
                .knowledgeId(88L)
                .status(AiKnowledgeEmbeddingTaskStatus.FAILED)
                .retryCount(3)
                .maxRetryCount(3)
                .build();
        when(taskMapper.selectByKnowledgeIdForUpdate(88L)).thenReturn(task);
        when(knowledgeMapper.selectByIdForUpdate(88L)).thenReturn(knowledge);
        when(taskMapper.resetFailedForManualRetry(
                eq(99L), eq(3), any(LocalDateTime.class))).thenReturn(1);
        when(knowledgeMapper.markEmbeddingPendingForManualRetry(
                eq(88L), any(LocalDateTime.class))).thenReturn(1);

        AiKnowledgeRetryOutcome outcome = service.retryFailedEmbedding(88L);

        assertThat(outcome).isEqualTo(AiKnowledgeRetryOutcome.QUEUED);
        InOrder order = inOrder(taskMapper, knowledgeMapper);
        order.verify(taskMapper).selectByKnowledgeIdForUpdate(88L);
        order.verify(knowledgeMapper).selectByIdForUpdate(88L);
        order.verify(taskMapper).resetFailedForManualRetry(
                eq(99L), eq(3), any(LocalDateTime.class));
        order.verify(knowledgeMapper).markEmbeddingPendingForManualRetry(
                eq(88L), any(LocalDateTime.class));
    }

    @Test
    void shouldReturnProcessingWithoutResettingAnActiveTask() {
        AiKnowledge knowledge = pendingKnowledge();
        knowledge.setId(88L);
        AiKnowledgeEmbeddingTask task = AiKnowledgeEmbeddingTask.builder()
                .id(99L)
                .knowledgeId(88L)
                .status(AiKnowledgeEmbeddingTaskStatus.PROCESSING)
                .build();
        when(taskMapper.selectByKnowledgeIdForUpdate(88L)).thenReturn(task);
        when(knowledgeMapper.selectByIdForUpdate(88L)).thenReturn(knowledge);

        assertThat(service.retryFailedEmbedding(88L))
                .isEqualTo(AiKnowledgeRetryOutcome.PROCESSING);

        verify(taskMapper, never()).resetFailedForManualRetry(
                any(Long.class), any(Integer.class), any(LocalDateTime.class));
        verify(knowledgeMapper, never()).markEmbeddingPendingForManualRetry(
                any(Long.class), any(LocalDateTime.class));
    }

    @Test
    void shouldReturnReadyWithoutCreatingAnotherTask() {
        AiKnowledge knowledge = readyKnowledge();
        knowledge.setId(88L);
        when(knowledgeMapper.selectByIdForUpdate(88L)).thenReturn(knowledge);

        assertThat(service.retryFailedEmbedding(88L))
                .isEqualTo(AiKnowledgeRetryOutcome.READY);

        verify(taskMapper, never()).resetFailedForManualRetry(
                any(Long.class), any(Integer.class), any(LocalDateTime.class));
    }

    @Test
    void shouldRejectFailedKnowledgeWhenTaskStateIsInconsistent() {
        AiKnowledge knowledge = pendingKnowledge();
        knowledge.setEmbeddingStatus(AiKnowledgeEmbeddingStatus.FAILED);
        AiKnowledgeEmbeddingTask task = AiKnowledgeEmbeddingTask.builder()
                .id(99L)
                .knowledgeId(88L)
                .status(AiKnowledgeEmbeddingTaskStatus.PENDING)
                .build();
        when(taskMapper.selectByKnowledgeIdForUpdate(88L)).thenReturn(task);
        when(knowledgeMapper.selectByIdForUpdate(88L)).thenReturn(knowledge);

        assertThatThrownBy(() -> service.retryFailedEmbedding(88L))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_KNOWLEDGE_EMBEDDING_RETRY_CONFLICT);
    }

    private AiKnowledge pendingKnowledge() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        return AiKnowledge.builder()
                .knowledgeKey("SHOP_HOURS")
                .title("营业时间")
                .category(AiKnowledgeCategory.SHOP)
                .content("餐云每天09:00至21:00营业。")
                .embeddingStatus(AiKnowledgeEmbeddingStatus.PENDING)
                .versionNo(1)
                .status(AiKnowledgeStatus.ENABLED)
                .createTime(now)
                .updateTime(now)
                .build();
    }

    private AiKnowledge readyKnowledge() {
        AiKnowledge knowledge = pendingKnowledge();
        knowledge.setEmbedding("[0.25,0.0]");
        knowledge.setEmbeddingModel("embedding-3");
        knowledge.setEmbeddingDimensions(2);
        knowledge.setEmbeddingStatus(AiKnowledgeEmbeddingStatus.READY);
        return knowledge;
    }

    private AiKnowledge nextVersionDraft() {
        return AiKnowledge.builder()
                .title("营业时间")
                .category(AiKnowledgeCategory.SHOP)
                .content("餐云每天09:00至22:00营业。")
                .embeddingStatus(AiKnowledgeEmbeddingStatus.PENDING)
                .build();
    }
}
