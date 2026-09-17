package com.sky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sky.enums.AiPendingActionStatus;
import com.sky.enums.AiPendingActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** AI客服等待用户通过前端按钮确认的受控动作。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_chat_pending_action")
public class AiChatPendingAction implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("session_id")
    private Long sessionId;

    @TableField("action_type")
    private AiPendingActionType actionType;

    /** 创建确认动作时冻结的目标订单主键，不跟随后续selected_order_id变化。 */
    @TableField("target_order_id")
    private Long targetOrderId;

    /** 生成确认按钮的客服消息，用于历史恢复时把按钮放回正确位置。 */
    @TableField("assistant_message_id")
    private Long assistantMessageId;

    @TableField("status")
    private AiPendingActionStatus status;

    @TableField("expire_time")
    private LocalDateTime expireTime;

    /** 保存幂等执行结果，重复点击时返回相同的业务结论。 */
    @TableField("result_message")
    private String resultMessage;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
