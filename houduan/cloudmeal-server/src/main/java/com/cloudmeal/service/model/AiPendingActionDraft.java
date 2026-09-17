package com.cloudmeal.service.model;

import com.cloudmeal.enums.AiPendingActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工具层提出、但尚未落库的待确认动作。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPendingActionDraft {

    private AiPendingActionType actionType;
    private Long targetOrderId;
}
