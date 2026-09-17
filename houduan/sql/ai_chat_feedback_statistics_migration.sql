-- 管理端按首次评价时间筛选统计时使用。
-- 已执行 ai_chat_feedback_migration.sql 的数据库再执行一次本迁移。
ALTER TABLE `ai_chat_feedback`
    ADD INDEX `idx_ai_chat_feedback_create_time` (`create_time`);
