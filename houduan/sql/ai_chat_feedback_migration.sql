-- 已创建AI客服基础表的数据库只需执行本迁移。
-- 同一会话只保留一条评价；result=2的数据即“没解决”待复核队列。
CREATE TABLE IF NOT EXISTS `ai_chat_feedback` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话评价ID',
    `session_id` BIGINT NOT NULL COMMENT '被评价的AI会话ID',
    `user_id` BIGINT NOT NULL COMMENT 'JWT对应的用户ID',
    `result` TINYINT UNSIGNED NOT NULL COMMENT '评价结果：1-有帮助，2-没解决',
    `create_time` DATETIME NOT NULL COMMENT '首次评价时间',
    `update_time` DATETIME NOT NULL COMMENT '最近评价时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_chat_feedback_session` (`session_id`),
    KEY `idx_ai_chat_feedback_review` (`result`, `update_time`, `id`),
    KEY `idx_ai_chat_feedback_user` (`user_id`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服会话评价';
