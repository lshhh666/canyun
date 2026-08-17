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
