package com.sky.service;

import com.sky.vo.AiChatVO;
import com.sky.vo.AiChatHistoryVO;

public interface AiChatService {

    /** 恢复当前用户最近一次可继续的会话。 */
    AiChatHistoryVO getRecentHistory();

    /** 结束当前会话，下一条消息将创建新会话。 */
    void startNewConversation(Long sessionId);

    AiChatVO chat(String message, Long sessionId);

    /** 仅供前端明确的确认按钮调用。 */
    AiChatVO confirmAction(Long actionId);
}
