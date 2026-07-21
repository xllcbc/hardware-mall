# Phase 7: 生产验收 + 备份恢复演练 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Phase 0-6 系统就绪后做端到端真实业务验收 + 监控告警有效性 + 备份恢复演练，最终产出上线就绪心报告。

**Architecture:** 全部为运维验收操作清单 + 一份 `docs/LIVE_GO_LIVE_REPORT.md` 报告。代码改动只一项：补 `@SpringBootTest` 启动校验测试（已有则跳过）。

**Tech Stack:** curl、docker compose、微信开发者工具、mysqldump

**前置约束：** Phase 0-6 与 Phase 6' 已全部合并并在生产服务器跑稳至少 24h。在 `phase7-final-checklist` 分支执行。

---

## 文件结构

- Create: `docs/LIVE_GO_LIVE_REPORT.md` — 最终上线就绪报告（所有验收 checkbox 状态 + 风险残留）
- Run only: `scripts/deploy/up.sh` / `down.sh` / `logs.sh`
- Run only: `scripts/backup/db-daily-backup.sh`
- 仅手工 curl / 浏览器 / 微信开发者工具操作

---

### Task 1: 准备分支

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git checkout main && git pull && git checkout -b phase7-final-checklist
```

---

### Task 2: 系统健康检查（方案 C：2 容器 + 宿主机 MySQL/Redis）

- [ ] **Step 1: 2 个容器 healthy + 宿主机服务 active**

```bash
ssh deploy@server
# 2 个容器（backend + admin）
docker compose -f /opt/hardware-mall/docker-compose.prod.yml ps
# 期望: hw-backend / hw-admin 两个容器 Up (healthy)

# 宿主机 systemctl 服务状态
systemctl is-active mysql redis-server
# 期望: 两行都输出 active
```

- [ ] **Step 2: 后端 actuator / health**

```bash
# actuator 不经 nginx 反代 (仅暴露给服务器内部), 直接走 localhost:8080 
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/actuator/prometheus | head -3
```

Expected: 第一行 `{"status":"UP"}`；第二行有 metric 输出。

经 nginx 反代的 API（更接近外部用户路径）:
```bash
curl -s https://shop.yourdomain.com/api/user/product/list | head -c 100
# 期望: {"code":200,...
```

- [ ] **Step 3: 数据库表与数据完整**

```bash
# 走宿主机 MySQL (非 docker exec)
mysql -uhardware_mall -p"$DB_PASSWORD" -h127.0.0.1 -e "use hardware_mall; show tables;" | wc -l
mysql -uhardware_mall -p"$DB_PASSWORD" -h127.0.0.1 -e "select count(*) from hardware_mall.spu;"
mysql -uhardware_mall -p"$DB_PASSWORD" -h127.0.0.1 -e "select count(*) from hardware_mall.category;"
```

Expected: ≥ 12 个表（与 init.sql 全等）；spu/category 至少 1 行。

- [ ] **Step 4: Redis 连接验证**

```bash
# 走宿主机 Redis
redis-cli -h 127.0.0.1 -a "$REDIS_PASSWORD" ping
redis-cli -h 127.0.0.1 -a "$REDIS_PASSWORD" dbsize
```

Expected: `PONG` 与 `≥ 0` keys。

- [ ] **Step 5: 磁盘 + 内存 + 日志（2g 服务器监督）**

```bash
df -h
free -h
docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}"
docker compose logs --tail=50 backend | grep -iE "error|exception" | head -10
ls -la /var/log/hardware-mall/
ls -la /var/log/mysql/slow.log 2>/dev/null
```

Expected:
- 磁盘剩余 > 5GB
- `free -h` available ≥ 300MB（2g 服务器留 15% 缓冲）
- docker stats backend MemUsage ≤ 500MB（JVM -Xmx384m + overhead）
- docker stats admin MemUsage ≤ 50MB
- 后端日志无 error/exception 异常行
- 日志文件在按日滚动

---

### Task 3: 端到端业务验收（必做）

> 每步在 `docs/LIVE_GO_LIVE_REPORT.md` 记录实际通过状态与观察到的异常。

- [ ] **Step 1: 用户登录微信小程序**

打开微信 → 搜索小程序 → 点击登录 → 后端日志含 `登录成功, openid=xxx`。
- [ ] **Step 2: 浏览商品**

- 首页 → 推荐商品列表加载
- 分类页 → 全部分类 → 点某分类 → 商品列表加载
- 搜索 → 输入关键词 → 出结果 → 滚到底触发 loadMore

- [ ] **Step 3: 商品详情 → 加入购物车 → 立即购买**

- 进入商品详情选规格 → 看价格/库存变化
- 加购 →购物车 cart icon 数字 +1
- 店铺直接购买 → 跳 checkout

- [ ] **Step 4: 下单 + 真实小额支付 0.01**

- 选地址 → 提交订单
- 调起微信支付 → 支付成功
- 跳订单详情页 → 状态=2 待发货
- 后端日志：`收到微信支付回调通知` + `processPaymentSuccess: order_xxx 1→2`

- [ ] **Step 5: 管理后台订单操作**

- admin 登录 → 订单管理 → 看到刚下的订单
- 发货 → 输入物流单号 → 状态 2→3
- 用户端小程序 → 我的订单 → 看到状态已发货

- [ ] **Step 6: 用户确认收货**

- 小程序订单详情 → 确认收货 → 状态 3→4

- [ ] **Step 7: 退款流程**

- 用一笔"待发货"或"已发货"订单管理后台发起退款
- 管理后台状态 → 6 退款中
- 后端日志：`微信退款受理成功`
- 等退款回调（实时或几分钟），看后端日志：`退款回调成功 payment_xxx REFUNDING→REFUNDED`，订单 6→7
- 如有失败，钉钉机器人应该收到告警

- [ ] **Step 8: 取消流程**

- 用户下一个待付款订单
- 用户在小程序点"取消订单"
- 状态 1→5；库存回滚；后端日志显示 `restoreStock` 与 `StockSyncEvent`

- [ ] **Step 9: 库存验证**

- 选一个库存=2 的 SKU，连续下两个订单后库存=0
- 第三个订单应返回"库存不足"
- Admin 编辑 SKU → 改库存 → 列表立即显示新库存（StockWarmupRunner 预热）

- [ ] **Step 10: 限流验证**

- 用 curl 暴击 `/api/user/login` 6 次/分钟
- 第 6 次 → 429
- 等 60s 后再调一次 → 200

- [ ] **Step 11: 多标签登出**

- admin 开两个 tab → A 标签登出 → B 标签点任意操作 → 主动跳 /login

- [ ] **Step 12: 跨标签 store 同步 (uniapp 类似适配)**

> 跳过 uniapp（生命周期内不存在"多标签")。

- [ ] **Step 13: 钉钉告警有效性**

- 手动触发一次 PayService 异常分支（如让一次微信查单失败模拟）
- 看钉钉机器人收到告警

> 这步骤可以在 dev 环境模拟；prod 不强制触发。

---

### Task 4: 备份与恢复演练

- [ ] **Step 1: 手工跑一次 db-daily-backup.sh**

```bash
ssh deploy@server
# .env 中已含 DB_PASSWORD, 通过 bash 加载
source /opt/hardware-mall/.env
DB_PASSWORD=$DB_PASSWORD /opt/hardware-mall/scripts/backup/db-daily-backup.sh
ls -lh /var/backups/hardware-mall/
```

Expected: 一个 `hardware_mall_YYYYMMDD_HHMMSS.sql.gz` 大小 > 0。

> 注：脚本内部用 `mysql -h 127.0.0.1` 访问宿主机 MySQL（方案 C，非容器）。

- [ ] **Step 2: 恢复演练**

```bash
# 走宿主机 MySQL (非 docker exec)
mysql -uroot -p"$DB_ROOT_PASSWORD" -h127.0.0.1 -e "CREATE DATABASE hardware_mall_restore_test;"
gunzip -c /var/backups/hardware-mall/hardware_mall_*.sql.gz | \
  mysql -uhardware_mall -p"$DB_PASSWORD" -h127.0.0.1 hardware_mall_restore_test
mysql -uhardware_mall -p"$DB_PASSWORD" -h127.0.0.1 -e "select count(*) from hardware_mall_restore_test.spu;"
mysql -uroot -p"$DB_ROOT_PASSWORD" -h127.0.0.1 -e "DROP DATABASE hardware_mall_restore_test;"
```

Expected: 恢复成功 + spu 数与生产一致。

- [ ] **Step 3: OSS 同步验证（如已开通）**

```bash
OSS_BUCKET=$OSS_BUCKET /opt/hardware-mall/scripts/backup/oss-sync.sh
# 用 ossutil ls 检查
ossutil ls oss://$OSS_BUCKET/backups/hardware-mall/
```

Expected: 看到刚才备份的文件已同步。

- [ ] **Step 4: cron 自动备份验证**

在 root cron 加入：
```bash
sudo crontab -l
# 应包含:
30 2 * * * /opt/hardware-mall/scripts/backup/db-daily-backup.sh >> /var/log/hardware-mall/backup.log 2>&1
0 3 * * * /opt/hardware-mall/scripts/backup/oss-sync.sh >> /var/log/hardware-mall/backup-oss.log 2>&1
```

如未配置，加进去：
```bash
( sudo crontab -l 2>/dev/null; echo "30 2 * * * /opt/hardware-mall/scripts/backup/db-daily-backup.sh >> /var/log/hardware-mall/backup.log 2>&1" ) | sudo crontab -
( sudo crontab -l 2>/dev/null; echo "0 3 * * * /opt/hardware-mall/scripts/backup/oss-sync.sh >> /var/log/hardware-mall/backup-oss.log 2>&1" ) | sudo crontab -
```

---

### Task 5: 监控与告警有效性

- [ ] **Step 1: 钉钉告警手工触发**

```bash
# ssh 到 server，进入容器手工改 log 改成 alert 然后触发
# 或者更简单：手工调用 DingTalkAlertService.alert (但无对外接口)
# 最简：跑 db-backup 时改个错密码，看日志走 catch 触发告警（如果 abckup 脚本没告警则手工)
```

更实际：触发 `PayService.queryWechatOrder` 异常态（后端微信配置故意写错 mch-id，调用一次预下单失败查单，看告警触发）。或直接在 prod 改一个 Nginx 安全头不加 → curl I 检查，看 يش十二月监控……> 此步可选，主要靠 Phase 5 的实现自身完整。

- [ ] **Step 2: Prometheus 抓取验证（如安装了）**

如果有 Prometheus container：
```bash
curl -s http://prometheus:9090/api/v1/targets | grep backend
```

Expected: backend target up，last scrape 5s 内。

> Prometheus 容器配置是本 Phase 不做的扩展，仅检查已配置情况下是否健康。

- [ ] **Step 3: 日志含 traceId**

```bash
docker exec hw-backend sh -c 'tail -20 /var/log/hardware-mall/application.log'
```

Expected: 每行日志 `[xxxxxxxx]` traceId 占位有值（即便空也证明 pattern 正常加载），但希望正常请求后能看到 8-16 字节 hex traceId。

---

### Task 6: 性能验收（可选）

- [ ] **Step 1: 商品列表响应时间**

```bash
for i in 1 2 3 4 5; do
  curl -s -o /dev/null -w "list: %{time_total}s\n" https://api.yourdomain.com/api/user/product/list
done
ab -n 100 -c 5 https://api.yourdomain.com/api/user/product/list
```

Expected: 平均延迟 < 500ms 在 2c4g 服务器上。

- [ ] **Step 2: 订单创建响应时间**

```bash
ab -n 20 -c 1 -p /tmp/order.json -T application/json -H "Authorization: Bearer ..." \
  https://api.yourdomain.com/api/user/order/create
```

Expected: 平均 < 800ms。

> 如性能不达标，转 Phase 5+ 进一步优化 DB 索引/缓存。

---

### Task 7: 编写 LIVE_GO_LIVE_REPORT.md

**Files:**
- Create: `docs/LIVE_GO_LIVE_REPORT.md`

- [ ] **Step 1: 创建报告模板**

Create: `docs/LIVE_GO_LIVE_REPORT.md`

```markdown
# 五金商城上线就绪报告

**验收日期：** YYYY-MM-DD
**验收人：** ____
**生产服务器：** api.yourdomain.com (IP x.x.x.x)
**当前 commit：** ____

## 1. 系统组成

| 组件 | 部署方式 | 版本 | 状态 |
|---|---|---|---|
| Backend | docker 容器 | Spring Boot 3.2.12 | Up (healthy) |
| Admin Nginx | docker 容器 | nginx:alpine | Up (healthy) |
| Uniapp | 微信小程序 | uni-app mp-weixin | 提交审核 |
| MySQL | apt 直装（宿主机） | 8.0 | systemctl active |
| Redis | apt 直装（宿主机） | 7.x | systemctl active |
| TLS | Let's Encrypt 两单域名 | shop.* + admin.* | 有效期至 YYYY-MM-DD |
| Basic Auth | nginx htpasswd | malladmin | OK |

## 2. 端到端业务验收

| 项目 | 状态 | 备注 |
|---|---|---|
| 用户登录 | ⬜ | |
| 商品浏览 | ⬜ | |
| 购物车下单 | ⬜ | |
| 真实支付 0.01 | ⬜ | |
| 后端日志含回调 | ⬜ | |
| admin 发货 | ⬜ | |
| 用户确认收货 | ⬜ | |
| 退款流程 | ⬜ | |
| 库存扣减与回滚 | ⬜ | |
| 限流生效 | ⬜ | |
| 多标签登出同步 | ⬜ | |
| 钉钉告警触发 | ⬜ | |

## 3. 备份恢复演练

| 项目 | 状态 | 备注 |
|---|---|---|
| 备份脚本手工触发 | ⬜ | |
| 恢复测试库 | ⬜ | |
| OSS 冷备 | ⬜ | |
| cron 配置 | ⬜ | |

## 4. 安全验收

| 项目 | 状态 | 备注 |
|---|---|---|
| HTTPS（两个子域名） | ⬜ | shop.* + admin.* |
| HSTS 1 年 (admin) | ⬜ | |
| JWT secret 32 字节 | ⬜ | |
| ADMIN_PASSWORD 强密码 P2 | ⬜ | 应用层 |
| Basic Auth 密码强 P1（与 P2 不同） | ⬜ | nginx 层，用户名 malladmin |
| Basic Auth 限流 5r/m | ⬜ | nginx limit_req |
| RateLimit 启用（后端注解） | ⬜ | |
| 防火墙仅 22/80/443 | ⬜ | ufw |
| MySQL/Redis bind 127.0.0.1 | ⬜ | 不对公网开放 |
| 越权 BUG 已修 | ⬜ | Phase 0 B1/B2 |

## 5. 可观测性

| 项目 | 状态 | 备注 |
|---|---|---|
| /actuator/health UP | ⬜ | |
| /actuator/prometheus 输出 | ⬜ | |
| 日志含 traceId | ⬜ | |
| 钉钉告警收到一次 | ⬜ | |

## 6. 性能（取样）

| 接口 | 平均响应时间 | 备注 |
|---|---|---|
| GET /api/user/product/list | ⬜ ms | |
| POST /api/user/order/create | ⬜ ms | |
| POST /api/user/pay/prepay | ⬜ ms | |

## 7. 已知遗留与缓解

（在此列出未做项目 + 缓解措施）

- BCrypt 密码哈希未做：自用项目+强 env 密码可接受
- 收藏/足迹未后端同步：用户确认延后
- Spring Boot 2.7 仍存在：已升 3.2
- ICP 备案完成后立即合并 Phase 6'

## 8. 上线决策

提交人：____
审核人：____
上线时间：YYYY-MM-DD HH:MM

签字（电子）：
```

- [ ] **Step 2: 填报告（验收时边跑边记）**

按 Task 3-6 的验收结果填上面 checkbox 与表格。

- [ ] **Step 3: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add docs/LIVE_GO_LIVE_REPORT.md
git commit -m "docs: Phase 7 上线就绪报告 (LIVE_GO_LIVE_REPORT)"
git push -u origin phase7-final-checklist
```

---

### Task 8: 合并所有 Phase 分支回 main

- [ ] **Step 1: 切回 main**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git checkout main
git pull
```

- [ ] **Step 2: merge 各 Phase 分支（按序）**

```bash
git merge --no-ff phase0-bug-fixes
git merge --no-ff phase1-sb-upgrade
git merge --no-ff phase2-engineering-pinia
git merge --no-ff phase3-containerization
git merge --no-ff phase4-tests
git merge --no-ff phase5-observability
git merge --no-ff phase6-cicd
git merge --no-ff phase6p-https-domain
git merge --no-ff phase7-final-checklist
```

每个 merge 解决冲突（预期少，因各 Phase 范围不同）。

- [ ] **Step 3: 跑端到端测试确保 merge 不破坏**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
mvn clean test -q
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-admin"
npm run build
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-uniapp"
npm run build:mp-weixin
```

Expected: 全绿。

- [ ] **Step 4: 推送 main**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git push origin main
```

GitHub Actions 自动触发 `backend-admin-deploy.yml` 部署到生产。

- [ ] **Step 5: 生产最终回归**

```bash
curl -s https://api.yourdomain.com/actuator/health
curl -s -X POST https://api.yourdomain.com/api/admin/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"ProductionStrongPwd"}'
```

Expected: 全 200。

- [ ] **Step 6: Phase 7 完成 checkpoint**

全部 Phase 0-7 上线工作完成，进入 Phase 8 微信小程序提审。

---

## Phase 7 验收清单

| Task | 内容 | 验收 | 状态 |
|---|---|---|---|
| 2 | 系统健康检查（方案 C） | 2 容器 healthy + systemctl active mysql/redis | ⬜ |
| 3 | 端到端 13 项业务 | 全⬜变✅ | ⬜ |
| 4 | 备份恢复演练 | 备份+恢复成功 (走 127.0.0.1 mysqldump) | ⬜ |
| 5 | 监控告警有效性 | 钉钉收到告警 | ⬜ |
| 6 | 性能验收 | 列表<500ms（2g 服务器） | ⬜ |
| 7 | 上线报告 | 报告完整 | ⬜ |
| 8 | 全部合并 main | merge & 部署成功 | ⬜ |

---

## Self-Review

- ✅ 端到端覆盖真实下单→支付→发货→退款全链路
- ✅ 备份与恢复演练保证数据丢失可恢复
- ✅ 安全 checkbox 全部可对照
- ✅ 上线就绪报告产出可签字归档
- ⚠️ 性能验收为可选 + 不达标时 Phase 7 后追加优化项目
- ⚠️ cron 配置和 certbot 续期 cron 都需在生产服务器一次性写入