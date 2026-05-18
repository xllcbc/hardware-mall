-- 五金商城系统数据库建表脚本 V2.1
-- 仅包含 DDL（建表）+ 管理员账号 + 物流方式
-- 执行方式: mysql -u root -p < init.sql
-- 真实商品数据请另执行: mysql -u root -p < seed_real_products.sql

CREATE DATABASE IF NOT EXISTS `hardware_mall`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `hardware_mall`;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `openid` VARCHAR(64) NOT NULL COMMENT '微信OpenID',
    `unionid` VARCHAR(64) DEFAULT NULL COMMENT '微信UnionID',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '用户昵称',
    `avatar_url` VARCHAR(255) DEFAULT NULL COMMENT '用户头像URL',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '用户手机号',
    `province` VARCHAR(50) DEFAULT NULL COMMENT '用户所在省份',
    `city` VARCHAR(50) DEFAULT NULL COMMENT '用户所在城市',
    `role` TINYINT NOT NULL DEFAULT 1 COMMENT '用户角色：1-普通用户，2-管理员',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '用户状态：1-正常，0-禁用',
    `last_login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
    `last_login_ip` VARCHAR(50) DEFAULT NULL COMMENT '最后登录IP地址',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_time` BIGINT DEFAULT 0 COMMENT '删除时间，软删除标识',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_openid` (`openid`),
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

-- 收货地址表
CREATE TABLE IF NOT EXISTS `address` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '地址ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `consignee` VARCHAR(50) NOT NULL COMMENT '收货人姓名',
    `phone` VARCHAR(20) NOT NULL COMMENT '收货人手机号',
    `province` VARCHAR(50) NOT NULL COMMENT '省份',
    `city` VARCHAR(50) NOT NULL COMMENT '城市',
    `district` VARCHAR(50) NOT NULL COMMENT '区县',
    `detail` VARCHAR(255) NOT NULL COMMENT '详细地址',
    `postal_code` VARCHAR(10) DEFAULT NULL COMMENT '邮政编码',
    `is_default` TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认地址：1-默认，0-非默认',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_time` BIGINT DEFAULT 0 COMMENT '删除时间，软删除标识',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_user_default` (`user_id`, `is_default`),
    CONSTRAINT `fk_address_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='收货地址表';

-- 商品分类表
CREATE TABLE IF NOT EXISTS `category` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父级分类ID，0表示顶级分类',
    `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
    `icon` VARCHAR(255) DEFAULT NULL COMMENT '分类图标URL或emoji',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重，数值越大越靠前',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '分类状态：1-启用，0-禁用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_time` BIGINT DEFAULT 0 COMMENT '删除时间，软删除标识',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_status_sort` (`status`, `sort_order` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品分类表';

-- SPU商品型号表
CREATE TABLE IF NOT EXISTS `spu` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '商品型号ID',
    `category_id` BIGINT UNSIGNED NOT NULL COMMENT '分类ID',
    `name` VARCHAR(100) NOT NULL COMMENT '商品名称(SPU)',
    `subtitle` VARCHAR(200) DEFAULT NULL COMMENT '商品副标题',
    `description` TEXT DEFAULT NULL COMMENT '商品详细描述，支持富文本',
    `images` JSON DEFAULT NULL COMMENT '商品图片列表，JSON数组格式',
    `original_price` DECIMAL(10,2) DEFAULT NULL COMMENT '商品原价',
    `weight` DECIMAL(10,2) DEFAULT NULL COMMENT '商品重量，单位：千克',
    `sales_count` INT NOT NULL DEFAULT 0 COMMENT '商品销量',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '商品状态：1-上架，0-下架',
    `is_recommend` TINYINT NOT NULL DEFAULT 0 COMMENT '是否推荐商品：1-推荐，0-非推荐',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_time` BIGINT DEFAULT 0 COMMENT '删除时间，软删除标识',
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`),
    KEY `idx_status_recommend` (`status`, `is_recommend`),
    KEY `idx_status_sales` (`status`, `sales_count` DESC),
    KEY `idx_name` (`name`),
    FULLTEXT KEY `ft_name` (`name`),
    CONSTRAINT `fk_spu_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品型号表(SPU)';

-- SKU具体规格表
CREATE TABLE IF NOT EXISTS `sku` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'SKU ID',
    `spu_id` BIGINT UNSIGNED NOT NULL COMMENT 'SPU ID',
    `specs` JSON NOT NULL COMMENT '规格组合 [{"templateId":1,"itemId":1,"name":"颜色","value":"金色"},...]',
    `price` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT 'SKU价格',
    `stock` INT NOT NULL DEFAULT 0 COMMENT 'SKU库存数量',
    `image` VARCHAR(255) DEFAULT NULL COMMENT 'SKU图片',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT 'SKU状态：1-启用，0-禁用',
    `spec_hash` VARCHAR(64) NOT NULL COMMENT '规格组合哈希值，用于快速查找',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_time` BIGINT DEFAULT 0 COMMENT '删除时间，软删除标识',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_spu_specs` (`spu_id`, `spec_hash`) COMMENT '同一SPU下规格组合唯一',
    KEY `idx_spu_id` (`spu_id`),
    KEY `idx_status` (`status`),
    KEY `idx_stock` (`stock`),
    CONSTRAINT `fk_sku_spu` FOREIGN KEY (`spu_id`) REFERENCES `spu` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品SKU表';

-- 规格模板表
CREATE TABLE IF NOT EXISTS `spec_template` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '规格模板ID',
    `category_id` BIGINT UNSIGNED NOT NULL COMMENT '分类ID',
    `name` VARCHAR(50) NOT NULL COMMENT '规格名称(如:颜色/尺寸/锁体大小)',
    `spec_type` TINYINT NOT NULL DEFAULT 1 COMMENT '规格类型:1-选择型,2-输入型',
    `is_required` TINYINT NOT NULL DEFAULT 1 COMMENT '是否必选:1-必选,0-可选',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_time` BIGINT DEFAULT 0 COMMENT '删除时间，软删除标识',
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_category_sort` (`category_id`, `sort_order`),
    CONSTRAINT `fk_spec_template_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='规格模板表';

-- 规格项表
CREATE TABLE IF NOT EXISTS `spec_item` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '规格项ID',
    `template_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模板ID',
    `value` VARCHAR(50) NOT NULL COMMENT '规格值(如:白色/50mm/金色)',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_time` BIGINT DEFAULT 0 COMMENT '删除时间，软删除标识',
    PRIMARY KEY (`id`),
    KEY `idx_template_id` (`template_id`),
    KEY `idx_template_sort` (`template_id`, `sort_order`),
    CONSTRAINT `fk_spec_item_template` FOREIGN KEY (`template_id`) REFERENCES `spec_template` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='规格项表';

-- 购物车表（以SKU为单位）
CREATE TABLE IF NOT EXISTS `cart` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '购物车ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `sku_id` BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID',
    `quantity` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '购买数量',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_time` BIGINT DEFAULT 0 COMMENT '删除时间，软删除标识',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_sku` (`user_id`, `sku_id`) COMMENT '同一用户同一SKU只允许一条记录',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_sku_id` (`sku_id`),
    CONSTRAINT `fk_cart_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_cart_sku` FOREIGN KEY (`sku_id`) REFERENCES `sku` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='购物车表';

-- 物流方式表
CREATE TABLE IF NOT EXISTS `logistics` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '物流ID',
    `name` VARCHAR(50) NOT NULL COMMENT '物流名称',
    `code` VARCHAR(50) NOT NULL COMMENT '物流编码',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '物流描述/备注',
    `contact` VARCHAR(50) DEFAULT NULL COMMENT '物流联系人',
    `phones` JSON DEFAULT NULL COMMENT '物流联系电话列表',
    `city` VARCHAR(50) DEFAULT NULL COMMENT '物流所在城市',
    `address` VARCHAR(255) DEFAULT NULL COMMENT '物流网点地址',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '物流状态：1-启用，0-禁用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_time` BIGINT DEFAULT 0 COMMENT '删除时间，软删除标识',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_status_sort` (`status`, `sort_order` DESC),
    KEY `idx_city` (`city`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='物流方式表';

-- 订单主表
CREATE TABLE IF NOT EXISTS `shop_order` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单编号',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `address_id` BIGINT UNSIGNED NOT NULL COMMENT '收货地址ID',
    `logistics_id` BIGINT UNSIGNED NOT NULL COMMENT '物流方式ID',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '订单状态：1-待付款，2-待发货，3-已发货，4-已完成，5-已取消，6-退款中，7-已退款',
    `total_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '订单总金额',
    `freight_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '运费金额',
    `pay_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '实付金额',
    `logistics_no` VARCHAR(50) DEFAULT NULL COMMENT '物流单号',
    `pay_time` DATETIME DEFAULT NULL COMMENT '支付时间',
    `ship_time` DATETIME DEFAULT NULL COMMENT '发货时间',
    `receive_time` DATETIME DEFAULT NULL COMMENT '收货时间',
    `cancel_time` DATETIME DEFAULT NULL COMMENT '取消时间',
    `cancel_reason` VARCHAR(255) DEFAULT NULL COMMENT '取消原因',
    `buyer_remark` VARCHAR(500) DEFAULT NULL COMMENT '买家备注',
    `admin_remark` VARCHAR(500) DEFAULT NULL COMMENT '管理员备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `admin_delete_time` BIGINT DEFAULT NULL COMMENT '管理员删除时间',
    `user_delete_time` BIGINT DEFAULT NULL COMMENT '用户删除时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_pay_time` (`pay_time`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_status_create` (`status`, `create_time` DESC),
    CONSTRAINT `fk_order_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_order_address` FOREIGN KEY (`address_id`) REFERENCES `address` (`id`),
    CONSTRAINT `fk_order_logistics` FOREIGN KEY (`logistics_id`) REFERENCES `logistics` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单主表';

-- 订单明细表
CREATE TABLE IF NOT EXISTS `order_item` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订单明细ID',
    `order_id` BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    `sku_id` BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID(下单时快照)',
    `spu_id` BIGINT UNSIGNED NOT NULL COMMENT 'SPU ID(下单时快照)',
    `product_name` VARCHAR(100) NOT NULL COMMENT '商品名称快照',
    `product_spec` VARCHAR(500) DEFAULT NULL COMMENT '商品规格快照(JSON)',
    `product_image` VARCHAR(255) DEFAULT NULL COMMENT '商品图片快照',
    `price` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '商品单价快照',
    `quantity` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '购买数量',
    `subtotal` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '小计金额',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_sku_id` (`sku_id`),
    CONSTRAINT `fk_order_item_order` FOREIGN KEY (`order_id`) REFERENCES `shop_order` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_order_item_sku` FOREIGN KEY (`sku_id`) REFERENCES `sku` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单明细表';

-- ==================== 初始化数据 ====================

-- 初始化管理员账号 (密码: admin123)
INSERT INTO `user` (`openid`, `nickname`, `role`, `status`) VALUES ('admin', '管理员', 2, 1);

-- 初始化物流数据
INSERT INTO `logistics` (`name`, `code`, `phones`, `sort_order`, `status`) VALUES ('德邦物流', 'debang', '["400-800-8888"]', 100, 1);
INSERT INTO `logistics` (`name`, `code`, `phones`, `sort_order`, `status`) VALUES ('顺心捷达', 'shunxin', '["400-900-9999"]', 90, 1);
INSERT INTO `logistics` (`name`, `code`, `phones`, `sort_order`, `status`) VALUES ('安能物流', 'anneng', '["400-700-7777"]', 80, 1);

-- 真实商品数据请执行: mysql -u root -p < db/seed_real_products.sql
