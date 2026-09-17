-- AI 客服“没解决”评价处理闭环增量脚本。
-- 适用于已经执行 ai_customer_service.sql 的数据库，仅执行一次。
-- 本脚本只修改表结构，不会直接改变现有评价结果或创建复测任务。

-- 评价结果与管理端处理进度分开保存。
ALTER TABLE `ai_chat_feedback`
    ADD COLUMN `handle_status` TINYINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '处理状态：0待处理，1待复测，2已处理' AFTER `result`,
    ADD COLUMN `handler_id` BIGINT NULL DEFAULT NULL
        COMMENT '最终确认处理完成的管理员ID' AFTER `handle_status`,
    ADD COLUMN `handle_time` DATETIME NULL DEFAULT NULL
        COMMENT '最终确认处理完成时间' AFTER `handler_id`,
    DROP INDEX `idx_ai_chat_feedback_review`,
    ADD INDEX `idx_ai_chat_feedback_review`
        (`result`, `handle_status`, `update_time`, `id`);

-- 异步生成向量期间 embedding 可以为空；已有知识默认视为已经同步完成。
ALTER TABLE `ai_knowledge`
    MODIFY COLUMN `embedding` JSON NULL
        COMMENT '由标题和规则正文生成的语义向量',
    MODIFY COLUMN `embedding_model` VARCHAR(32) NULL
        COMMENT '生成向量的模型名称',
    MODIFY COLUMN `embedding_dimensions` SMALLINT UNSIGNED NULL
        COMMENT '向量维度',
    ADD COLUMN `embedding_status` TINYINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '向量状态：0待生成，1可用，2生成失败' AFTER `embedding_dimensions`,
    DROP INDEX `idx_ai_knowledge_retrieval`,
    ADD INDEX `idx_ai_knowledge_retrieval`
        (`status`, `embedding_status`, `category`, `update_time`);

-- 一个知识版本对应一个向量生成任务。knowledge_id 已经唯一标识 knowledge_key 的某一版本。
CREATE TABLE IF NOT EXISTS `ai_knowledge_embedding_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '向量任务ID',
    `knowledge_id` BIGINT NOT NULL COMMENT '待生成向量的知识版本ID',
    `status` TINYINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '状态：0待处理，1处理中，2成功，3失败，4旧版本作废',
    `retry_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已执行的自动重试次数，不含首次执行',
    `max_retry_count` INT UNSIGNED NOT NULL DEFAULT 3 COMMENT '首次执行失败后最多自动重试次数',
    `next_retry_time` DATETIME NOT NULL COMMENT '下次允许执行时间',
    `processing_time` DATETIME NULL DEFAULT NULL COMMENT '最近一次开始处理时间',
    `success_time` DATETIME NULL DEFAULT NULL COMMENT '生成成功时间',
    `last_error` VARCHAR(500) NULL DEFAULT NULL COMMENT '最近一次内部失败摘要，不返回用户端',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_knowledge_embedding_task_knowledge` (`knowledge_id`),
    KEY `idx_ai_knowledge_embedding_task_pending` (`status`, `next_retry_time`, `id`),
    KEY `idx_ai_knowledge_embedding_task_processing` (`status`, `processing_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI知识向量生成任务';

-- 已有有效向量可直接使用；没有向量的历史知识进入异步补偿队列。
UPDATE `ai_knowledge`
SET `embedding_status` = 1
WHERE `embedding` IS NOT NULL
  AND `embedding_model` IS NOT NULL
  AND `embedding_dimensions` IS NOT NULL;

INSERT IGNORE INTO `ai_knowledge_embedding_task`
    (`knowledge_id`, `status`, `retry_count`, `max_retry_count`,
     `next_retry_time`, `create_time`, `update_time`)
SELECT `id`, 0, 0, 3, NOW(), NOW(), NOW()
FROM `ai_knowledge`
WHERE `embedding_status` = 0;

-- 关联稳定 knowledge_key，避免知识新增版本后反馈关联到旧ID而失效。
CREATE TABLE IF NOT EXISTS `ai_feedback_knowledge_relation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `feedback_id` BIGINT NOT NULL COMMENT '没解决的评价ID',
    `knowledge_key` VARCHAR(64) NOT NULL COMMENT '关联知识的稳定业务标识',
    `operator_id` BIGINT NOT NULL COMMENT '建立关联的管理员ID',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_feedback_knowledge_relation`
        (`feedback_id`, `knowledge_key`),
    KEY `idx_ai_feedback_knowledge_key` (`knowledge_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI未解决评价与知识关联';

-- 复测记录自身同时承担低频异步任务，不再额外创建一张复测任务表。
CREATE TABLE IF NOT EXISTS `ai_feedback_retest` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '复测ID',
    `feedback_id` BIGINT NOT NULL COMMENT '被复测的评价ID',
    `question` TEXT NOT NULL COMMENT '本次实际使用的问题快照，可与原问题不同',
    `answer` TEXT NULL COMMENT '模型成功生成的回答',
    `used_knowledge_ids` JSON NULL COMMENT '本次回答实际使用的知识版本ID快照',
    `execution_status` TINYINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '执行状态：0待处理，1处理中，2成功，3失败，4评价变更后失效',
    `review_result` TINYINT UNSIGNED NULL DEFAULT NULL
        COMMENT '人工确认结果：0回答错误，1回答正确，NULL尚未确认',
    `retry_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已执行的自动重试次数，不含首次执行',
    `max_retry_count` INT UNSIGNED NOT NULL DEFAULT 3 COMMENT '首次执行失败后最多自动重试次数',
    `next_retry_time` DATETIME NOT NULL COMMENT '下次允许执行时间',
    `processing_time` DATETIME NULL DEFAULT NULL COMMENT '最近一次开始处理时间',
    `initiator_id` BIGINT NOT NULL COMMENT '发起复测的管理员ID',
    `reviewer_id` BIGINT NULL DEFAULT NULL COMMENT '确认回答正确或错误的管理员ID',
    `review_time` DATETIME NULL DEFAULT NULL COMMENT '人工确认时间',
    `last_error` VARCHAR(500) NULL DEFAULT NULL COMMENT '最近一次内部失败摘要',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_ai_feedback_retest_feedback` (`feedback_id`, `create_time`, `id`),
    KEY `idx_ai_feedback_retest_pending` (`execution_status`, `next_retry_time`, `id`),
    KEY `idx_ai_feedback_retest_processing` (`execution_status`, `processing_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI未解决评价复测记录';
