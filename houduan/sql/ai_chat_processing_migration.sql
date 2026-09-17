-- 仅用于已创建 ai_chat_session、但尚未添加以下两个字段的数据库。
-- 若已使用最新版 ai_customer_service.sql 新建会话表，不要重复执行本文件。
-- 执行前：确认选中餐云数据库，并使用 SHOW COLUMNS FROM ai_chat_session 检查字段。
-- 本迁移只需执行一次；不会删表、删除消息或批量修改会话状态。
-- 先完成数据库迁移，再启动包含新实体字段/Mapper查询的后端代码。
-- 已有会话的两个新字段初始均为NULL；历史PROCESSING（正在处理）会话不会在这里被恢复。
-- 配套后端会在下一次提问时检查超时并接替；本SQL本身不会开启定时恢复。
-- 升级时先停止旧后端，迁移后启动新后端；不要让不校验轮次的旧代码与新代码同时处理会话。
-- 旧PROCESSING记录没有截止时间时，新后端按update_time加配置的处理期限判断是否可接替。
ALTER TABLE `ai_chat_session`
    ADD COLUMN `processing_sequence_no` INT NULL DEFAULT NULL
        COMMENT '当前正在处理的用户提问顺序号，与会话ID共同标识本轮请求' AFTER `status`,
    ADD COLUMN `processing_deadline` DATETIME NULL DEFAULT NULL
        COMMENT '本轮处理截止时间，不是实际结束时间' AFTER `processing_sequence_no`;
