-- 多级用户定价正式发布迁移（MySQL 8）
-- 前置条件：production 原始 schema，无旧批发价字段，无部分应用的定价结构。
-- 执行方式：mysql --default-character-set=utf8mb4 <database> < migration_2026-11-02_multi_level_pricing_release.sql
-- 执行前必须完成全库备份；脚本不执行旧绝对批发价到分类倍率的数据转换。

DELIMITER $$

DROP PROCEDURE IF EXISTS `pricing_release_preflight`$$

CREATE PROCEDURE `pricing_release_preflight`()
BEGIN
    DECLARE required_tables INT DEFAULT 0;
    DECLARE required_columns INT DEFAULT 0;
    DECLARE legacy_fields INT DEFAULT 0;
    DECLARE target_structure INT DEFAULT 0;

    SELECT COUNT(*)
      INTO required_tables
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name IN ('user', 'category', 'spu', 'sku', 'shop_order', 'order_item');

    SELECT COUNT(*)
      INTO required_columns
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND (
            (table_name = 'user' AND column_name IN ('id', 'role'))
         OR (table_name = 'category' AND column_name = 'id')
         OR (table_name = 'spu' AND column_name IN ('id', 'category_id'))
         OR (table_name = 'sku' AND column_name IN ('id', 'spu_id', 'price'))
         OR (table_name = 'shop_order' AND column_name IN ('id', 'user_id'))
         OR (table_name = 'order_item' AND column_name IN ('id', 'order_id', 'sku_id', 'price'))
       );

    IF required_tables <> 6 OR required_columns <> 14 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'production original schema is incomplete; stop release';
    END IF;

    SELECT COUNT(*)
      INTO legacy_fields
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND (
            (table_name = 'user' AND column_name = 'user_group')
         OR (table_name = 'sku' AND column_name = 'wholesale_price')
         OR (table_name = 'order_item' AND column_name = 'price_type')
       );

    IF legacy_fields > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'legacy wholesale pricing fields detected; stop release';
    END IF;

    SELECT COUNT(*)
      INTO target_structure
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name IN ('user_level', 'level_category_discount');

    SELECT target_structure + COUNT(*)
      INTO target_structure
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND (
            (table_name = 'user' AND column_name = 'level_id')
         OR (table_name = 'spu' AND column_name = 'exclude_discount')
         OR (table_name = 'order_item' AND column_name IN ('level_id', 'level_name', 'discount_rate'))
       );

    IF target_structure > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'schema already contains target pricing schema; stop release';
    END IF;
END$$

DELIMITER ;

CALL `pricing_release_preflight`();
DROP PROCEDURE `pricing_release_preflight`;

CREATE TABLE `user_level` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '等级ID',
    `name` VARCHAR(50) NOT NULL COMMENT '等级名称',
    `is_retail` TINYINT NOT NULL DEFAULT 0 COMMENT '是否零售等级：1-是，0-否',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '等级状态：1-启用，0-停用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`),
    KEY `idx_status_sort` (`status`, `sort_order` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户等级表';

INSERT INTO `user_level` (`id`, `name`, `is_retail`, `sort_order`, `status`)
VALUES (1, '零售', 1, 100, 1);

CREATE TABLE `level_category_discount` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `level_id` BIGINT UNSIGNED NOT NULL COMMENT '用户等级ID',
    `category_id` BIGINT UNSIGNED NOT NULL COMMENT '商品分类ID',
    `rate` DECIMAL(6,4) NOT NULL COMMENT '倍率(0,1]',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_level_category` (`level_id`, `category_id`),
    KEY `idx_category_id` (`category_id`),
    CONSTRAINT `fk_lcd_level` FOREIGN KEY (`level_id`) REFERENCES `user_level` (`id`),
    CONSTRAINT `fk_lcd_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='等级分类倍率表';

ALTER TABLE `user`
    ADD COLUMN `level_id` BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '用户等级ID' AFTER `role`,
    ADD KEY `idx_level_id` (`level_id`),
    ADD CONSTRAINT `fk_user_level` FOREIGN KEY (`level_id`) REFERENCES `user_level` (`id`);

ALTER TABLE `spu`
    ADD COLUMN `exclude_discount` TINYINT NOT NULL DEFAULT 0 COMMENT '不参与折扣：1-是，0-否' AFTER `is_recommend`;

ALTER TABLE `order_item`
    ADD COLUMN `level_id` BIGINT UNSIGNED NULL COMMENT '下单时用户等级ID快照' AFTER `price`,
    ADD COLUMN `level_name` VARCHAR(50) NULL COMMENT '下单时用户等级名称快照' AFTER `level_id`,
    ADD COLUMN `discount_rate` DECIMAL(6,4) NULL COMMENT '下单时适用倍率快照' AFTER `level_name`;

-- 不插入任何等级分类倍率行。非零售等级由管理员创建并显式配置矩阵。
