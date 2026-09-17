package com.cloudmeal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmeal.enums.AiChatFeedbackHandleStatus;
import com.cloudmeal.enums.AiChatFeedbackResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** AI客服会话级评价。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_chat_feedback")
public class AiChatFeedback implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private Long sessionId;

    @TableField("user_id")
    private Long userId;

    @TableField("result")
    private AiChatFeedbackResult result;

    /** 管理端处理进度，与用户评价结果相互独立。 */
    @TableField("handle_status")
    private AiChatFeedbackHandleStatus handleStatus;

    /** 最终确认处理完成的管理员ID。 */
    @TableField("handler_id")
    private Long handlerId;

    /** 最终确认处理完成时间。 */
    @TableField("handle_time")
    private LocalDateTime handleTime;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
