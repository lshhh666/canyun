package com.sky.service.model;

import com.sky.enums.AiPendingActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 待确认动作与客服回答在同一事务提交后的安全回执。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPendingActionReceipt {

    private Long actionId;
    private AiPendingActionType actionType;
    private LocalDateTime expireTime;
}
