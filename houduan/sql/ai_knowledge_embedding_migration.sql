-- 仅用于已经创建过 ai_knowledge 表的本地数据库。
-- 如果数据库是直接使用最新版 ai_customer_service.sql 新建的，请不要重复执行本文件。
ALTER TABLE `ai_knowledge`
    ADD COLUMN `embedding` JSON NULL COMMENT '由标题和规则正文生成的语义向量' AFTER `content`,
    ADD COLUMN `embedding_model` VARCHAR(32) NULL COMMENT '生成向量的模型名称' AFTER `embedding`,
    ADD COLUMN `embedding_dimensions` SMALLINT UNSIGNED NULL COMMENT '向量维度' AFTER `embedding_model`;

-- 历史知识没有可安全推导的向量，必须保持为空，后续迁移会将其标记为待生成并创建任务。
