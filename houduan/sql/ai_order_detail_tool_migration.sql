-- AI客服订单详情工具迁移。
-- 1. 会话保存当前选中的订单主键，用于理解“这单”等追问。
-- 2. 订单保存商品金额和配送费快照，避免配置变化后历史账单发生变化。
SET @canyun_schema = DATABASE();

SET @canyun_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @canyun_schema AND TABLE_NAME = 'ai_chat_session'
             AND COLUMN_NAME = 'selected_order_id'),
    'SELECT 1',
    'ALTER TABLE `ai_chat_session` ADD COLUMN `selected_order_id` BIGINT NULL DEFAULT NULL COMMENT ''当前会话选中的订单ID，仅供后端关联追问'' AFTER `processing_deadline`'
);
PREPARE canyun_stmt FROM @canyun_sql;
EXECUTE canyun_stmt;
DEALLOCATE PREPARE canyun_stmt;

SET @canyun_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @canyun_schema AND TABLE_NAME = 'orders'
             AND COLUMN_NAME = 'goods_amount'),
    'SELECT 1',
    'ALTER TABLE `orders` ADD COLUMN `goods_amount` DECIMAL(10, 2) NULL COMMENT ''下单时商品金额快照'' AFTER `pack_amount`'
);
PREPARE canyun_stmt FROM @canyun_sql;
EXECUTE canyun_stmt;
DEALLOCATE PREPARE canyun_stmt;

SET @canyun_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @canyun_schema AND TABLE_NAME = 'orders'
             AND COLUMN_NAME = 'delivery_fee'),
    'SELECT 1',
    'ALTER TABLE `orders` ADD COLUMN `delivery_fee` DECIMAL(10, 2) NULL COMMENT ''下单时配送费快照'' AFTER `goods_amount`'
);
PREPARE canyun_stmt FROM @canyun_sql;
EXECUTE canyun_stmt;
DEALLOCATE PREPARE canyun_stmt;

-- 旧订单优先根据订单明细恢复商品金额，再根据历史总价恢复配送费。
-- original_amount迁移前为0的记录，以amount + discount_amount作为优惠前总价。
UPDATE orders o
LEFT JOIN (
    SELECT order_id, SUM(amount * number) AS detail_goods_amount
    FROM order_detail
    GROUP BY order_id
) d ON d.order_id = o.id
SET o.goods_amount = COALESCE(
        o.goods_amount,
        d.detail_goods_amount,
        GREATEST(COALESCE(NULLIF(o.original_amount, 0), o.amount + COALESCE(o.discount_amount, 0))
                 - COALESCE(o.pack_amount, 0), 0)
    )
WHERE o.goods_amount IS NULL;

UPDATE orders
SET delivery_fee = GREATEST(
        COALESCE(NULLIF(original_amount, 0), amount + COALESCE(discount_amount, 0))
        - COALESCE(goods_amount, 0)
        - COALESCE(pack_amount, 0),
        0
    )
WHERE delivery_fee IS NULL;

-- 更早创建的订单可能没有写入优惠前金额。详情回答必须使用完整账单快照，
-- 因此使用已经恢复出的商品、打包和配送金额补齐，而不是在Java里猜测。
UPDATE orders
SET original_amount = goods_amount + COALESCE(pack_amount, 0) + delivery_fee
WHERE original_amount IS NULL OR original_amount = 0;

-- 没有使用优惠券的旧订单可能没有写discount_amount，按优惠前金额与实付金额的差额恢复。
UPDATE orders
SET discount_amount = GREATEST(original_amount - amount, 0)
WHERE discount_amount IS NULL;

ALTER TABLE orders
    MODIFY COLUMN original_amount DECIMAL(10, 2) NOT NULL COMMENT '下单时优惠前总金额快照',
    MODIFY COLUMN discount_amount DECIMAL(10, 2) NOT NULL COMMENT '下单时优惠金额快照',
    MODIFY COLUMN goods_amount DECIMAL(10, 2) NOT NULL COMMENT '下单时商品金额快照',
    MODIFY COLUMN delivery_fee DECIMAL(10, 2) NOT NULL COMMENT '下单时配送费快照';
