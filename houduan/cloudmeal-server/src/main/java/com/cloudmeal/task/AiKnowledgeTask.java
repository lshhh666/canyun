package com.cloudmeal.task;

import com.cloudmeal.service.AiKnowledgeEmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期处理低频AI知识向量任务。 */
@Component
@RequiredArgsConstructor
public class AiKnowledgeTask {

    private final AiKnowledgeEmbeddingService embeddingService;

    @Scheduled(
            initialDelayString = "${cloudmeal.ai.embedding-task-initial-delay-ms:5000}",
            fixedDelayString = "${cloudmeal.ai.embedding-task-scan-delay-ms:5000}")
    public void processEmbeddingTasks() {
        embeddingService.processDueTasks();
    }
}
