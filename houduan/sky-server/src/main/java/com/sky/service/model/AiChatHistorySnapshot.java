package com.sky.service.model;

import com.sky.entity.AiChatMessage;
import com.sky.entity.AiChatPendingAction;
import com.sky.entity.AiChatSession;
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
