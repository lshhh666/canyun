-- 优惠券模板
CREATE TABLE IF NOT EXISTS `coupon` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '优惠券ID',
    `name` VARCHAR(64) NOT NULL COMMENT '优惠券名称',
    `threshold_amount` DECIMAL(10, 2) NOT NULL COMMENT '使用门槛金额',
    `discount_amount` DECIMAL(10, 2) NOT NULL COMMENT '优惠金额',
    `total_stock` INT NOT NULL COMMENT '发行总量',
    `stock` INT NOT NULL COMMENT '剩余库存',
    `receive_start_time` DATETIME NOT NULL COMMENT '领取开始时间',
    `receive_end_time` DATETIME NOT NULL COMMENT '领取结束时间',
    `valid_start_time` DATETIME NOT NULL COMMENT '使用开始时间',
    `valid_end_time` DATETIME NOT NULL COMMENT '使用截止时间',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0草稿，1发放中，2停用',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_coupon_receive` (`status`, `receive_start_time`, `receive_end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板';

-- 用户领取的优惠券。金额与有效期保存领取时快照，避免模板修改影响历史数据。
CREATE TABLE IF NOT EXISTS `user_coupon` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户优惠券ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `coupon_id` BIGINT NOT NULL COMMENT '优惠券模板ID',
    `coupon_name` VARCHAR(64) NOT NULL COMMENT '优惠券名称快照',
    `threshold_amount` DECIMAL(10, 2) NOT NULL COMMENT '门槛金额快照',
    `discount_amount` DECIMAL(10, 2) NOT NULL COMMENT '优惠金额快照',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0可用，1锁定，2已使用，3已过期',
    `order_id` BIGINT NULL COMMENT '锁定或使用该券的订单ID',
    `receive_time` DATETIME NOT NULL COMMENT '领取时间',
    `valid_start_time` DATETIME NOT NULL COMMENT '使用开始时间',
    `valid_end_time` DATETIME NOT NULL COMMENT '使用截止时间',
    `locked_time` DATETIME NULL COMMENT '锁定时间',
    `used_time` DATETIME NULL COMMENT '核销时间',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_coupon` (`user_id`, `coupon_id`),
    UNIQUE KEY `uk_user_coupon_order` (`order_id`),
    KEY `idx_user_coupon_status` (`user_id`, `status`, `valid_end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券';

-- 订单保存服务端计价快照和使用的用户优惠券。
-- 以下语句通过 information_schema 判断后再迁移，已存在的列或索引不会重复创建。
SET @canyun_schema = DATABASE();

SET @canyun_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @canyun_schema AND TABLE_NAME = 'orders'
             AND COLUMN_NAME = 'original_amount'),
    'SELECT 1',
    'ALTER TABLE `orders` ADD COLUMN `original_amount` DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT ''订单原价快照'' AFTER `amount`'
);
PREPARE canyun_stmt FROM @canyun_sql;
EXECUTE canyun_stmt;
DEALLOCATE PREPARE canyun_stmt;

SET @canyun_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @canyun_schema AND TABLE_NAME = 'orders'
             AND COLUMN_NAME = 'discount_amount'),
    'SELECT 1',
    'ALTER TABLE `orders` ADD COLUMN `discount_amount` DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT ''优惠金额快照'' AFTER `original_amount`'
);
PREPARE canyun_stmt FROM @canyun_sql;
EXECUTE canyun_stmt;
DEALLOCATE PREPARE canyun_stmt;

SET @canyun_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @canyun_schema AND TABLE_NAME = 'orders'
             AND COLUMN_NAME = 'user_coupon_id'),
    'SELECT 1',
    'ALTER TABLE `orders` ADD COLUMN `user_coupon_id` BIGINT NULL COMMENT ''订单使用的用户优惠券ID'' AFTER `discount_amount`'
);
PREPARE canyun_stmt FROM @canyun_sql;
EXECUTE canyun_stmt;
DEALLOCATE PREPARE canyun_stmt;

SET @canyun_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = @canyun_schema AND TABLE_NAME = 'orders'
             AND INDEX_NAME = 'idx_orders_user_coupon'),
    'SELECT 1',
    'ALTER TABLE `orders` ADD INDEX `idx_orders_user_coupon` (`user_coupon_id`)'
);
PREPARE canyun_stmt FROM @canyun_sql;
EXECUTE canyun_stmt;
DEALLOCATE PREPARE canyun_stmt;

-- 订单补偿任务。业务事务回滚后独立记录，用于有限重试超时取消流程。
CREATE TABLE IF NOT EXISTS `order_compensation_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '补偿任务ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `user_coupon_id` BIGINT NULL COMMENT '用户优惠券ID，无券订单可为空',
    `task_type` TINYINT NOT NULL DEFAULT 1 COMMENT '任务类型：1超时取消订单',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0待处理，1处理中，2成功，3人工处理',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '已执行的自动重试次数',
    `next_retry_time` DATETIME NOT NULL COMMENT '下次允许重试时间',
    `processing_time` DATETIME NULL COMMENT '最近一次开始处理时间',
    `first_failed_time` DATETIME NOT NULL COMMENT '首次失败时间',
    `last_failed_time` DATETIME NOT NULL COMMENT '最近失败时间',
    `success_time` DATETIME NULL COMMENT '补偿成功时间',
    `failed_reason` TEXT NOT NULL COMMENT '最近失败原因',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_compensation_task` (`order_id`, `task_type`),
    KEY `idx_compensation_pending` (`status`, `next_retry_time`),
    KEY `idx_compensation_processing` (`status`, `processing_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单补偿任务';
