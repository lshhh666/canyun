package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.pagehelper.Page;
import com.sky.entity.AiChatFeedback;
import com.sky.vo.AiChatFeedbackStatisticsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** AI客服会话评价数据访问层。 */
@Mapper
public interface AiChatFeedbackMapper extends BaseMapper<AiChatFeedback> {

    /** 同一会话重复评价时更新原记录，避免产生重复待处理项。 */
    int upsert(@Param("feedback") AiChatFeedback feedback,
               @Param("resultValue") int resultValue);

    /** 锁定同一会话的已有评价，用于安全处理评价变更。 */
    AiChatFeedback selectBySessionIdForUpdate(@Param("sessionId") Long sessionId);

    /** 评价结果改变后使尚未人工确认的旧复测失效。 */
    int obsoleteActiveRetests(@Param("feedbackId") Long feedbackId,
                              @Param("now") LocalDateTime now);

    /** 评价结果改变后删除旧知识关联，避免后续复用过期处理上下文。 */
    int deleteKnowledgeRelations(@Param("feedbackId") Long feedbackId);

    /** 按最后评价时间聚合管理端客服评价数据。 */
    AiChatFeedbackStatisticsVO statistics(
            @Param("beginTime") LocalDateTime beginTime,
            @Param("endExclusive") LocalDateTime endExclusive);

    /** 按最后评价时间倒序分页查询当前未解决评价。 */
    Page<AiChatFeedback> pageUnsolved(
            @Param("beginTime") LocalDateTime beginTime,
            @Param("endExclusive") LocalDateTime endExclusive);

    /** 查询评价并持有短事务排他锁，用于并发确认复测结果。 */
    AiChatFeedback selectByIdForUpdate(@Param("id") Long id);

    /** 关联知识后将尚未完成的“没解决”评价推进到待复测。 */
    int markPendingRetest(@Param("id") Long id,
                          @Param("now") LocalDateTime now);

    /** 仅允许待复测评价被最终确认处理完成。 */
    int markHandled(@Param("id") Long id,
                    @Param("handlerId") Long handlerId,
                    @Param("now") LocalDateTime now);
}
