package com.cloudmeal.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** AI客服历史消息；待确认按钮只挂在生成它的客服消息上。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatHistoryMessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long messageId;
    private String role;
    private String content;
    private Integer sequenceNo;
    private LocalDateTime createTime;
    private AiChatActionVO action;
}
