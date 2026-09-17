package com.sky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sky.enums.AiChatRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 客服消息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_chat_message")
public class AiChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 消息所属会话ID。 */
    @TableField("session_id")
    private Long sessionId;

    /** 消息角色：USER或ASSISTANT。 */
    @TableField("role")
    private AiChatRole role;

    /** 消息正文。 */
    @TableField("content")
    private String content;

    /** 会话内消息序号，从1开始。 */
    @TableField("sequence_no")
    private Integer sequenceNo;

    /** 创建时间。 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
