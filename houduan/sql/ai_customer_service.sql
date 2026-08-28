-- AI 客服会话。user_id 必须来自服务端 JWT 上下文，不能信任前端传入值。
CREATE TABLE IF NOT EXISTS `ai_chat_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
    `title` VARCHAR(100) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0已关闭，1进行中',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_time` DATETIME NOT NULL COMMENT '最近活动时间',
    PRIMARY KEY (`id`),
    KEY `idx_ai_chat_session_user` (`user_id`, `status`, `update_time`)
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

-- AI 客服权威知识。旧版本通过 status 停用，新版本使用相同 knowledge_key 和递增版本号新增。
CREATE TABLE IF NOT EXISTS `ai_knowledge` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '知识ID',
    `knowledge_key` VARCHAR(64) NOT NULL COMMENT '同一业务规则跨版本的稳定标识',
    `title` VARCHAR(100) NOT NULL COMMENT '知识标题',
    `category` VARCHAR(32) NOT NULL COMMENT '业务分类：ORDER、COUPON、DISH、DELIVERY等',
    `content` TEXT NOT NULL COMMENT '提供给模型的权威规则正文',
    `version_no` INT NOT NULL DEFAULT 1 COMMENT '版本号',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_knowledge_version` (`knowledge_key`, `version_no`),
    KEY `idx_ai_knowledge_retrieval` (`status`, `category`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服知识库';
