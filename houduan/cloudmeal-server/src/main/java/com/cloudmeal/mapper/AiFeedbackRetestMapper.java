package com.cloudmeal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmeal.entity.AiFeedbackRetest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** AI未解决评价复测数据访问层。 */
@Mapper
public interface AiFeedbackRetestMapper extends BaseMapper<AiFeedbackRetest> {

    /** 锁定仍在执行或等待人工确认的最新复测，防止重复创建活动任务。 */
    AiFeedbackRetest selectUnfinishedByFeedbackIdForUpdate(
            @Param("feedbackId") Long feedbackId);

    /** 查看反馈最近一次复测，不加载更早的历史复测。 */
    AiFeedbackRetest selectLatestByFeedbackId(@Param("feedbackId") Long feedbackId);

    /** 人工确认前锁定指定复测，和反馈行保持统一加锁顺序。 */
    AiFeedbackRetest selectByIdForUpdate(@Param("id") Long id);

    List<AiFeedbackRetest> findDuePendingRetests(@Param("now") LocalDateTime now,
                                                 @Param("limit") int limit);

    int claimPendingRetest(@Param("id") Long id,
                           @Param("now") LocalDateTime now);

    int markExecutionSucceeded(@Param("id") Long id,
                               @Param("processingTime") LocalDateTime processingTime,
                               @Param("answer") String answer,
                               @Param("usedKnowledgeIds") String usedKnowledgeIds,
                               @Param("now") LocalDateTime now);

    int markRetryFailure(@Param("id") Long id,
                         @Param("processingTime") LocalDateTime processingTime,
                         @Param("lastError") String lastError,
                         @Param("nextRetryTime") LocalDateTime nextRetryTime,
                         @Param("now") LocalDateTime now);

    int markReviewed(@Param("id") Long id,
                     @Param("reviewResult") int reviewResult,
                     @Param("reviewerId") Long reviewerId,
                     @Param("now") LocalDateTime now);

    int recoverStaleProcessingRetests(@Param("staleBefore") LocalDateTime staleBefore,
                                      @Param("nextRetryTime") LocalDateTime nextRetryTime,
                                      @Param("now") LocalDateTime now,
                                      @Param("limit") int limit);
}
