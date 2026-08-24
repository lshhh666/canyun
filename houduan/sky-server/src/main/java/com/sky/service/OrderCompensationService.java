package com.sky.service;

/**
 * 订单补偿任务服务
 */
public interface OrderCompensationService {

    /**
     * 记录一次超时取消失败。
     *
     * @param orderId       订单ID
     * @param userCouponId  用户优惠券ID，无券订单可为空
     * @param failedReason  失败原因摘要
     */
    void recordTimeoutCancelFailure(Long orderId, Long userCouponId, String failedReason);

    /** 扫描并处理一批已经到期的订单补偿任务。 */
    void processDueTasks();
}
