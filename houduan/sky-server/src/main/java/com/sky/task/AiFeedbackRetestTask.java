package com.sky.task;

import com.sky.service.AiFeedbackRetestService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期处理低频AI反馈复测任务。 */
@Component
@RequiredArgsConstructor
public class AiFeedbackRetestTask {

    private final AiFeedbackRetestService retestService;

    @Scheduled(
            initialDelayString = "${sky.ai.retest-task-initial-delay-ms:5000}",
            fixedDelayString = "${sky.ai.retest-task-scan-delay-ms:5000}")
    public void processRetestTasks() {
        retestService.processDueTasks();
    }
}
