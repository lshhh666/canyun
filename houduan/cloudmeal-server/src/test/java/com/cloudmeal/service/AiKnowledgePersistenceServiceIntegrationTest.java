package com.cloudmeal.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloudmeal.dto.AiKnowledgePageQueryDTO;
import com.cloudmeal.entity.AiKnowledge;
import com.cloudmeal.entity.AiKnowledgeEmbeddingTask;
import com.cloudmeal.enums.AiKnowledgeCategory;
import com.cloudmeal.enums.AiKnowledgeEmbeddingStatus;
import com.cloudmeal.enums.AiKnowledgeEmbeddingTaskStatus;
import com.cloudmeal.enums.AiKnowledgeStatus;
import com.cloudmeal.mapper.AiKnowledgeEmbeddingTaskMapper;
import com.cloudmeal.mapper.AiKnowledgeMapper;
import com.cloudmeal.result.PageResult;
import com.cloudmeal.vo.AiKnowledgePageVO;
import com.cloudmeal.vo.AiKnowledgeSaveVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 使用真实MySQL验证知识版本切换的原子性。
 */
@SpringBootTest(properties = {
        "cloudmeal.websocket.enabled=false",
        "cloudmeal.ai.embedding-task-initial-delay-ms=3600000"
})
class AiKnowledgePersistenceServiceIntegrationTest {

    @Autowired
    private AiKnowledgePersistenceService persistenceService;

    @Autowired
    private AiKnowledgeMapper knowledgeMapper;

    @Autowired
    private AiKnowledgeEmbeddingTaskMapper taskMapper;

    @Autowired
    private AiKnowledgeService knowledgeService;

    @Test
    void shouldPageCurrentKnowledgeByCategoryAndEmbeddingStatus() {
        String knowledgeKey = uniqueKey();
        try {
            persistenceService.saveFirstVersion(pendingFirstVersion(knowledgeKey));
            AiKnowledgePageQueryDTO query = new AiKnowledgePageQueryDTO();
            query.setPage(1);
            query.setPageSize(10);
            query.setKeyword(knowledgeKey);
            query.setCategory(AiKnowledgeCategory.SHOP);
            query.setEmbeddingStatus(AiKnowledgeEmbeddingStatus.PENDING);

            PageResult result = knowledgeService.pageCurrent(query);

            assertEquals(1L, result.getTotal());
            AiKnowledgePageVO record = (AiKnowledgePageVO) result.getRecords().get(0);
            assertEquals(knowledgeKey, record.getKnowledgeKey());
            assertEquals("待生成", record.getEmbeddingStatusDesc());
        } finally {
            deleteByKnowledgeKey(knowledgeKey);
        }
    }

    @Test
    void shouldAtomicallySaveFirstVersionAndPendingTask() {
        String knowledgeKey = uniqueKey();
        AiKnowledge pending = pendingFirstVersion(knowledgeKey);
        try {
            Long knowledgeId = persistenceService.saveFirstVersion(pending);

            AiKnowledge saved = knowledgeMapper.selectById(knowledgeId);
            AiKnowledgeEmbeddingTask task = taskMapper.selectOne(
                    Wrappers.<AiKnowledgeEmbeddingTask>lambdaQuery()
                            .eq(AiKnowledgeEmbeddingTask::getKnowledgeId, knowledgeId));
            assertEquals(AiKnowledgeEmbeddingStatus.PENDING, saved.getEmbeddingStatus());
            assertEquals(null, saved.getEmbedding());
            assertEquals(AiKnowledgeEmbeddingTaskStatus.PENDING, task.getStatus());
            assertEquals(0, task.getRetryCount());
            assertEquals(3, task.getMaxRetryCount());
        } finally {
            deleteByKnowledgeKey(knowledgeKey);
        }
    }

    @Test
    void shouldAtomicallyDisableOldVersionAndInsertNewVersion() {
        String knowledgeKey = uniqueKey();
        AiKnowledge source = insertSource(knowledgeKey);
        try {
            Long nextId = persistenceService.saveNextVersion(
                    source.getId(), nextVersionDraft());

            AiKnowledge oldVersion = knowledgeMapper.selectById(source.getId());
            AiKnowledge newVersion = knowledgeMapper.selectById(nextId);
            assertEquals(AiKnowledgeStatus.DISABLED, oldVersion.getStatus());
            assertEquals(AiKnowledgeStatus.ENABLED, newVersion.getStatus());
            assertEquals(AiKnowledgeEmbeddingStatus.PENDING, newVersion.getEmbeddingStatus());
            assertEquals(null, newVersion.getEmbedding());
            assertEquals(knowledgeKey, newVersion.getKnowledgeKey());
            assertEquals(2, newVersion.getVersionNo());

            AiKnowledgeEmbeddingTask task = taskMapper.selectOne(
                    Wrappers.<AiKnowledgeEmbeddingTask>lambdaQuery()
                            .eq(AiKnowledgeEmbeddingTask::getKnowledgeId, nextId));
            assertEquals(AiKnowledgeEmbeddingTaskStatus.PENDING, task.getStatus());
        } finally {
            deleteByKnowledgeKey(knowledgeKey);
        }
    }

    @Test
    void shouldRollbackOldVersionDisableWhenNewInsertFails() {
        String knowledgeKey = uniqueKey();
        AiKnowledge source = insertSource(knowledgeKey);
        insertExistingSecondVersion(knowledgeKey);
        try {
            assertThrows(RuntimeException.class,
                    () -> persistenceService.saveNextVersion(
                            source.getId(), nextVersionDraft()));

            List<AiKnowledge> versions = knowledgeMapper.selectList(
                    Wrappers.<AiKnowledge>lambdaQuery()
                            .eq(AiKnowledge::getKnowledgeKey, knowledgeKey));
            assertEquals(2, versions.size());
            AiKnowledge reloadedSource = knowledgeMapper.selectById(source.getId());
            assertEquals(AiKnowledgeStatus.ENABLED, reloadedSource.getStatus());
            assertEquals(0L, taskMapper.selectCount(
                    Wrappers.<AiKnowledgeEmbeddingTask>lambdaQuery()
                            .inSql(AiKnowledgeEmbeddingTask::getKnowledgeId,
                                    "SELECT id FROM ai_knowledge WHERE knowledge_key = '"
                                            + knowledgeKey + "'")));
        } finally {
            deleteByKnowledgeKey(knowledgeKey);
        }
    }

    @Test
    void shouldResetFailedTaskAndHandleDuplicateManualRetryIdempotently() {
        String knowledgeKey = uniqueKey();
        try {
            Long knowledgeId = persistenceService.saveFirstVersion(
                    pendingFirstVersion(knowledgeKey));
            AiKnowledge knowledge = knowledgeMapper.selectById(knowledgeId);
            knowledge.setEmbeddingStatus(AiKnowledgeEmbeddingStatus.FAILED);
            assertEquals(1, knowledgeMapper.updateById(knowledge));

            AiKnowledgeEmbeddingTask task = taskMapper.selectOne(
                    Wrappers.<AiKnowledgeEmbeddingTask>lambdaQuery()
                            .eq(AiKnowledgeEmbeddingTask::getKnowledgeId, knowledgeId));
            task.setStatus(AiKnowledgeEmbeddingTaskStatus.FAILED);
            task.setRetryCount(3);
            task.setLastError("test failure");
            assertEquals(1, taskMapper.updateById(task));

            AiKnowledgeSaveVO first = knowledgeService.retryEmbedding(knowledgeId);
            AiKnowledge reloadedKnowledge = knowledgeMapper.selectById(knowledgeId);
            AiKnowledgeEmbeddingTask reloadedTask = taskMapper.selectById(task.getId());
            assertEquals(AiKnowledgeEmbeddingStatus.PENDING,
                    first.getEmbeddingStatus());
            assertEquals("已提交，向量同步中", first.getMessage());
            assertEquals(AiKnowledgeEmbeddingStatus.PENDING,
                    reloadedKnowledge.getEmbeddingStatus());
            assertEquals(AiKnowledgeEmbeddingTaskStatus.PENDING,
                    reloadedTask.getStatus());
            assertEquals(0, reloadedTask.getRetryCount());
            assertEquals(null, reloadedTask.getLastError());

            AiKnowledgeSaveVO duplicate = knowledgeService.retryEmbedding(knowledgeId);
            assertEquals("知识正在同步中", duplicate.getMessage());
            assertEquals(1L, taskMapper.selectCount(
                    Wrappers.<AiKnowledgeEmbeddingTask>lambdaQuery()
                            .eq(AiKnowledgeEmbeddingTask::getKnowledgeId, knowledgeId)));
        } finally {
            deleteByKnowledgeKey(knowledgeKey);
        }
    }

    @Test
    void shouldQueueOnlyOnceWhenTwoAdministratorsRetryConcurrently() throws Exception {
        String knowledgeKey = uniqueKey();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Long knowledgeId = persistenceService.saveFirstVersion(
                    pendingFirstVersion(knowledgeKey));
            markEmbeddingAsFinallyFailed(knowledgeId);
            CountDownLatch start = new CountDownLatch(1);

            Future<String> first = executor.submit(() -> {
                start.await();
                return knowledgeService.retryEmbedding(knowledgeId).getMessage();
            });
            Future<String> second = executor.submit(() -> {
                start.await();
                return knowledgeService.retryEmbedding(knowledgeId).getMessage();
            });
            start.countDown();

            Set<String> messages = Set.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));
            assertEquals(Set.of("已提交，向量同步中", "知识正在同步中"), messages);
            assertEquals(1L, taskMapper.selectCount(
                    Wrappers.<AiKnowledgeEmbeddingTask>lambdaQuery()
                            .eq(AiKnowledgeEmbeddingTask::getKnowledgeId, knowledgeId)));
        } finally {
            executor.shutdownNow();
            deleteByKnowledgeKey(knowledgeKey);
        }
    }

    private void markEmbeddingAsFinallyFailed(Long knowledgeId) {
        AiKnowledge knowledge = knowledgeMapper.selectById(knowledgeId);
        knowledge.setEmbeddingStatus(AiKnowledgeEmbeddingStatus.FAILED);
        assertEquals(1, knowledgeMapper.updateById(knowledge));

        AiKnowledgeEmbeddingTask task = taskMapper.selectOne(
                Wrappers.<AiKnowledgeEmbeddingTask>lambdaQuery()
                        .eq(AiKnowledgeEmbeddingTask::getKnowledgeId, knowledgeId));
        task.setStatus(AiKnowledgeEmbeddingTaskStatus.FAILED);
        task.setRetryCount(task.getMaxRetryCount());
        task.setLastError("test failure");
        assertEquals(1, taskMapper.updateById(task));
    }

    private AiKnowledge insertSource(String knowledgeKey) {
        LocalDateTime now = LocalDateTime.now();
        AiKnowledge source = AiKnowledge.builder()
                .knowledgeKey(knowledgeKey)
                .title("营业时间")
                .category(AiKnowledgeCategory.SHOP)
                .content("餐云每天09:00至21:00营业。")
                .embedding(validEmbedding())
                .embeddingModel("embedding-3")
                .embeddingDimensions(256)
                .embeddingStatus(AiKnowledgeEmbeddingStatus.READY)
                .versionNo(1)
                .status(AiKnowledgeStatus.ENABLED)
                .createTime(now)
                .updateTime(now)
                .build();
        assertEquals(1, knowledgeMapper.insert(source));
        return source;
    }

    private AiKnowledge pendingFirstVersion(String knowledgeKey) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        return AiKnowledge.builder()
                .knowledgeKey(knowledgeKey)
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

    private void insertExistingSecondVersion(String knowledgeKey) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        AiKnowledge version = AiKnowledge.builder()
                .knowledgeKey(knowledgeKey)
                .title("已有第二版")
                .category(AiKnowledgeCategory.SHOP)
                .content("用于制造版本号唯一键冲突。")
                .embedding(validEmbedding())
                .embeddingModel("embedding-3")
                .embeddingDimensions(256)
                .embeddingStatus(AiKnowledgeEmbeddingStatus.READY)
                .versionNo(2)
                .status(AiKnowledgeStatus.DISABLED)
                .createTime(now)
                .updateTime(now)
                .build();
        assertEquals(1, knowledgeMapper.insert(version));
    }

    private AiKnowledge nextVersionDraft() {
        return AiKnowledge.builder()
                .title("营业时间")
                .category(AiKnowledgeCategory.SHOP)
                .content("餐云每天09:00至22:00营业。")
                .embeddingStatus(AiKnowledgeEmbeddingStatus.PENDING)
                .build();
    }

    private String validEmbedding() {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < 256; i++) {
            if (i > 0) {
                result.append(',');
            }
            result.append("0.0");
        }
        return result.append(']').toString();
    }

    private String uniqueKey() {
        return "TEST_VERSION_" + UUID.randomUUID().toString().replace("-", "");
    }

    private void deleteByKnowledgeKey(String knowledgeKey) {
        List<AiKnowledge> versions = knowledgeMapper.selectList(
                Wrappers.<AiKnowledge>lambdaQuery()
                        .eq(AiKnowledge::getKnowledgeKey, knowledgeKey));
        if (!versions.isEmpty()) {
            taskMapper.delete(Wrappers.<AiKnowledgeEmbeddingTask>lambdaQuery()
                    .in(AiKnowledgeEmbeddingTask::getKnowledgeId,
                            versions.stream().map(AiKnowledge::getId).toList()));
        }
        knowledgeMapper.delete(Wrappers.<AiKnowledge>lambdaQuery()
                .eq(AiKnowledge::getKnowledgeKey, knowledgeKey));
    }
}
