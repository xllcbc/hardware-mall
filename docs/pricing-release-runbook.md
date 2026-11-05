# 多级用户定价发布 Runbook

本文用于 MySQL 8 维护窗口。迁移只接受 production 原始 schema；发现旧批发价
字段、目标定价结构或关键表缺失时，脚本会在第一条 DDL 前中止。

## 0. 固定路径

```text
hardware-mall-backend/src/main/resources/db/migration_2026-11-02_multi_level_pricing_release.sql
hardware-mall-backend/src/main/resources/db/pricing_release_verify.sql
scripts/pricing-release.sh
scripts/release/smoke.sh
docs/release/pricing-release-evidence.md
```

脚本依赖本机 `mysql`、`mysqldump`、`sha256sum` 和 `redis-cli`。数据库与 Redis
连接参数通过环境变量提供，不要把密码写入命令历史或版本库：

```bash
export DB_HOST=127.0.0.1
export DB_PORT=3306
export DB_NAME=hardware_mall
export DB_USERNAME=release_operator
export MYSQL_PWD='<从密钥管理系统读取>'
export REDIS_HOST=127.0.0.1
export REDIS_PORT=6379
export REDIS_PASSWORD='<从密钥管理系统读取；无密码时留空>'
```

`DB_USERNAME` 需要备份、建临时库、DDL 和恢复权限。发布前确认备份盘剩余空间
至少为当前数据库大小的两倍。

## 0.1 CI 发布门禁

以下检查必须全部通过，`release-gate` 才允许作为合并和生产部署的前置条件：

- `backend-unit`：纯单测，不启动 Spring、不连接任何外部服务；
- `backend-h2`：H2 MySQL 兼容模式下的 Spring 集成测试，连接一次性 Redis；
- `backend-acceptance`：Testcontainers MySQL 迁移和真实 Redis 失效/TTL/防击穿测试；
- `admin-build`：管理端单测和生产构建；
- `uniapp-h5-build`：H5 单测和生产构建；
- `uniapp-mp-build`：微信小程序生产构建；
- `browser-e2e`：管理端价格视角/矩阵/深链/1280px 布局和 H5 结算价格链路。

生产部署使用不可变镜像标签，并在 GitHub `production` Environment 审批后执行。
禁止用本地构建结果绕过 `release-gate` 直接部署 main。

## 1. 停止写入并备份

先停止管理端、小程序下单入口和后端写入流量，只保留运维连接。禁止在写入
未停止时迁移。

```bash
./scripts/pricing-release.sh backup /secure-backups/pricing-2026-11-02.sql
```

命令必须输出备份 SHA-256。将备份和校验文件复制到与生产主机不同的存储位置，
并确认文件可读取：

```bash
sha256sum -c /secure-backups/pricing-2026-11-02.sql.sha256
```

## 2. 在隔离临时库执行预检和迁移

不要在生产库首次执行。使用最近一次 production schema 和脱敏数据恢复隔离
MySQL 8，然后依次执行：

```bash
./scripts/pricing-release.sh preflight /secure-backups/pricing-2026-11-02.sql
./scripts/pricing-release.sh verify
```

`preflight` 会创建 `hardware_mall_pricing_preflight`，恢复备份，再执行完整迁移。
成功条件：

- 脚本退出码为 0；
- 迁移没有输出 `legacy wholesale pricing fields detected`；
- `verify` 的最终输出为 `PASS: pricing release schema`；
- 迁移前后用户、分类、SPU、SKU 和订单行数一致；
- `level_category_discount` 行数为 0。

如果预检失败，停止发布。不得通过删除旧列、跳过预检或修改脚本来继续。

维护窗口开始前必须用同一份备份完成一次回滚演练：

```bash
./scripts/pricing-release.sh rollback-drill \
  /secure-backups/pricing-2026-11-02.sql \
  registry.example.com/hardware-mall/hardware-mall-backend:<previous-sha>
```

成功输出必须包含：

- `PASS: rollback backup restored`;
- `PASS: previous release image present`。

演练使用独立临时库，不得指向 production 库名。

## 3. Production 迁移

确认维护窗口、备份校验、隔离预检和回滚镜像均已就绪后执行：

```bash
./scripts/pricing-release.sh migrate \
  /secure-backups/pricing-2026-11-02.sql
./scripts/pricing-release.sh verify
```

迁移脚本会再次执行同一个 preflight。任何非零退出码都立即停止发布，不重试
DDL，不执行第二次迁移。

## 4. 发布后冒烟

只有数据库验证通过后才启动新版本后端、管理端和用户端。后端启动后先主动
删除旧定价缓存；`clean-cache` 会清理 `pricing:*`、`product:list:*`、
`product:detail:*`、`product:recommend:*`、`product:search:*`、
`product:cache:generation` 和 `sku:info:*`，但保留 `sku:stock:*`。
`clean-cache` 失败时必须停止发布，不能等待 TTL：

```bash
./scripts/pricing-release.sh clean-cache
```

至少验证：

1. 健康检查通过；
2. 访客和零售用户看到商品列表、详情价格；
3. 非零售用户的价格与分类倍率一致；
4. 倍率缺口按零售价兜底，管理端显示未配置；
5. 购物车小计和订单创建金额与商品页一致；
6. 新订单写入等级 ID、等级名称和倍率快照；
7. 修改倍率后新请求生效，旧订单金额不变；
8. 日志没有迁移、外键、缓存失效和价格异常。

自动冒烟可执行：

```bash
RELEASE_API_BASE=http://127.0.0.1:8080 \
RELEASE_ADMIN_BASE=http://127.0.0.1 \
./scripts/release/smoke.sh
```

人工步骤和签字统一记录在 `docs/release/pricing-release-evidence.md` 的当次发布
副本中，禁止只在聊天记录里确认。

## 5. 回滚

出现结构、价格、订单或缓存严重回归时，停止新版本和写入流量，然后恢复备份：

```bash
./scripts/pricing-release.sh restore \
  /secure-backups/pricing-2026-11-02.sql
```

恢复后部署上一版镜像，清理 Redis 中所有新旧商品与定价缓存，再执行旧版本
健康检查、登录、商品价格和订单创建冒烟。`sku:stock:*` 不清理，库存以数据库
恢复结果为准。

回滚完成后记录：

- 备份路径与 SHA-256；
- 恢复开始/结束时间；
- 恢复后的表行数与关键数据校验；
- 上一版镜像 SHA；
- 冒烟结果和遗留风险。

## 6. 人工验证 SQL

验证脚本只读，不修改数据库：

```bash
mysql --protocol=TCP --default-character-set=utf8mb4 \
  -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" "$DB_NAME" \
  < hardware-mall-backend/src/main/resources/db/pricing_release_verify.sql
```

必须看到 `PASS: pricing release schema`。任何 `SIGNAL` 错误都表示发布未通过。
