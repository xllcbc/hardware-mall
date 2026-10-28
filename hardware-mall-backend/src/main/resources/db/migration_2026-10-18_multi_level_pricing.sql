-- 多级用户定价迁移：
-- 1. 兼容“原始结构（无 user_group/wholesale_price/price_type）”
-- 2. 兼容“feat/wholesale-pricing 结构（已有三列）”
-- 执行前必须备份数据库。

CREATE TABLE IF NOT EXISTS `user_level` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(50) NOT NULL,
    `is_retail` TINYINT NOT NULL DEFAULT 0,
    `sort_order` INT NOT NULL DEFAULT 0,
    `status` TINYINT NOT NULL DEFAULT 1,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`),
    KEY `idx_status_sort` (`status`, `sort_order` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户等级表';

INSERT INTO `user_level` (`id`, `name`, `is_retail`, `sort_order`, `status`)
VALUES (1, '零售', 1, 100, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `is_retail` = 1, `status` = 1;

CREATE TABLE IF NOT EXISTS `level_category_discount` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `level_id` BIGINT UNSIGNED NOT NULL,
    `category_id` BIGINT UNSIGNED NOT NULL,
    `rate` DECIMAL(6,4) NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_level_category` (`level_id`, `category_id`),
    KEY `idx_category_id` (`category_id`),
    CONSTRAINT `fk_lcd_level` FOREIGN KEY (`level_id`) REFERENCES `user_level` (`id`),
    CONSTRAINT `fk_lcd_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='等级分类倍率表';

SET @has_level_id := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'level_id'
);
SET @ddl := IF(@has_level_id > 0, 'SELECT 1',
    'ALTER TABLE `user` ADD COLUMN `level_id` BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''用户等级ID'' AFTER `role`');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_user_group := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'user_group'
);
SET @has_wholesale_level := (
    SELECT COUNT(*) FROM `user_level` WHERE `name` = '批发商'
);
SET @ddl := IF(@has_user_group > 0 AND @has_wholesale_level = 0,
    'INSERT INTO `user_level` (`name`, `is_retail`, `sort_order`, `status`) VALUES (''批发商'', 0, 90, 1)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @wholesale_level_id := (SELECT `id` FROM `user_level` WHERE `name` = '批发商' LIMIT 1);
SET @ddl := IF(@has_user_group > 0 AND @wholesale_level_id IS NOT NULL,
    CONCAT('UPDATE `user` SET `level_id` = ', @wholesale_level_id, ' WHERE `user_group` = 2'),
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@has_user_group > 0,
    'ALTER TABLE `user` DROP COLUMN `user_group`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_exclude_discount := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'spu' AND COLUMN_NAME = 'exclude_discount'
);
SET @ddl := IF(@has_exclude_discount > 0, 'SELECT 1',
    'ALTER TABLE `spu` ADD COLUMN `exclude_discount` TINYINT NOT NULL DEFAULT 0 COMMENT ''不参与折扣：1-是，0-否'' AFTER `is_recommend`');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_wholesale_price := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sku' AND COLUMN_NAME = 'wholesale_price'
);
SET @ddl := IF(@has_wholesale_price > 0,
    'ALTER TABLE `sku` DROP COLUMN `wholesale_price`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_level_snapshot := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_item' AND COLUMN_NAME = 'level_id'
);
SET @ddl := IF(@has_level_snapshot > 0, 'SELECT 1',
    'ALTER TABLE `order_item` ADD COLUMN `level_id` BIGINT UNSIGNED NULL COMMENT ''下单时用户等级ID快照'' AFTER `price`, ADD COLUMN `level_name` VARCHAR(50) NULL COMMENT ''下单时用户等级名称快照'' AFTER `level_id`, ADD COLUMN `discount_rate` DECIMAL(6,4) NULL COMMENT ''下单时适用倍率快照'' AFTER `level_name`');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_price_type := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_item' AND COLUMN_NAME = 'price_type'
);
SET @ddl := IF(@has_price_type > 0,
    'ALTER TABLE `order_item` DROP COLUMN `price_type`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@wholesale_level_id IS NOT NULL,
    CONCAT('INSERT INTO `level_category_discount` (`level_id`, `category_id`, `rate`) ',
           'SELECT ', @wholesale_level_id, ', c.`id`, 1.0000 FROM `category` c ',
           'WHERE c.`delete_time` = 0 AND NOT EXISTS (',
           'SELECT 1 FROM `level_category_discount` d ',
           'WHERE d.`level_id` = ', @wholesale_level_id, ' AND d.`category_id` = c.`id`)'),
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
