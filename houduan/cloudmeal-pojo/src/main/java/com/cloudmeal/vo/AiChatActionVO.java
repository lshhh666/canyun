package com.cloudmeal.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 前端可展示的AI客服确认按钮，不暴露目标订单主键。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatActionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long actionId;
    private String actionType;
    private String label;
    private LocalDateTime expireTime;
}
