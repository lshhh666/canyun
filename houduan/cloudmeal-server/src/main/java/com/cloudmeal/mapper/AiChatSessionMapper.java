package com.cloudmeal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmeal.entity.AiChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * AI 客服会话数据访问层。
 */
@Mapper
public interface AiChatSessionMapper extends BaseMapper<AiChatSession> {

    /** 查询当前用户最近活动的、仍可续接的会话。 */
    AiChatSession selectLatestContinuable(@Param("userId") Long userId);

    /** 查询当前用户拥有的会话，不加锁。 */
    AiChatSession selectOwned(@Param("sessionId") Long sessionId,
                              @Param("userId") Long userId);

    /**
     * 查询当前用户拥有的会话并加排他锁。
     * 必须在事务中调用，锁会一直持有到事务提交或回滚。
     */
    AiChatSession selectOwnedForUpdate(@Param("sessionId") Long sessionId,
                                       @Param("userId") Long userId);

    /**
     * 已在短事务中锁定会话并验证可接替后，登记新的处理轮次。
     */
    int claimProcessingTurn(@Param("sessionId") Long sessionId,
                     @Param("userId") Long userId,
                     @Param("expectedStatus") int expectedStatus,
                     @Param("processingSequenceNo") int processingSequenceNo,
                     @Param("processingDeadline") LocalDateTime processingDeadline,
                     @Param("updateTime") LocalDateTime updateTime);

    /** 只有仍处于PROCESSING（正在处理）且轮次相同时，才释放占用。 */
    int completeProcessingTurn(@Param("sessionId") Long sessionId,
                               @Param("userId") Long userId,
                               @Param("processingSequenceNo") int processingSequenceNo,
                               @Param("selectedOrderId") Long selectedOrderId,
                               @Param("updateTime") LocalDateTime updateTime);

    /** 将指定用户的当前会话关闭，状态条件用于防止并发覆盖。 */
    int closeSession(@Param("sessionId") Long sessionId,
                     @Param("userId") Long userId,
                     @Param("expectedStatus") int expectedStatus,
                     @Param("updateTime") LocalDateTime updateTime);
}
