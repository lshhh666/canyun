package com.sky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sky.enums.AiChatSessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 客服会话。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_chat_session")
public class AiChatSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 会话所属用户ID，只能取自服务端登录上下文。 */
    @TableField("user_id")
    private Long userId;

    /** 会话标题。 */
    @TableField("title")
    private String title;

    /** 会话状态：0-已关闭，1-可以发送消息，2-AI正在回答。 */
    @TableField("status")
    private AiChatSessionStatus status;

    /**
     * 当前正在处理的USER（用户提问）的sequence_no（消息顺序号）。
     * 与会话ID共同标识本轮请求，用于后续拦截旧轮次的迟到回答或异常恢复。
     * 无处理轮次时为空；该字段不是消息表主键，也不是将要生成的回答顺序号。
     */
    @TableField("processing_sequence_no")
    private Integer processingSequenceNo;

    /**
     * 本轮处理截止时间，不是模型实际回答完成的时间。
     * 后续超时判断将使用该字段；无处理轮次时为空。
     */
    @TableField("processing_deadline")
    private LocalDateTime processingDeadline;

    /**
     * 当前会话选中的订单主键，仅供后端关联后续“这单”等追问。
     * 每次使用时仍必须结合当前登录用户重新校验订单归属。
     */
    @TableField("selected_order_id")
    private Long selectedOrderId;

    /** 创建时间。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 最近活动时间。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
