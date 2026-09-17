package com.cloudmeal.service.model;

import com.cloudmeal.entity.AiChatMessage;
import com.cloudmeal.entity.AiChatPendingAction;
import com.cloudmeal.entity.AiChatSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 最近会话恢复所需的服务端可信数据。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatHistorySnapshot {

    private AiChatSession session;
    private List<AiChatMessage> messages;
    private AiChatPendingAction pendingAction;
    private boolean processing;
}
