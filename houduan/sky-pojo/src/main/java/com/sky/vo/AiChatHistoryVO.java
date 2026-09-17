package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/** 重新进入AI客服页面时使用的最近会话快照。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatHistoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long sessionId;
    private boolean processing;
    private List<AiChatHistoryMessageVO> messages;
}
