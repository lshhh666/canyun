package com.sky.constant;

/**
 * 订单补偿任务常量
 */
public class OrderCompensationConstant {

    private OrderCompensationConstant() {
    }

    /** 任务类型：超时取消订单 */
    public static final int TASK_TYPE_TIMEOUT_CANCEL = 1;

    /** 任务状态：待处理 */
    public static final int STATUS_PENDING = 0;

    /** 任务状态：处理中 */
    public static final int STATUS_PROCESSING = 1;

    /** 任务状态：处理成功 */
    public static final int STATUS_SUCCESS = 2;

    /** 任务状态：需要人工处理 */
    public static final int STATUS_MANUAL = 3;

    /** 自动重试间隔，单位：分钟 */
    public static final long RETRY_INTERVAL_MINUTES = 5L;

    /** 最大自动重试次数 */
    public static final int MAX_RETRY_COUNT = 3;

    /** 处理中任务的超时时间，单位：分钟 */
    public static final long PROCESSING_TIMEOUT_MINUTES = 10L;

    /** 单次扫描补偿任务的最大数量，避免积压任务造成瞬时压力 */
    public static final int SCAN_BATCH_SIZE = 100;
}
