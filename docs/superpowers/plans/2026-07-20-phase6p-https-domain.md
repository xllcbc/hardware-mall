# Phase 6': HTTPS + 域名 + 小程序白名单 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** ICP 备案通过后，配置 DNS 两个子域名解析 + Let's Encrypt 两张单域名证书（HTTP-01）+ nginx HTTPS 双子域名 + 小程序后台 request 合法域名白名单（`shop.yourdomain.com`）+ 微信支付 notify_url 切换正式域名 + Basic Auth htpasswd 生成（用户名 `malladmin`）+ certbot 自动续期 cron。

**Architecture:** 纯运维操作 + 1 个 nginx.conf 已在 Phase 3 配好（含双 server_name + Basic Auth + limit_req）。Let's Encrypt 两张单域名证书走最简单 `--standalone` HTTP-01 模式。

**Tech Stack:** certbot（apt），Let's Encrypt（HTTP-01），nginx（已在 admin 容器内），ufw，微信小程序后台，微信支付商户平台

**前置约束：** **ICP 备案已通过**。Phase 0-6 已 merge。在 `phase6p-https-domain` 分支执行。

---

### Task 1: 前置确认 + DNS 配置

- [ ] ICP 已通过（工信部备案查询验证）
- [ ] DNS 两条 A 记录已配：
  - `shop.yourdomain.com` → 生产服务器 IP
  - `admin.yourdomain.com` → 生产服务器 IP

```bash
dig +short shop.yourdomain.com
dig +short admin.yourdomain.com
# 期望: 都返回服务器 IP
```

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git checkout main && git pull && git checkout -b phase6p-https-domain
```

---

### Task 2: 域名命名约定文档

**Files:** Create `docs/DOMAIN_NAMING.md`

| 子域名 | 用途 | 防护 |
|---|---|---|
| `shop.yourdomain.com` | 小程序后端 API | HTTPS + nginx rate limit + 后端 JWT |
| `admin.yourdomain.com` | 管理端 SPA | HTTPS + nginx Basic Auth (用户 malladmin) + limit_req 5r/m + 后端 JWT + admin 限流 5/60s |

命名理由：`shop`/`admin` 业务对仗清晰，不与后端路径 `/api/user/` 字面冲突。一级域名 `yourdomain.com` 已备案，子域名免单独备案。

---

### Task 3: 申请 Let's Encrypt 两张单域名证书

**Files:** Create `scripts/deploy/issue-certs.sh`

```bash
#!/usr/bin/env bash
set -e
EMAIL="${1:?用法: $0 <email> <shop_domain> <admin_domain>}"
SHOP="${2}"; ADMIN="${3}"

docker compose -f /opt/hardware-mall/docker-compose.prod.yml stop admin || true

certbot certonly --standalone --non-interactive --agree-tos --email "$EMAIL" -d "$SHOP"
certbot certonly --standalone --non-interactive --agree-tos --email "$EMAIL" -d "$ADMIN"

mkdir -p /etc/nginx/certs
cp /etc/letsencrypt/live/$SHOP/fullchain.pem  /etc/nginx/certs/shop.fullchain.pem
cp /etc/letsencrypt/live/$SHOP/privkey.pem    /etc/nginx/certs/shop.privkey.pem
cp /etc/letsencrypt/live/$ADMIN/fullchain.pem /etc/nginx/certs/admin.fullchain.pem
cp /etc/letsencrypt/live/$ADMIN/privkey.pem   /etc/nginx/certs/admin.privkey.pem
chmod 644 /etc/nginx/certs/*.pem

docker compose -f /opt/hardware-mall/docker-compose.prod.yml start admin
echo "完成。证书已复制到 /etc/nginx/certs/"
```

执行：
```bash
chmod +x scripts/deploy/issue-certs.sh
# ssh deploy@server，执行脚本
sudo ./scripts/deploy/issue-certs.sh you@yourdomain.com shop.yourdomain.com admin.yourdomain.com
```

---

### Task 4: 生成 htpasswd (Basic Auth)

```bash
sudo apt install -y apache2-utils
sudo htpasswd -c /etc/nginx/.htpasswd malladmin
# 输入强密码 P1（≥16 位，与后端 admin 密码 P2 不同）
sudo chmod 644 /etc/nginx/.htpasswd
```

验证：
```bash
curl -I https://admin.yourdomain.com/
# 期望: 401

curl -I -u malladmin:YOUR_P1 https://admin.yourdomain.com/
# 期望: 200

curl -X POST https://admin.yourdomain.com/api/admin/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"YOUR_P2"}'
# 期望: {"code":200,...}  ← /api/ 不走 Basic Auth
```

---

### Task 5: 小程序后台配置 request 合法域名

微信公众平台 → 开发管理 → 开发设置 → 服务器域名：
- request 合法域名：`https://shop.yourdomain.com`
- uploadFile / downloadFile 域名如需要则加 OSS bucket 域名

---

### Task 6: 微信支付 notify_url 切换正式域名

```bash
# .env 或 .env.example 中
WECHAT_PAY_NOTIFY_URL=https://shop.yourdomain.com/api/user/pay/callback

# 重启后端
docker compose -f /opt/hardware-mall/docker-compose.prod.yml restart backend
```

---

### Task 7: certbot 自动续期 cron

**Files:** Create `scripts/deploy/renew-certs.sh`

```bash
#!/usr/bin/env bash
set -e
certbot renew --quiet
# 续签后同步到 nginx 挂载
cp /etc/letsencrypt/live/shop.yourdomain.com/fullchain.pem  /etc/nginx/certs/shop.fullchain.pem
cp /etc/letsencrypt/live/shop.yourdomain.com/privkey.pem    /etc/nginx/certs/shop.privkey.pem
cp /etc/letsencrypt/live/admin.yourdomain.com/fullchain.pem /etc/nginx/certs/admin.fullchain.pem
cp /etc/letsencrypt/live/admin.yourdomain.com/privkey.pem   /etc/nginx/certs/admin.privkey.pem
docker exec hw-admin nginx -s reload 2>/dev/null || docker compose -f /opt/hardware-mall/docker-compose.prod.yml restart admin
```

cron：
```cron
30 3 * * 1 /opt/hardware-mall/scripts/deploy/renew-certs.sh >> /var/log/hardware-mall/cert-renew.log 2>&1
```

---

### Task 8: 端到端 HTTPS 全验证

- `curl -s https://shop.yourdomain.com/api/user/product/list | head -c 100` → 200
- `curl -sI https://admin.yourdomain.com/` → 401
- `curl -sI -u malladmin:P1 https://admin.yourdomain.com/` → 200
- HTTP → HTTPS 重定向 → 301
- 微信开发者工具关掉"不校验合法域名"后请求 shop.* 成功
- 真实小额支付 0.01 → 支付成功 → 回调成功

---

## Phase 6' 验收清单

| Task | 内容 | 验收 | 状态 |
|---|---|---|---|
| 1 | ICP 通过 + DNS 两条 A 记录 | dig 返回 IP | ⬜ |
| 2 | 域名命名文档 | 文档存在 | ⬜ |
| 3 | LE 两张单域名证书签发 | cert 目录有 4 个 pem | ⬜ |
| 4 | htpasswd malladmin 用户 | htpasswd 存在 + 401/200 | ⬜ |
| 5 | nginx 配置校验 | curl 测试符合预期 | ⬜ |
| 6 | 小程序合法域名白名单 | dev 模式请求成功 | ⬜ |
| 7 | notify_url 切换 | 后端日志显示正式 URL | ⬜ |
| 8 | 微信商户平台授权目录 | 添加 + 回调可达 | ⬜ |
| 9 | certbot 续期 cron | dry-run 通过 | ⬜ |
| 10 | 端到端 HTTPS | 全部 curl 200 + 真实支付 | ⬜ |
