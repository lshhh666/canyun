-- 评价统计与未解决列表统一按最后评价时间筛选。
-- 已创建 ai_chat_feedback 表的数据库执行一次本迁移。
ALTER TABLE `ai_chat_feedback`
    ADD INDEX `idx_ai_chat_feedback_update_time` (`update_time`);
