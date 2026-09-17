-- 批发商专属价: 新增用户分组、SKU 批发价、订单价格类型
ALTER TABLE `sku`        ADD COLUMN `wholesale_price` DECIMAL(10,2) NULL COMMENT '批发价, NULL=不设';
ALTER TABLE `user`       ADD COLUMN `user_group`      TINYINT NOT NULL DEFAULT 1 COMMENT '用户分组: 1-零售 2-批发商';
ALTER TABLE `order_item` ADD COLUMN `price_type`      TINYINT NOT NULL DEFAULT 1 COMMENT '价格类型: 1-零售价 2-批发价';
