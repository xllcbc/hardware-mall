# Phase 7: 生产验收 + 备份恢复演练 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Phase 0-6' 系统就绪后做端到端真实业务验收 + 监控告警有效性 + 备份恢复演练，最终产出上线就绪报告。

**Architecture:** 全部为运维验收操作清单 + 一份 `docs/LIVE_GO_LIVE_REPORT.md` 报告。

**前置约束：** Phase 0-6 与 Phase 6' 已全部合并并在生产服务器跑稳至少 24h。在 `phase7-final-checklist` 分支执行。

---

### Task 1: 准备分支

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git checkout main && git pull && git checkout -b phase7-final-checklist
```

---

### Task 2: 系统健康检查（方案 C：2 容器 + 宿主机 MySQL/Redis）

- [ ] **Step 1: 2 个容器 healthy + 宿主机服务 active**

```bash
ssh deploy@server
docker compose -f /opt/hardware-mall/docker-compose.prod.yml ps
# 期望: hw-backend / hw-admin 两个容器 Up (healthy)

systemctl is-active mysql redis-server
# 期望: 两行都输出 active
```

- [ ] **Step 2: 后端 actuator / health**

```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/actuator/prometheus | head -3
# 期望: {"status":"UP"}

curl -s https://shop.yourdomain.com/api/user/product/list | head -c 100
# 期望: {"code":200,...
```

- [ ] **Step 3: 数据库完整**

```bash
mysql -uhardware_mall -p"$DB_PASSWORD" -h127.0.0.1 -e "use hardware_mall; show tables;" | wc -l
# 期望: ≥ 12 个表
```

- [ ] **Step 4: Redis 连接**

```bash
redis-cli -h 127.0.0.1 -a "$REDIS_PASSWORD" ping
# 期望: PONG
```

- [ ] **Step 5: 内存磁盘**

```bash
df -h; free -h
docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}"
# free available ≥ 300MB; backend MemUsage ≤ 500MB
```

---

### Task 3: 端到端业务验收（13 项）

1. ✅ 用户登录微信小程序
2. ✅ 浏览商品（首页/分类/搜索分页）
3. ✅ 商品详情 → 加购/立即购买
4. ✅ 下单 + 真实小额支付 0.01 → 状态 1→2
5. ✅ admin Basic Auth 输入 P1 → 进入登录页 → 输 P2 → 后台
6. ✅ 管理后台订单列表 → 发货 → 状态 2→3
7. ✅ 用户确认收货 → 3→4
8. ✅ 退款流程（admin 发起 → 状态 6→7）
9. ✅ 取消订单 → 库存回滚
10. ✅ 库存扣减验证（扣到 0 再下单 → "库存不足"）
11. ✅ 限流验证（curl 暴击 6 次/分钟 → 429）
12. ✅ 多标签登出同步（admin A 标签登出 → B 标签自动跳 /login）
13. ✅ 钉钉告警有效性（手工触发一次 PayService 异常）

---

### Task 4: 备份与恢复演练

- [ ] **Step 1: 手工备份**

```bash
source /opt/hardware-mall/.env
DB_PASSWORD=$DB_PASSWORD /opt/hardware-mall/scripts/backup/db-daily-backup.sh
ls -lh /var/backups/hardware-mall/
# 期望: 一个 .sql.gz 文件大小 > 0
```

- [ ] **Step 2: 恢复演练**

```bash
mysql -uroot -p"$DB_ROOT_PASSWORD" -h127.0.0.1 -e "CREATE DATABASE hardware_mall_restore_test;"
gunzip -c /var/backups/hardware-mall/hardware_mall_*.sql.gz | \
  mysql -uhardware_mall -p"$DB_PASSWORD" -h127.0.0.1 hardware_mall_restore_test
mysql -uhardware_mall -p"$DB_PASSWORD" -h127.0.0.1 -e "select count(*) from hardware_mall_restore_test.spu;"
mysql -uroot -p"$DB_ROOT_PASSWORD" -h127.0.0.1 -e "DROP DATABASE hardware_mall_restore_test;"
# 期望: 恢复成功 + spu 数一致
```

- [ ] **Step 3: cron 自动备份验证**

```bash
sudo crontab -l | grep backup
# 期望: 含 db-daily-backup.sh 与 oss-sync.sh
```

---

### Task 5: 监控与告警有效性

- ✅ `/actuator/prometheus` 输出 metric line
- ✅ 日志含 `[traceId]` 占位
- ✅ 钉钉机器人收到过至少一次告警

---

### Task 6: 编写 LIVE_GO_LIVE_REPORT.md

**Files:** Create `docs/LIVE_GO_LIVE_REPORT.md`

报告含：系统组成（方案 C）、端到端 13 项验收、备份恢复演练、安全验收（HTTPS 两子域名 + Basic Auth malladmin + 防火墙 22/80/443 + MySQL/Redis bind 127.0.0.1 + 越权 BUG 已修）、可观测性、性能取样、已知遗留与缓解。

---

### Task 7: 合并所有 Phase 回 main

```bash
git checkout main
git merge --no-ff phase0-bug-fixes
git merge --no-ff phase1-sb-upgrade
git merge --no-ff phase2-engineering-pinia
git merge --no-ff phase3-containerization
git merge --no-ff phase4-tests
git merge --no-ff phase5-observability
git merge --no-ff phase6-cicd
git merge --no-ff phase6p-https-domain
git merge --no-ff phase7-final-checklist

# 跑端到端确认
mvn clean test -q && npm run build  # admin
npm run build:mp-weixin  # uniapp

git push origin main
```

---

## Phase 7 验收清单

| Task | 内容 | 验收 | 状态 |
|---|---|---|---|
| 2 | 系统健康检查（方案 C） | 2 容器 healthy + systemctl active mysql/redis | ⬜ |
| 3 | 端到端 13 项业务 | 全⬜变✅ | ⬜ |
| 4 | 备份恢复演练 | 备份+恢复成功 (127.0.0.1 mysqldump) | ⬜ |
| 5 | 监控告警有效性 | 钉钉收到告警 | ⬜ |
| 6 | 上线报告 | 报告完整 | ⬜ |
| 7 | 全部合并 main | merge & 部署成功 | ⬜ |
