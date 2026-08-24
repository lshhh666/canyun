package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sky.entity.OrderCompensationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单补偿任务数据访问层
 */
@Mapper
public interface OrderCompensationTaskMapper extends BaseMapper<OrderCompensationTask> {

    /**
     * 记录订单取消失败；同一订单的同类任务已存在时，仅刷新最近失败信息。
     *
     * @param task 补偿任务
     * @return 受影响行数
     */
    int insertOrUpdateFailure(OrderCompensationTask task);

    /**
     * 原子抢占一条已经到期的待处理任务。
     * 返回1表示当前线程抢占成功，返回0表示任务未到期或已被其他线程抢占。
     *
     * @param id  补偿任务ID
     * @param now 当前处理时间
     * @return 受影响行数
     */
    int claimPendingTask(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * 按到期时间查询一批待处理任务，先到期的任务优先。
     * 查询结果只是候选任务，执行前仍需通过条件更新原子抢占。
     *
     * @param now   当前扫描时间
     * @param limit 单次扫描上限
     * @return 到期待处理任务
     */
    List<OrderCompensationTask> findDuePendingTasks(@Param("taskType") Integer taskType,
                                                    @Param("now") LocalDateTime now,
                                                    @Param("limit") int limit);

    /** 将当前正在处理的任务标记为成功。 */
    int markTaskSuccess(@Param("id") Long id,
                        @Param("processingTime") LocalDateTime processingTime,
                        @Param("now") LocalDateTime now);

    /**
     * 记录一次实际补偿失败。未达到最大次数时重新等待，达到上限时转人工处理。
     */
    int markTaskRetryFailure(@Param("id") Long id,
                             @Param("processingTime") LocalDateTime processingTime,
                             @Param("failedReason") String failedReason,
                             @Param("now") LocalDateTime now,
                             @Param("nextRetryTime") LocalDateTime nextRetryTime,
                             @Param("maxRetryCount") int maxRetryCount);

    /**
     * 将超过处理时限仍未结束的任务恢复为待处理，供其他实例重新抢占。
     */
    int recoverStaleProcessingTasks(@Param("taskType") Integer taskType,
                                    @Param("staleBefore") LocalDateTime staleBefore,
                                    @Param("nextRetryTime") LocalDateTime nextRetryTime,
                                    @Param("now") LocalDateTime now,
                                    @Param("limit") int limit);
}
