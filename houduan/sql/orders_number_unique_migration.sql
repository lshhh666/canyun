-- Run against the selected MySQL database before deploying the new generator.
-- Preflight: SELECT number, COUNT(*) FROM orders GROUP BY number HAVING COUNT(*) > 1;
-- Resolve any historical duplicates manually; this script never changes order rows.
-- If uk_orders_number is occupied by a non-unique or composite index, use
-- uk_orders_number_unique. If both names are occupied by incompatible indexes,
-- the ADD UNIQUE INDEX fails; inspect and rename/drop the conflicting index first.
SET @canyun_schema = DATABASE();
SET @has_orders_number_unique_index = (
    SELECT COUNT(1)
    FROM information_schema.STATISTICS AS candidate
    WHERE candidate.TABLE_SCHEMA = @canyun_schema
      AND candidate.TABLE_NAME = 'orders'
      AND candidate.INDEX_NAME IN ('uk_orders_number', 'uk_orders_number_unique')
      AND candidate.NON_UNIQUE = 0
      AND candidate.COLUMN_NAME = 'number'
      AND candidate.SEQ_IN_INDEX = 1
      AND candidate.SUB_PART IS NULL
      AND (SELECT COUNT(1) FROM information_schema.STATISTICS AS member
           WHERE member.TABLE_SCHEMA = @canyun_schema
             AND member.TABLE_NAME = 'orders'
             AND member.INDEX_NAME = candidate.INDEX_NAME) = 1
);
SET @has_primary_orders_number_index = (
    SELECT COUNT(1) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @canyun_schema
      AND TABLE_NAME = 'orders'
      AND INDEX_NAME = 'uk_orders_number'
);
SET @add_orders_number_unique_index_sql = IF(
    @has_orders_number_unique_index = 0,
    IF(@has_primary_orders_number_index = 0,
       'ALTER TABLE `orders` ADD UNIQUE INDEX `uk_orders_number` (`number`)',
       'ALTER TABLE `orders` ADD UNIQUE INDEX `uk_orders_number_unique` (`number`)'),
    'SELECT 1'
);
PREPARE add_orders_number_unique_index_stmt FROM @add_orders_number_unique_index_sql;
EXECUTE add_orders_number_unique_index_stmt;
DEALLOCATE PREPARE add_orders_number_unique_index_stmt;
