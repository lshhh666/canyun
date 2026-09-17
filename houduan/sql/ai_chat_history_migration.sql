-- AI客服最近会话恢复：把待确认动作准确关联到原客服消息。
-- 已有动作无法可靠推断对应消息，因此旧数据保持NULL；新动作会写入该字段。
ALTER TABLE `ai_chat_pending_action`
    ADD COLUMN `assistant_message_id` BIGINT NULL DEFAULT NULL
        COMMENT '生成确认按钮的客服消息ID，用于重新进入页面时恢复到原消息' AFTER `target_order_id`,
    ADD UNIQUE KEY `uk_ai_action_assistant_message` (`assistant_message_id`);

-- 查询当前用户最近会话时按最近活动时间倒序。
ALTER TABLE `ai_chat_session`
    ADD KEY `idx_ai_chat_session_user_recent` (`user_id`, `update_time`, `id`);
