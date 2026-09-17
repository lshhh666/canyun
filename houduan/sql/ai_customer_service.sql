-- AI 客服会话。user_id 必须来自服务端 JWT 上下文，不能信任前端传入值。
CREATE TABLE IF NOT EXISTS `ai_chat_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
    `title` VARCHAR(100) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0已关闭，1可以发送消息，2-AI正在回答',
    `processing_sequence_no` INT NULL DEFAULT NULL COMMENT '当前正在处理的用户提问顺序号，与会话ID共同标识本轮请求',
    `processing_deadline` DATETIME NULL DEFAULT NULL COMMENT '本轮处理截止时间，不是实际结束时间',
    `selected_order_id` BIGINT NULL DEFAULT NULL COMMENT '当前会话选中的订单ID，仅供后端关联追问',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_time` DATETIME NOT NULL COMMENT '最近活动时间',
    PRIMARY KEY (`id`),
    KEY `idx_ai_chat_session_user` (`user_id`, `status`, `update_time`),
    KEY `idx_ai_chat_session_user_recent` (`user_id`, `update_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服会话';

-- AI 客服消息。一行只保存一条消息，消息列表由同一 session_id 的多行记录组成。
CREATE TABLE IF NOT EXISTS `ai_chat_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `session_id` BIGINT NOT NULL COMMENT '所属会话ID',
    `role` VARCHAR(16) NOT NULL COMMENT '消息角色：USER或ASSISTANT',
    `content` TEXT NOT NULL COMMENT '消息正文',
    `sequence_no` INT NOT NULL COMMENT '会话内消息序号，从1开始',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_chat_message_sequence` (`session_id`, `sequence_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服消息';

CREATE TABLE IF NOT EXISTS `ai_chat_pending_action` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '待确认动作主键',
    `user_id` BIGINT NOT NULL COMMENT 'JWT对应的用户ID',
    `session_id` BIGINT NOT NULL COMMENT '动作所属AI会话ID',
    `action_type` TINYINT UNSIGNED NOT NULL COMMENT '动作类型：1-取消订单',
    `target_order_id` BIGINT NOT NULL COMMENT '创建动作时冻结的目标订单ID',
    `assistant_message_id` BIGINT NULL DEFAULT NULL COMMENT '生成确认按钮的客服消息ID，用于恢复历史按钮',
    `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1-等待确认，2-执行成功，3-拒绝，4-过期，5-被新动作替代',
    `expire_time` DATETIME NOT NULL COMMENT '确认截止时间',
    `result_message` VARCHAR(255) NULL COMMENT '幂等执行结果',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_action_assistant_message` (`assistant_message_id`),
    KEY `idx_ai_action_user_session_status` (`user_id`, `session_id`, `status`, `expire_time`),
    KEY `idx_ai_action_target_order` (`target_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服待确认敏感动作';

-- AI 客服会话级评价。同一会话只保留一条评价，可由用户重新选择后更新。
CREATE TABLE IF NOT EXISTS `ai_chat_feedback` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话评价ID',
    `session_id` BIGINT NOT NULL COMMENT '被评价的AI会话ID',
    `user_id` BIGINT NOT NULL COMMENT 'JWT对应的用户ID',
    `result` TINYINT UNSIGNED NOT NULL COMMENT '评价结果：1-有帮助，2-没解决',
    `handle_status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '处理状态：0待处理，1待复测，2已处理',
    `handler_id` BIGINT NULL DEFAULT NULL COMMENT '最终确认处理完成的管理员ID',
    `handle_time` DATETIME NULL DEFAULT NULL COMMENT '最终确认处理完成时间',
    `create_time` DATETIME NOT NULL COMMENT '首次评价时间',
    `update_time` DATETIME NOT NULL COMMENT '最近评价时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_chat_feedback_session` (`session_id`),
    KEY `idx_ai_chat_feedback_review` (`result`, `handle_status`, `update_time`, `id`),
    KEY `idx_ai_chat_feedback_user` (`user_id`, `update_time`),
    KEY `idx_ai_chat_feedback_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服会话评价';

-- AI 客服权威知识。旧版本通过 status 停用，新版本使用相同 knowledge_key 和递增版本号新增。
CREATE TABLE IF NOT EXISTS `ai_knowledge` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '知识ID',
    `knowledge_key` VARCHAR(64) NOT NULL COMMENT '同一业务规则跨版本的稳定标识',
    `title` VARCHAR(100) NOT NULL COMMENT '知识标题',
    `category` VARCHAR(32) NOT NULL COMMENT '业务分类：ORDER、COUPON、DISH、DELIVERY等',
    `content` TEXT NOT NULL COMMENT '提供给模型的权威规则正文',
    `embedding` JSON NULL COMMENT '由标题和规则正文生成的语义向量',
    `embedding_model` VARCHAR(32) NULL COMMENT '生成向量的模型名称',
    `embedding_dimensions` SMALLINT UNSIGNED NULL COMMENT '向量维度',
    `embedding_status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '向量状态：0待生成，1可用，2生成失败',
    `version_no` INT NOT NULL DEFAULT 1 COMMENT '版本号',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_knowledge_version` (`knowledge_key`, `version_no`),
    KEY `idx_ai_knowledge_retrieval` (`status`, `embedding_status`, `category`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服知识库';

CREATE TABLE IF NOT EXISTS `ai_knowledge_embedding_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '向量任务ID',
    `knowledge_id` BIGINT NOT NULL COMMENT '待生成向量的知识版本ID',
    `status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0待处理，1处理中，2成功，3失败，4旧版本作废',
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

CREATE TABLE IF NOT EXISTS `ai_feedback_knowledge_relation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `feedback_id` BIGINT NOT NULL COMMENT '没解决的评价ID',
    `knowledge_key` VARCHAR(64) NOT NULL COMMENT '关联知识的稳定业务标识',
    `operator_id` BIGINT NOT NULL COMMENT '建立关联的管理员ID',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_feedback_knowledge_relation` (`feedback_id`, `knowledge_key`),
    KEY `idx_ai_feedback_knowledge_key` (`knowledge_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI未解决评价与知识关联';

CREATE TABLE IF NOT EXISTS `ai_feedback_retest` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '复测ID',
    `feedback_id` BIGINT NOT NULL COMMENT '被复测的评价ID',
    `question` TEXT NOT NULL COMMENT '本次实际使用的问题快照，可与原问题不同',
    `answer` TEXT NULL COMMENT '模型成功生成的回答',
    `used_knowledge_ids` JSON NULL COMMENT '本次回答实际使用的知识版本ID快照',
    `execution_status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '执行状态：0待处理，1处理中，2成功，3失败，4评价变更后失效',
    `review_result` TINYINT UNSIGNED NULL DEFAULT NULL COMMENT '人工确认结果：0回答错误，1回答正确，NULL尚未确认',
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
