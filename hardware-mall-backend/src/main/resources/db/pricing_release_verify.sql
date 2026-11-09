-- 多级用户定价发布后只读验证（MySQL 8）
-- 成功输出：PASS: pricing release schema

DELIMITER $$

DROP PROCEDURE IF EXISTS `pricing_release_verify`$$

CREATE PROCEDURE `pricing_release_verify`()
BEGIN
    DECLARE target_tables INT DEFAULT 0;
    DECLARE target_columns INT DEFAULT 0;
    DECLARE legacy_fields INT DEFAULT 0;
    DECLARE legacy_pricing_structure INT DEFAULT 0;
    DECLARE retail_rows INT DEFAULT 0;
    DECLARE invalid_users INT DEFAULT 0;
    DECLARE foreign_keys INT DEFAULT 0;
    DECLARE rate_rows INT DEFAULT 0;
    DECLARE expect_empty_rate_table INT DEFAULT 0;

    SET expect_empty_rate_table = COALESCE(@expect_empty_level_category_rate, 0);

    SELECT COUNT(*)
      INTO target_tables
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name IN ('user_level', 'level_category_rate');

    SELECT COUNT(*)
      INTO target_columns
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND (
            (table_name = 'user' AND column_name = 'level_id')
         OR (table_name = 'spu' AND column_name = 'exclude_discount')
         OR (table_name = 'order_item' AND column_name IN ('level_id', 'level_name', 'pricing_rate'))
       );

    SELECT COUNT(*)
      INTO legacy_fields
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND (
            (table_name = 'user' AND column_name = 'user_group')
         OR (table_name = 'sku' AND column_name = 'wholesale_price')
         OR (table_name = 'order_item' AND column_name = 'price_type')
       );

    SELECT
        (SELECT COUNT(*) FROM information_schema.tables
          WHERE table_schema = DATABASE()
            AND table_name = 'level_category_discount')
        +
        (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name = 'order_item'
            AND column_name = 'discount_rate')
      INTO legacy_pricing_structure;

    SELECT COUNT(*)
      INTO retail_rows
      FROM user_level
     WHERE id = 1 AND name = '零售' AND is_retail = 1 AND status = 1;

    SELECT COUNT(*)
      INTO invalid_users
      FROM user u
      LEFT JOIN user_level l ON l.id = u.level_id
     WHERE u.level_id IS NULL OR l.id IS NULL;

    SELECT COUNT(*)
      INTO foreign_keys
      FROM information_schema.table_constraints
     WHERE constraint_schema = DATABASE()
       AND constraint_name IN ('fk_lcr_level', 'fk_lcr_category', 'fk_user_level')
       AND constraint_type = 'FOREIGN KEY';

    SELECT COUNT(*)
      INTO rate_rows
      FROM `level_category_rate`;

    IF target_tables <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'missing user_level or level_category_rate table';
    END IF;

    IF target_columns <> 5 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'missing target pricing columns';
    END IF;

    IF legacy_fields > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'legacy wholesale pricing fields still exist';
    END IF;

    IF legacy_pricing_structure > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'legacy pricing table or order item column still exists';
    END IF;

    IF retail_rows <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'retail level is missing or invalid';
    END IF;

    IF invalid_users > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'users contain invalid level_id values';
    END IF;

    IF foreign_keys <> 3 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pricing foreign keys are incomplete';
    END IF;

    IF expect_empty_rate_table = 1 AND rate_rows <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'level_category_rate must be empty after migration';
    END IF;
END$$

DELIMITER ;

CALL `pricing_release_verify`();
DROP PROCEDURE `pricing_release_verify`;

SELECT 'PASS: pricing release schema' AS verification_result;
