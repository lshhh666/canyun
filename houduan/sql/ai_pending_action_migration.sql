-- AI客服敏感动作二次确认表。
-- 用户的自然语言只能创建待确认动作，真正执行必须携带后端返回的action_id调用确认接口。
CREATE TABLE IF NOT EXISTS `ai_chat_pending_action` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '待确认动作主键',
    `user_id` BIGINT NOT NULL COMMENT 'JWT对应的用户ID',
    `session_id` BIGINT NOT NULL COMMENT '动作所属AI会话ID',
    `action_type` TINYINT UNSIGNED NOT NULL COMMENT '动作类型：1-取消订单',
    `target_order_id` BIGINT NOT NULL COMMENT '创建动作时冻结的目标订单ID',
    `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1-等待确认，2-执行成功，3-拒绝，4-过期，5-被新动作替代',
    `expire_time` DATETIME NOT NULL COMMENT '确认截止时间',
    `result_message` VARCHAR(255) NULL COMMENT '幂等执行结果',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_ai_action_user_session_status` (`user_id`, `session_id`, `status`, `expire_time`),
    KEY `idx_ai_action_target_order` (`target_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服待确认敏感动作';
