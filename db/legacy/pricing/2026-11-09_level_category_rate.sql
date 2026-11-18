-- 等级分类倍率命名迁移（MySQL 8）
-- 前置条件：数据库已经应用 migration_2026-11-02_multi_level_pricing_release.sql。
-- 执行方式：mysql --default-character-set=utf8mb4 <database> < migration_2026-11-09_level_category_rate.sql
-- 执行前必须完成全库备份。本脚本不修改 sku.price 或 spu.original_price。

SET NAMES utf8mb4;
SET SESSION lock_wait_timeout = 30;

DELIMITER $$

DROP PROCEDURE IF EXISTS `pricing_rate_rename_preflight`$$

CREATE PROCEDURE `pricing_rate_rename_preflight`()
BEGIN
    DECLARE required_tables INT DEFAULT 0;
    DECLARE required_columns INT DEFAULT 0;
    DECLARE legacy_tables INT DEFAULT 0;
    DECLARE legacy_columns INT DEFAULT 0;
    DECLARE target_tables INT DEFAULT 0;
    DECLARE target_columns INT DEFAULT 0;
    DECLARE legacy_foreign_keys INT DEFAULT 0;

    SELECT COUNT(*)
      INTO required_tables
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name IN ('user_level', 'level_category_discount', 'spu', 'sku', 'order_item');

    SELECT COUNT(*)
      INTO required_columns
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND (
            (table_name = 'level_category_discount'
             AND column_name IN ('id', 'level_id', 'category_id', 'rate', 'create_time', 'update_time'))
         OR (table_name = 'spu' AND column_name IN ('id', 'original_price'))
         OR (table_name = 'sku' AND column_name IN ('id', 'price'))
         OR (table_name = 'order_item'
             AND column_name IN ('id', 'level_id', 'level_name', 'discount_rate'))
       );

    IF required_tables <> 5 OR required_columns <> 14 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '2026-11-02 pricing schema is incomplete; stop migration';
    END IF;

    SELECT COUNT(*)
      INTO legacy_tables
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name = 'level_category_discount';

    SELECT COUNT(*)
      INTO target_tables
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name = 'level_category_rate';

    SELECT COUNT(*)
      INTO legacy_columns
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'order_item'
       AND column_name = 'discount_rate';

    SELECT COUNT(*)
      INTO target_columns
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'order_item'
       AND column_name = 'pricing_rate';

    IF legacy_tables <> 1 OR target_tables <> 0
       OR legacy_columns <> 1 OR target_columns <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'pricing schema is partial or already migrated; stop migration';
    END IF;

    SELECT COUNT(*)
      INTO legacy_foreign_keys
      FROM information_schema.table_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 'level_category_discount'
       AND constraint_type = 'FOREIGN KEY'
       AND constraint_name IN ('fk_lcd_level', 'fk_lcd_category');

    IF legacy_foreign_keys <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'legacy pricing foreign keys are incomplete; stop migration';
    END IF;
END$$

DELIMITER ;

CALL `pricing_rate_rename_preflight`();
DROP PROCEDURE `pricing_rate_rename_preflight`;

ALTER TABLE `level_category_discount`
    DROP FOREIGN KEY `fk_lcd_level`,
    DROP FOREIGN KEY `fk_lcd_category`;

RENAME TABLE `level_category_discount` TO `level_category_rate`;

ALTER TABLE `level_category_rate`
    ADD CONSTRAINT `fk_lcr_level`
        FOREIGN KEY (`level_id`) REFERENCES `user_level` (`id`),
    ADD CONSTRAINT `fk_lcr_category`
        FOREIGN KEY (`category_id`) REFERENCES `category` (`id`);

-- 旧倍率不换算，新表从空矩阵开始。
TRUNCATE TABLE `level_category_rate`;

ALTER TABLE `order_item`
    CHANGE COLUMN `discount_rate` `pricing_rate`
        DECIMAL(6,4) NULL COMMENT '下单时适用倍率快照';

SELECT 'PASS: renamed pricing schema; level_category_rate is empty' AS migration_result;
