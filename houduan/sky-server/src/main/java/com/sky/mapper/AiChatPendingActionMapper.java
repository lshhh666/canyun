package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sky.entity.AiChatPendingAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface AiChatPendingActionMapper extends BaseMapper<AiChatPendingAction> {

    /** 查询会话中仍有效、可恢复到历史消息上的最新确认动作。 */
    AiChatPendingAction selectRestorable(@Param("sessionId") Long sessionId,
                                         @Param("userId") Long userId,
                                         @Param("now") LocalDateTime now);

    AiChatPendingAction selectOwned(@Param("actionId") Long actionId,
                                    @Param("userId") Long userId);

    AiChatPendingAction selectOwnedForUpdate(@Param("actionId") Long actionId,
                                             @Param("userId") Long userId);

    int supersedePending(@Param("sessionId") Long sessionId,
                         @Param("userId") Long userId,
                         @Param("resultMessage") String resultMessage,
                         @Param("updateTime") LocalDateTime updateTime);

    int completePending(@Param("actionId") Long actionId,
                        @Param("userId") Long userId,
                        @Param("newStatus") int newStatus,
                        @Param("resultMessage") String resultMessage,
                        @Param("updateTime") LocalDateTime updateTime);
}
