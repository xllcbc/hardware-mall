-- Local/test database only. Run after init.sql and product seed data.
-- This creates one reusable test user and address. The CSV generator can
-- repeat the row for 100-200 concurrent requests without hitting login limits.

SET @test_openid = 'jmeter_loadtest_user_001';
SET @test_user_id = NULL;
SELECT id INTO @test_user_id
FROM `user`
WHERE openid = @test_openid
LIMIT 1;

INSERT INTO `user` (openid, nickname, role, status)
SELECT @test_openid, 'JMeter Load Test User', 1, 1
WHERE @test_user_id IS NULL;

SELECT id INTO @test_user_id
FROM `user`
WHERE openid = @test_openid
LIMIT 1;

INSERT INTO `address`
    (user_id, consignee, phone, province, city, district, detail, postal_code, is_default)
SELECT @test_user_id, 'JMeter Test', '13800138000', 'Beijing', 'Beijing',
       'Chaoyang', 'JMeter load-test address', '100000', 1
WHERE NOT EXISTS (
    SELECT 1 FROM `address`
    WHERE user_id = @test_user_id AND detail = 'JMeter load-test address'
);

-- Select IDs for the next command. Pick an enabled SKU with enough stock and
-- an enabled logistics row from the output of these queries.
SELECT @test_user_id AS user_id;
SELECT id AS address_id
FROM `address`
WHERE user_id = @test_user_id AND detail = 'JMeter load-test address'
ORDER BY id DESC
LIMIT 1;
SELECT id AS sku_id, stock, status
FROM `sku`
WHERE status = 1
ORDER BY id
LIMIT 10;
SELECT id AS logistics_id, name, status
FROM `logistics`
WHERE status = 1
ORDER BY id
LIMIT 10;

-- After choosing a sku_id, reset its stock before each run:
-- UPDATE `sku` SET stock = 10000 WHERE id = <sku_id> AND status = 1;
