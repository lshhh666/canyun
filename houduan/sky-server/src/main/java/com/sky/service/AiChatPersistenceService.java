package com.sky.service;

import com.sky.entity.AiChatMessage;
import com.sky.service.model.AiChatTurnClaim;
import com.sky.service.model.AiChatHistorySnapshot;
import com.sky.service.model.AiPendingActionDraft;
import com.sky.service.model.AiPendingActionReceipt;

import java.util.List;

/**
 * AI 客服会话与消息持久化服务。
 * 负责通过短事务保存用户消息、模型回答以及恢复会话状态。
 */
public interface AiChatPersistenceService {

    /** 查询当前用户最近一次可继续会话及其历史恢复数据。 */
    AiChatHistorySnapshot loadLatestHistory(Long userId, int limit);

    /**
     * 结束当前会话并废弃其中尚未确认的操作。
     * 正在正常处理的会话不能关闭；超过处理期限后允许关闭。
     */
    void closeForNewConversation(Long sessionId, Long userId);

    /**
     * 创建或校验会话，保存USER（用户消息），
     * 并将会话改为PROCESSING（AI正在回答）。
     * 正在处理且未到期时拒绝；到期后保留旧消息，接替为新的处理轮次。
     *
     * @param sessionId 前端传入的会话ID，首次聊天时为空
     * @param userId    当前JWT对应的用户ID
     * @param message   用户消息
     * @return 实际会话ID及本轮用户消息序号，仅在后端流转
     */
    AiChatTurnClaim saveUserMessage(Long sessionId, Long userId, String message);

    /**
     * 查询当前用户会话最近的消息，按消息序号从旧到新返回。
     *
     * @param sessionId 会话ID
     * @param userId    当前JWT对应的用户ID
     * @param limit     最多返回的消息数量
     * @return 最近的消息列表
     */
    List<AiChatMessage> listRecentMessages(Long sessionId, Long userId, int limit);

    /** 校验当前轮次，查询截至本轮用户消息的上下文，避免读入后续提问。 */
    List<AiChatMessage> listTurnMessages(AiChatTurnClaim claim, Long userId, int limit);

    /**
     * 保存ASSISTANT（AI客服回答），
     * 并将会话恢复为ACTIVE（可以发送消息）。
     *
     * @param claim    保存用户消息时取得的处理凭据，已被接替时拒绝保存
     * @param userId    当前JWT对应的用户ID
     * @param answer    AI客服回答
     */
    default void saveAssistantMessage(AiChatTurnClaim claim, Long userId, String answer) {
        saveAssistantMessage(claim, userId, answer, null);
    }

    /**
     * 保存回答，并在本轮成功完成时原子更新会话选中的订单。
     * selectedOrderId为空表示保留原来的选择。
     */
    void saveAssistantMessage(AiChatTurnClaim claim, Long userId, String answer,
                              Long selectedOrderId);

    /**
     * 在同一个短事务里保存客服的确认提示、创建待确认动作并恢复会话。
     * 任一步失败都会整体回滚，避免前端看见按钮但数据库没有对应动作。
     */
    AiPendingActionReceipt saveAssistantMessageWithPendingAction(
            AiChatTurnClaim claim, Long userId, String answer,
            Long selectedOrderId, AiPendingActionDraft pendingAction);

    /**
     * AI调用失败后，不保存回答，
     * 只将会话从PROCESSING（AI正在回答）恢复为ACTIVE（可以发送消息）。
     *
     * @param claim    原处理凭据，已被接替或已结束时不改变当前状态
     * @param userId    当前JWT对应的用户ID
     */
    void restoreActiveAfterFailure(AiChatTurnClaim claim, Long userId);
}
