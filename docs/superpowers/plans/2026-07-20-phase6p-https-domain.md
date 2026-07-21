# Phase 6': HTTPS + 域名 + 小程序白名单 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** ICP 备案通过后，配置 DNS 两个子域名解析 + Let's Encrypt 两张单域名证书（HTTP-01）+ nginx HTTPS 双子域名 + 小程序后台 request 合法域名白名单（`shop.yourdomain.com`）+ 微信支付 notify_url 切换正式域名 + Basic Auth htpasswd 生成（用户名 `malladmin`）+ certbot 自动续期 cron。

**Architecture:** 纯运维操作 + 1 个 nginx.conf 已在 Phase 3 配好（含双 server_name + Basic Auth + limit_req）。本 Phase 主要在云控制台、生产服务器、微信小程序后台、微信支付商户平台之间切换操作。Let's Encrypt 两张单域名证书走最简单 `--standalone` HTTP-01 模式，签发后拷贝到 `/etc/nginx/certs/` 供 admin 容器挂载。

**Tech Stack:** certbot（apt），Let's Encrypt（HTTP-01），nginx（已在 admin 容器内配好），ufw，微信小程序后台，微信支付商户平台

**前置约束：** **ICP 备案已通过**（用户从 Phase 0 Day 0 启动，约 2-4 周后通过）。Phase 0-6 已 merge。本 Phase 在 `phase6p-https-domain` 分支执行。

---

## 文件结构（Phase 6' 涉及）

- Modify: `hardware-mall-admin/nginx.conf` — 已于 Phase 3 配好双子域名 + Basic Auth + limit_req，本 Phase 仅校验
- Modify: `.env`、`.env.example` — 把 `WECHAT_PAY_NOTIFY_URL` 改正式域名
- Modify: `docs/CI_CD.md`（如有需要更新 secrets 描述）
- Create: `scripts/deploy/issue-certs.sh` — 一次性签发两个子域名的 LE 证书
- Create: `scripts/deploy/renew-certs.sh` — 续期与挂载同步脚本
- Create: `docs/DOMAIN_NAMING.md` — 子域名命名约定文档

---

### Task 1: 前置确认 + 分支

- [ ] **Step 1: 确认 ICP 已通过**

登录工信部备案查询 `https://beian.miit.gov.cn/` 查询域名备案号，或登录云服务商控制台确认。**未通过则禁止开始本 Phase**。

- [ ] **Step 2: 切分支**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git checkout main && git pull && git checkout -b phase6p-https-domain
```

- [ ] **Step 3: 在云控制台配两条 A 记录**

登录阿里云域名控制台 → DNS 解析：

| 主机记录 | 记录类型 | 解析线 | 记录值 |
|---|---|---|---|
| shop | A | 默认 | `<生产服务器 IP>` |
| admin | A | 默认 | `<生产服务器 IP>` |

> TTL 600 秒。等待 10 分钟生效。

- [ ] **Step 4: 本地验证 DNS 生效**

```bash
dig +short shop.yourdomain.com
dig +short admin.yourdomain.com
# 或
nslookup shop.yourdomain.com
nslookup admin.yourdomain.com
```

Expected: 两条都返回生产服务器 IP。

---

### Task 2: 域名命名约定文档

**Files:**
- Create: `docs/DOMAIN_NAMING.md`

- [ ] **Step 1: 创建命名文档**

Create: `docs/DOMAIN_NAMING.md`

```markdown
# 域名命名约定

## 当前使用

| 子域名 | 用途 | 防护层 |
|---|---|---|
| `shop.yourdomain.com` | 小程序后端 API（uniapp 调用） | HTTPS + nginx rate limit + 后端 JWT |
| `admin.yourdomain.com` | 管理端 SPA（浏览器访问） | HTTPS + nginx Basic Auth (用户 malladmin) + nginx limit_req 5r/m + 后端 JWT + admin 登录限流 5/60s |

## 命名选择理由

- `shop` / `admin` 业务对仗清晰，不与后端路径 `/api/user/...` 字面重复
- 避免 `user.*` 与 `/api/user/` 字面冲突造成日志混淆
- 未来可扩展 `pay.*` `oss.*` 等子域名，不与现有命名打架

## 一级域名

仅 ICP 备案一次（一级域名 `yourdomain.com`）。所有子域名共用此备案，不需子域名各自备案。
```

- [ ] **Step 2: Commit**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add docs/DOMAIN_NAMING.md
git commit -m "docs: 域名命名约定 (shop/admin 子域名)"
```

---

### Task 3: 申请 Let's Encrypt 两张单域名证书

**Files:**
- Create: `scripts/deploy/issue-certs.sh`

- [ ] **Step 1: 在生产服务器装 certbot**

```bash
ssh deploy@server
sudo apt update && sudo apt install -y certbot apache2-utils
```

> 注：`apache2-utils` 已在 Phase 3 server-setup.md 要求安装；此处再强调一次，因为本 Phase 需要 `htpasswd`。

- [ ] **Step 2: 写证书签发脚本**

Create: `scripts/deploy/issue-certs.sh`

```bash
#!/usr/bin/env bash
# Let's Encrypt 两张单域名证书签发 (HTTP-01 standalone 模式)
# 用法: sudo ./scripts/deploy/issue-certs.sh you@yourdomain.com shop.yourdomain.com admin.yourdomain.com
set -e

EMAIL="${1:?用法: $0 <email> <shop_domain> <admin_domain>}"
SHOP_DOMAIN="${2:?缺 shop 域名}"
ADMIN_DOMAIN="${3:?缺 admin 域名}"

echo "[1/5] 暂时停 admin 容器释放 80 端口"
docker compose -f /opt/hardware-mall/docker-compose.prod.yml stop admin || true

echo "[2/5] 签发 ${SHOP_DOMAIN} 证书"
certbot certonly \
  --standalone \
  --non-interactive \
  --agree-tos \
  --email "$EMAIL" \
  -d "$SHOP_DOMAIN"

echo "[3/5] 签发 ${ADMIN_DOMAIN} 证书"
certbot certonly \
  --standalone \
  --non-interactive \
  --agree-tos \
  --email "$EMAIL" \
  -d "$ADMIN_DOMAIN"

echo "[4/5] 复制证书到 nginx 挂载目录"
mkdir -p /etc/nginx/certs
cp /etc/letsencrypt/live/$SHOP_DOMAIN/fullchain.pem  /etc/nginx/certs/shop.fullchain.pem
cp /etc/letsencrypt/live/$SHOP_DOMAIN/privkey.pem    /etc/nginx/certs/shop.privkey.pem
cp /etc/letsencrypt/live/$ADMIN_DOMAIN/fullchain.pem /etc/nginx/certs/admin.fullchain.pem
cp /etc/letsencrypt/live/$ADMIN_DOMAIN/privkey.pem   /etc/nginx/certs/admin.privkey.pem
chmod 644 /etc/nginx/certs/*.pem

echo "[5/5] 重启 admin 容器加载新证书"
docker compose -f /opt/hardware-mall/docker-compose.prod.yml start admin
sleep 5

echo "完成。"
echo " - shop 证书完整链: $(ls -l /etc/letsencrypt/live/$SHOP_DOMAIN/fullchain.pem)"
echo " - admin 证书完整链: $(ls -l /etc/letsencrypt/live/$ADMIN_DOMAIN/fullchain.pem)"
echo "续期 cron 已自动由 certbot install 配置 (/etc/cron.d/certbot), 但需校验 post-hook 重启 nginx"
```

- [ ] **Step 3: 加可执行权限**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
chmod +x scripts/deploy/issue-certs.sh
```

- [ ] **Step 4: 推送脚本到生产服务器后执行**

```bash
# ssh deploy@server
cd /opt/hardware-mall
git pull
sudo ./scripts/deploy/issue-certs.sh you@yourdomain.com shop.yourdomain.com admin.yourdomain.com
```

Expected: 看到 `Congratulations! Your certificate and chain have been saved` 两行 + `/etc/nginx/certs/` 下 4 个 pem 文件。

- [ ] **Step 5: Commit**

```bash
git add scripts/deploy/issue-certs.sh
git commit -m "ops: certbot 一次性签发两单域名 LE 证书 + 复制到 nginx 挂载"
git push
```

---

### Task 4: 生成 htpasswd (Basic Auth)

- [ ] **Step 1: 在生产服务器生成 htpasswd**

```bash
# ssh deploy@server
# 用户名故意不用 admin (避开常见撞库词)
sudo htpasswd -c /etc/nginx/.htpasswd malladmin
# 按提示输入强密码 P1 (≥ 16 位, 大小写 + 数字 + 符号)
# 与后端应用 admin 登录密码 P2 不同

sudo chmod 644 /etc/nginx/.htpasswd
sudo ls -l /etc/nginx/.htpasswd
# 期望: -rw-r--r-- ... .htpasswd
```

> 注：如 Phase 3 已生成（执行 server-setup.md 时已做），跳过本 Step，仅校验存在。

- [ ] **Step 2: 校验 admin 容器能读到 htpasswd**

```bash
docker exec hw-admin cat /etc/nginx/.htpasswd
# 期望: 输出 malladmin:$apr1$... 一行
```

- [ ] **Step 3: 验证 nginx 路径正确**

```bash
docker exec hw-admin nginx -t
# 期望: syntax is ok / configuration file test is successful
```

如报错，检查 docker-compose.prod.yml admin 服务是否含：
```yaml
volumes:
  - /etc/nginx/certs:/etc/nginx/certs:ro
  - /etc/nginx/.htpasswd:/etc/nginx/.htpasswd:ro
```

如缺失，Modify `hardware-mall-uniapp/../docker-compose.prod.yml`（实际是项目根 `docker-compose.prod.yml`），按 Phase 3 写法补。

- [ ] **Step 4: 重启 admin 使新挂载生效**

```bash
docker compose -f /opt/hardware-mall/docker-compose.prod.yml restart admin
sleep 5
curl -I https://admin.yourdomain.com/
# 期望: HTTP/2 401 + WWW-Authenticate: Basic realm="Admin Area"

curl -I -u malladmin:YOUR_P1 https://admin.yourdomain.com/
# 期望: HTTP/2 200 OK
```

---

### Task 5: nginx 配置最终校验

**Files:** 已在 Phase 3 配好，本 Task 仅校验

- [ ] **Step 1: admin 容器内 nginx -t**

```bash
docker exec hw-admin nginx -t
```

Expected: `syntax is ok`、`configuration file test is successful`。

- [ ] **Step 2: 端到端 HTTPS 访问**

```bash
# shop 子域: API 反代
curl -sI https://shop.yourdomain.com/api/user/product/list | head -5
# 期望: HTTP/2 200 + Content-Type: application/json

curl -s https://shop.yourdomain.com/api/user/product/list | head -c 200
# 期望: {"code":200,...}

# admin 子域: 80 强制跳 443
curl -sI http://admin.yourdomain.com/ | head -3
# 期望: HTTP/1.1 301 Moved Permanently + Location: https://...

# admin 子域: 443 走 Basic Auth
curl -sI https://admin.yourdomain.com/ | head -3
# 期望: HTTP/2 401

curl -sI -u malladmin:YOUR_P1 https://admin.yourdomain.com/ | head -3
# 期望: HTTP/2 200 + Content-Type: text/html

# admin 子域: /api/ 不走 Basic Auth (前端调登录接口时无需 Basic 头)
curl -sI -X POST https://admin.yourdomain.com/api/admin/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"YOUR_P2"}' | head -3
# 期望: HTTP/2 200 + Content-Type: application/json
```

---

### Task 6: 小程序后台配置 request 合法域名

**用户操作，非代码改动。**

- [ ] **Step 1: 登录微信公众平台 `https://mp.weixin.qq.com`**

小程序后台 → 开发管理 → 开发设置 → 服务器域名 → 修改

- [ ] **Step 2: 添加 request 合法域名**

`https://shop.yourdomain.com` 加到 request 合法域名列表。

> 必须是 HTTPS；不能是 IP；必须备案过；端口必须 443（默认）。

- [ ] **Step 3: 添加 uploadFile / downloadFile 域名（如用）**

如果前端直传 OSS，需要把阿里云 bucket 域名加到 uploadFile 合法域名。

- [ ] **Step 4: 测试小程序请求**

用 HBuilderX 或微信开发者工具运行 `hardware-mall-uniapp`，**关闭"不校验合法域名"开关**，确认能请求 `https://shop.yourdomain.com/api/...` 返回商品列表。

Expected: 首页商品列表加载成功，无"不在合法域名列表"错误。

---

### Task 7: 微信支付 notify_url 切换正式域名

**Files:**
- Modify `.env`（生产服务器）
- Modify `.env.example`（项目模板）

- [ ] **Step 1: 检查 application.yml notify-url 配置来源**

Run:
```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake/hardware-mall-backend"
grep -n "notify" src/main/resources/application.yml src/main/resources/application-prod.yml
```

确认 `notify-url: ${WECHAT_PAY_NOTIFY_URL:...}` 已 env 化。如未 env 化（仍硬编码），先 Modify 改成 `${WECHAT_PAY_NOTIFY_URL:}`。

- [ ] **Step 2: 改 .env.example 注释示例**

Modify `.env.example`（项目根目录，已在 Phase 0 提交）

定位 `WECHAT_PAY_NOTIFY_URL` 行，确认注释说明：
```
# 微信支付回调地址 (生产必须 HTTPS + 域名已备案 + 已配小程序合法域名)
# shop 子域名反代 /api/ 到后端, 后端在 /api/user/pay/callback 接收回调
WECHAT_PAY_NOTIFY_URL=https://shop.yourdomain.com/api/user/pay/callback
```

- [ ] **Step 3: 修改生产 .env**

```bash
# ssh deploy@server
cd /opt/hardware-mall
sudo -u deploy nano .env
# 把 WECHAT_PAY_NOTIFY_URL 改为:
# WECHAT_PAY_NOTIFY_URL=https://shop.yourdomain.com/api/user/pay/callback
```

- [ ] **Step 4: 重启后端**

```bash
docker compose -f docker-compose.prod.yml restart backend
sleep 15
docker compose -f docker-compose.prod.yml logs --tail=30 backend | grep -i "notify"
# 期望: 日志中能看到 notify-url 加载了正式域名
```

- [ ] **Step 5: Commit .env.example**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
git add .env.example
git commit -m "docs: WECHAT_PAY_NOTIFY_URL 改用 shop 子域名示例"
git push
```

---

### Task 8: 微信支付商户平台绑定

**用户操作，非代码改动。**

- [ ] **Step 1: 登录微信支付商户平台 `https://pay.weixin.qq.com`**

产品中心 → 产品中心 → JSAPI 支付 → 产品配置

- [ ] **Step 2: 支付授权目录添加**

加上 `https://shop.yourdomain.com/`（uni-app 小程序走 `uni.requestPayment`，授权目录以小程序后台为准，但商户平台仍需登记后端域名）

- [ ] **Step 3: 校验回调 URL 可达**

```bash
curl -sI -X POST https://shop.yourdomain.com/api/user/pay/callback
# 期望: HTTP/2 4xx (微信服务器会带签名, 普通请求会被后端拒绝, 但 200/401 都说明路径可达)
```

> 后端代码 `PayController.callback` 在收到无签名的请求时会返回非 200，这正是路径正确的表现。

---

### Task 9: certbot 自动续期 cron

**Files:**
- Create: `scripts/deploy/renew-certs.sh`

- [ ] **Step 1: 写续期后脚本**

Create: `scripts/deploy/renew-certs.sh`

```bash
#!/usr/bin/env bash
# certbot 续期任一证书后, 同步到 nginx 挂载目录并 reload
set -e

SHOP_DOMAIN="${SHOP_DOMAIN:-shop.yourdomain.com}"
ADMIN_DOMAIN="${ADMIN_DOMAIN:-admin.yourdomain.com}"

# 仅当证书剩余天数 < 30 时 certbot renew 才会真续; 否则 no-action
/usr/bin/certbot renew --quiet

# 同步最新证书到 nginx 挂载目录 (覆盖)
cp /etc/letsencrypt/live/$SHOP_DOMAIN/fullchain.pem  /etc/nginx/certs/shop.fullchain.pem
cp /etc/letsencrypt/live/$SHOP_DOMAIN/privkey.pem    /etc/nginx/certs/shop.privkey.pem
cp /etc/letsencrypt/live/$ADMIN_DOMAIN/fullchain.pem /etc/nginx/certs/admin.fullchain.pem
cp /etc/letsencrypt/live/$ADMIN_DOMAIN/privkey.pem   /etc/nginx/certs/admin.privkey.pem
chmod 644 /etc/nginx/certs/*.pem

# 让 admin 容器内 nginx reload 新证书 (不重启整个容器)
docker exec hw-admin nginx -s reload 2>/dev/null || \
docker compose -f /opt/hardware-mall/docker-compose.prod.yml restart admin

echo "$(date) [renew-certs] 续期检查完成"
```

- [ ] **Step 2: 加可执行权限**

```bash
workdir="/mnt/c/Users/xllcbc/Desktop/写着玩/mystoremake"
chmod +x scripts/deploy/renew-certs.sh
git add scripts/deploy/renew-certs.sh
git commit -m "ops: certbot 自动续期 + 同步到 nginx 挂载"
git push
```

- [ ] **Step 3: 配置 root cron**

```bash
# ssh deploy@server
sudo crontab -l 2>/dev/null > /tmp/cron-bak
# 每周凌晨 3:30 跑续期检查, 若 LE 证书剩余 < 30 天则续; 否则 no-op
( cat /tmp/cron-bak 2>/dev/null
  echo "30 3 * * 1 /opt/hardware-mall/scripts/deploy/renew-certs.sh >> /var/log/hardware-mall/cert-renew.log 2>&1"
) | sudo crontab -
sudo crontab -l | grep renew
# 期望: 显示 renew-certs.sh 那一行
rm /tmp/cron-bak
```

- [ ] **Step 4: 测试续期 dry-run**

```bash
sudo certbot renew --dry-run
# 期望: "Congratulations, all simulated renewals succeeded"
```

---

### Task 10: 端到端 HTTPS 全验证

- [ ] **Step 1: 后端 actuator/health（经 nginx 反代，但 nginx 未暴露 actuator）**

```bash
# nginx 仅反代 /api/, actuator 走 backend 直连 (服务器内)
curl -s http://localhost:8080/actuator/health
```

Expected: `{"status":"UP"}`。

- [ ] **Step 2: shop 子域名 API**

```bash
curl -s https://shop.yourdomain.com/api/user/product/list | head -c 200
```

Expected: `{"code":200,...}`。

- [ ] **Step 3: admin 子域名 Basic Auth**

```bash
curl -sI https://admin.yourdomain.com/
# 期望: 401

curl -sI -u malladmin:YOUR_P1 https://admin.yourdomain.com/
# 期望: 200

curl -s -u malladmin:YOUR_P1 -X POST https://admin.yourdomain.com/api/admin/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"YOUR_P2"}'
# 期望: {"code":200,"data":{"token":"..."}}
```

- [ ] **Step 4: HTTP → HTTPS 跳转**

```bash
curl -sI http://shop.yourdomain.com/api/user/product/list | head -3
curl -sI http://admin.yourdomain.com/ | head -3
# 期望: 两个都返回 301 + Location: https://...
```

- [ ] **Step 5: HSTS 头**

```bash
curl -sI https://shop.yourdomain.com/api/user/product/list | grep -i strict-transport
# 期望: strict-transport-security: max-age=31536000; includeSubDomains
# 注: shop server block 在 Phase 3 配置里没加 HSTS, 仅 admin 加了
# 如需 shop 也加 HSTS, Modify nginx.conf 在 shop server block 补 add_header 行
```

> 决策记录：shop 子域名是小程序 API，不暴露浏览器，可不加 HSTS。admin 必加。

- [ ] **Step 6: 真实小额支付（强烈推荐）**

- 小程序下单一个 0.01 元商品
- 调起微信支付 → 支付成功
- 跳订单详情页 → 状态=2 待发货
- 后端日志：`收到微信支付回调通知` + `processPaymentSuccess: order_xxx 1→2`

- [ ] **Step 7: 退款回测（推荐）**

- 管理后台 Basic Auth 输入 P1 → 进入 admin 登录页 → 输 P2
- 订单管理 → 选一笔"待发货"订单发起退款
- 状态 2→6 退款中
- 等几秒看后端日志：`退款回调成功 payment_xxx REFUNDING→REFUNDED`
- 订单状态 6→7 已退款

- [ ] **Step 8: Phase 6' 完成 checkpoint**

进入 Phase 7 生产验收。

---

## Phase 6' 验收清单

| Task | 内容 | 验收 | 状态 |
|---|---|---|---|
| 1 | ICP 通过 + DNS 两条 A 记录 | dig 返回 IP | ⬜ |
| 2 | 域名命名文档 | 文档存在 | ⬜ |
| 3 | LE 两张单域名证书签发 | cert 目录有 4 个 pem | ⬜ |
| 4 | htpasswd malladmin 用户 | htpasswd 存在 + 401/200 验证 | ⬜ |
| 5 | nginx 配置校验 | 4 个 curl 测试符合预期 | ⬜ |
| 6 | 小程序合法域名白名单 | dev 模式请求成功 | ⬜ |
| 7 | notify_url 切换 | 后端日志显示正式 URL | ⬜ |
| 8 | 微信商户平台授权目录 | 添加 + 回调可达 | ⬜ |
| 9 | certbot 续期 cron | dry-run 通过 | ⬜ |
| 10 | 端到端 HTTPS | 全部 curl 200 + 真实下单+支付+退款 | ⬜ |

---

## Self-Review

- ✅ 两张单域名证书各自独立，互不影响
- ✅ 续期 cron 含 certbot renew + 复制到 nginx 挂载 + nginx reload
- ✅ Basic Auth 用户名 `malladmin` 避开常见撞库字典
- ✅ /api/ 不走 Basic Auth，避免前后端混乱
- ✅ 小程序合法域名仅 shop 不 admin，符合 1 个一级域名备案覆盖所有子域
- ✅ notify_url 走 HTTPS + 备案域名，微信支付合规
- ⚠️ ufw 已在 Phase 3 server-setup 中放行 80/443，80 用于 LE 续期 + HTTP→HTTPS 重定向
- ⚠️ 80 端口 certbot 续期时会临时占用，admin 容器需 stop/start；脚本已处理
- ⚠️ 用户操作清单：DNS 控制台、小程序后台、商户平台绑定 — 各一次手工操作