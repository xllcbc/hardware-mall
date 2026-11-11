-- 下单幂等记录增量迁移（MySQL 8）
-- 前置条件：shop_order 已存在且 order_idempotency 尚不存在。
-- 执行方式：mysql --default-character-set=utf8mb4 <database> < migration_2026-11-11_order_idempotency.sql
-- 发布顺序：先执行本迁移，再启动引用新表的应用版本。旧应用不引用新表，回滚应用不需要删表。

SET NAMES utf8mb4;
SET SESSION lock_wait_timeout = 30;

DELIMITER $$

DROP PROCEDURE IF EXISTS `order_idempotency_preflight`$$

CREATE PROCEDURE `order_idempotency_preflight`()
BEGIN
    DECLARE order_tables INT DEFAULT 0;
    DECLARE idempotency_tables INT DEFAULT 0;

    SELECT COUNT(*)
      INTO order_tables
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name = 'shop_order';

    SELECT COUNT(*)
      INTO idempotency_tables
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name = 'order_idempotency';

    IF order_tables <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'shop_order table is missing; stop migration';
    END IF;

    IF idempotency_tables <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'order_idempotency already exists; stop migration';
    END IF;
END$$

DELIMITER ;

CALL `order_idempotency_preflight`();
DROP PROCEDURE `order_idempotency_preflight`;

CREATE TABLE `order_idempotency` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `idem_key` VARCHAR(128) NOT NULL COMMENT '客户端幂等键',
  `request_hash` CHAR(64) NOT NULL COMMENT '请求指纹 SHA-256',
  `state` VARCHAR(16) NOT NULL COMMENT 'PROCESSING/SUCCEEDED/FAILED',
  `owner_token` VARCHAR(64) NOT NULL COMMENT '本次请求持有者令牌',
  `order_id` BIGINT DEFAULT NULL COMMENT '成功后关联的订单ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_idem_key` (`user_id`, `idem_key`),
  KEY `idx_state_update` (`state`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='下单幂等记录表';

SELECT 'PASS: created order_idempotency' AS migration_result;
