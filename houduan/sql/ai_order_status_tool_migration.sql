-- AI客服按当前用户和订单状态查询最近进行中订单。
-- 联合索引先按user_id隔离用户，再按status过滤进行中状态，并辅助按order_time读取最近订单。
SET @canyun_schema = DATABASE();
SET @has_ai_order_status_index = (
    SELECT COUNT(1)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @canyun_schema
      AND TABLE_NAME = 'orders'
      AND INDEX_NAME = 'idx_orders_user_status_time'
);
SET @add_ai_order_status_index_sql = IF(
    @has_ai_order_status_index = 0,
    'ALTER TABLE `orders` ADD INDEX `idx_orders_user_status_time` (`user_id`, `status`, `order_time`)',
    'SELECT 1'
);
PREPARE add_ai_order_status_index_stmt FROM @add_ai_order_status_index_sql;
EXECUTE add_ai_order_status_index_stmt;
DEALLOCATE PREPARE add_ai_order_status_index_stmt;
