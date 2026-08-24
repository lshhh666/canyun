package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单补偿任务
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCompensationTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 补偿任务ID */
    private Long id;

    /** 需要重新执行超时取消的订单ID */
    private Long orderId;

    /** 订单关联的用户优惠券ID，无券订单可为空 */
    private Long userCouponId;

    /** 任务类型：1-超时取消订单 */
    private Integer taskType;

    /** 任务状态：0-待处理，1-处理中，2-成功，3-人工处理 */
    private Integer status;

    /** 已执行的自动重试次数，首次记录失败时为0 */
    private Integer retryCount;

    /** 下次允许自动重试的时间 */
    private LocalDateTime nextRetryTime;

    /** 最近一次被任务抢占并开始处理的时间 */
    private LocalDateTime processingTime;

    /** 首次取消失败时间 */
    private LocalDateTime firstFailedTime;

    /** 最近一次取消失败时间 */
    private LocalDateTime lastFailedTime;

    /** 补偿成功时间 */
    private LocalDateTime successTime;

    /** 最近一次失败原因 */
    private String failedReason;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
