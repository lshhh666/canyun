package com.sky.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 受控工具生成的回答，以及需要随回答原子保存的内部会话状态。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiToolAnswer {

    private String answer;
    private Long selectedOrderId;
    private AiPendingActionDraft pendingAction;
    /** 当前操作缺少明确订单，需要先查询并让用户选择。 */
    private boolean orderSelectionRequired;
}
