package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sky.entity.AiChatMessage;
import com.sky.vo.AiChatFeedbackTurnVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 客服消息数据访问层。
 */
@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {

    /**
     * 计算指定会话的下一个消息序号。
     * 调用前必须先通过会话Mapper锁定对应会话。
     */
    Integer selectNextSequenceNo(@Param("sessionId") Long sessionId);

    /**
     * 查询会话最近的若干条消息，并按消息序号从旧到新返回。
     */
    List<AiChatMessage> selectRecentMessages(@Param("sessionId") Long sessionId,
                                             @Param("limit") int limit);

    /** 同一会话中，仅取截至本轮用户消息的最近N条上下文。 */
    List<AiChatMessage> selectTurnMessages(@Param("sessionId") Long sessionId,
                                          @Param("sequenceNo") int sequenceNo,
                                          @Param("limit") int limit);

    /** 批量查询每个会话最后一条AI回答及其前一条用户问题。 */
    List<AiChatFeedbackTurnVO> selectLatestTurnsBySessionIds(
            @Param("sessionIds") List<Long> sessionIds);
}
