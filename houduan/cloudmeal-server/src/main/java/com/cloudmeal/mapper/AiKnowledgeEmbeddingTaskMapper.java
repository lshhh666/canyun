package com.cloudmeal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmeal.entity.AiKnowledgeEmbeddingTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** AI知识向量异步任务数据访问层。 */
@Mapper
public interface AiKnowledgeEmbeddingTaskMapper extends BaseMapper<AiKnowledgeEmbeddingTask> {

    AiKnowledgeEmbeddingTask selectByIdForUpdate(@Param("id") Long id);

    /** 按知识版本锁定其唯一向量任务，锁顺序与任务完成事务保持一致。 */
    AiKnowledgeEmbeddingTask selectByKnowledgeIdForUpdate(
            @Param("knowledgeId") Long knowledgeId);

    List<AiKnowledgeEmbeddingTask> findDuePendingTasks(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    /** 返回1表示当前实例抢占成功。 */
    int claimPendingTask(@Param("id") Long id,
                         @Param("now") LocalDateTime now);

    /** processingTime同时作为本次抢占令牌，防止迟到结果覆盖新一轮处理。 */
    int markSucceeded(@Param("id") Long id,
                      @Param("processingTime") LocalDateTime processingTime,
                      @Param("now") LocalDateTime now);

    int markRetryFailure(@Param("id") Long id,
                         @Param("processingTime") LocalDateTime processingTime,
                         @Param("lastError") String lastError,
                         @Param("nextRetryTime") LocalDateTime nextRetryTime,
                         @Param("now") LocalDateTime now);

    int markObsolete(@Param("id") Long id,
                     @Param("now") LocalDateTime now);

    int markActiveTasksObsoleteByKnowledgeId(@Param("knowledgeId") Long knowledgeId,
                                             @Param("now") LocalDateTime now);

    int recoverStaleProcessingTasks(
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("nextRetryTime") LocalDateTime nextRetryTime,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    /** 仅允许最终失败任务重新进入等待队列。 */
    int resetFailedForManualRetry(@Param("id") Long id,
                                  @Param("maxRetryCount") int maxRetryCount,
                                  @Param("now") LocalDateTime now);
}
