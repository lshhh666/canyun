# 数据库脚本执行说明

这些脚本分为“全新安装”和“历史库升级”两条路径，不能混合执行。执行前先备份目标库，并确认已经选中正确的 MySQL 数据库。

## 全新安装

先导入项目原始基础业务表，再按下面顺序执行：

1. `coupon.sql`
2. `ai_customer_service.sql`
3. `ai_order_detail_tool_migration.sql`
4. `ai_order_status_tool_migration.sql`

`ai_customer_service.sql` 是当前 AI 模块的完整结构，已经包含会话、消息、待确认动作、评价、知识、向量任务、知识关联和复测表。全新安装不要再执行下方的历史增量脚本。

## 从旧版 AI 客服库升级

仅当数据库已经执行过仓库早期版本的 `ai_customer_service.sql` 时，按下面顺序逐个执行一次：

1. `ai_chat_processing_migration.sql`
2. `ai_pending_action_migration.sql`
3. `ai_chat_history_migration.sql`
4. `ai_chat_feedback_migration.sql`
5. `ai_chat_feedback_update_time_migration.sql`
6. `ai_chat_feedback_statistics_migration.sql`
7. `ai_knowledge_embedding_migration.sql`
8. `ai_feedback_resolution_migration.sql`
9. `ai_order_detail_tool_migration.sql`
10. `ai_order_status_tool_migration.sql`

向量迁移会先以可空字段接收历史知识，再由闭环迁移区分已有向量和待生成向量，并为缺失向量创建异步任务，因此不会要求历史数据临时伪造向量值。

多数历史增量脚本是一次性迁移，不保证重复执行成功。不要把“已执行过的增量脚本”当作启动脚本反复运行。

## 完成后核对

至少确认以下内容：

- `ai_chat_session` 包含 `processing_sequence_no`、`processing_deadline`、`selected_order_id`。
- `ai_chat_feedback` 包含处理状态、处理人和处理时间字段。
- `ai_knowledge` 的向量字段允许为空，并包含 `embedding_status`。
- `ai_knowledge_embedding_task`、`ai_feedback_knowledge_relation`、`ai_feedback_retest` 已创建。
- `orders` 包含 `original_amount`、`discount_amount`、`user_coupon_id`、`goods_amount`、`delivery_fee`。
