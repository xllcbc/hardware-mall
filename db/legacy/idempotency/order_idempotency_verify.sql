-- 下单幂等记录表发布后只读验证（MySQL 8）
-- 成功输出：PASS: order idempotency schema

DELIMITER $$

DROP PROCEDURE IF EXISTS `order_idempotency_verify`$$

CREATE PROCEDURE `order_idempotency_verify`()
BEGIN
    DECLARE target_tables INT DEFAULT 0;
    DECLARE target_columns INT DEFAULT 0;
    DECLARE unique_key_columns INT DEFAULT 0;
    DECLARE state_index_columns INT DEFAULT 0;
    DECLARE request_hash_type VARCHAR(64) DEFAULT '';

    SELECT COUNT(*)
      INTO target_tables
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name = 'order_idempotency';

    SELECT COUNT(*)
      INTO target_columns
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'order_idempotency'
       AND column_name IN (
            'id', 'user_id', 'idem_key', 'request_hash',
            'state', 'owner_token', 'order_id', 'create_time', 'update_time');

    SELECT COUNT(*)
      INTO unique_key_columns
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'order_idempotency'
       AND index_name = 'uk_user_idem_key'
       AND non_unique = 0;

    SELECT COUNT(*)
      INTO state_index_columns
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'order_idempotency'
       AND index_name = 'idx_state_update'
       AND non_unique = 1;

    SELECT column_type
      INTO request_hash_type
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'order_idempotency'
       AND column_name = 'request_hash'
     LIMIT 1;

    IF target_tables <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'missing order_idempotency table';
    END IF;

    IF target_columns <> 9 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'missing order_idempotency columns';
    END IF;

    IF unique_key_columns <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'uk_user_idem_key is missing or incomplete';
    END IF;

    IF state_index_columns <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'idx_state_update is missing or incomplete';
    END IF;

    IF request_hash_type <> 'char(64)' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'request_hash must be CHAR(64)';
    END IF;
END$$

DELIMITER ;

CALL `order_idempotency_verify`();
DROP PROCEDURE `order_idempotency_verify`;

SELECT 'PASS: order idempotency schema' AS verification_result;
