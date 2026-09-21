-- Run against the selected MySQL database before deploying the new generator.
-- Preflight: SELECT number, COUNT(*) FROM orders GROUP BY number HAVING COUNT(*) > 1;
-- Resolve any historical duplicates manually; this script never changes order rows.
SET @canyun_schema = DATABASE();
SET @has_orders_number_unique_index = (
    SELECT COUNT(1)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @canyun_schema
      AND TABLE_NAME = 'orders'
      AND INDEX_NAME = 'uk_orders_number'
      AND NON_UNIQUE = 0
      AND COLUMN_NAME = 'number'
      AND SEQ_IN_INDEX = 1
      AND (SELECT COUNT(1) FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = @canyun_schema
             AND TABLE_NAME = 'orders'
             AND INDEX_NAME = 'uk_orders_number') = 1
);
SET @add_orders_number_unique_index_sql = IF(
    @has_orders_number_unique_index = 0,
    'ALTER TABLE `orders` ADD UNIQUE INDEX `uk_orders_number` (`number`)',
    'SELECT 1'
);
PREPARE add_orders_number_unique_index_stmt FROM @add_orders_number_unique_index_sql;
EXECUTE add_orders_number_unique_index_stmt;
DEALLOCATE PREPARE add_orders_number_unique_index_stmt;
