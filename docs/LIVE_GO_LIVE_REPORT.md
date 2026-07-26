# 五金商城 — 上线验收报告

> 域名: `qianchengsuoju.cn`
> 执行人: ____________
> 日期: ____________

## 基础设施

- [ ] 云服务器 2c2g 就绪，Ubuntu 22.04+
- [ ] ufw 防火墙仅放行 22/80/443
- [ ] MySQL 8.0 运行 (`systemctl is-active mysql`)
- [ ] Redis 7 运行 (`systemctl is-active redis-server`)
- [ ] docker compose 启动 (`docker compose -f docker-compose.prod.yml ps`，hw-backend + hw-admin + hw-redis 均为 Up)
- [ ] deploy 用户已配置 SSH key（GitHub Actions 可免密连接）

## 域名 & HTTPS

- [ ] DNS A 记录 `shop.qianchengsuoju.cn` → 服务器 IP
- [ ] DNS A 记录 `admin.qianchengsuoju.cn` → 服务器 IP
- [ ] Let's Encrypt 证书签发（`issue-certs.sh` 执行成功）
- [ ] `curl https://shop.qianchengsuoju.cn/api/user/product/list` 返回 `{"code":200}`
- [ ] `curl -I https://admin.qianchengsuoju.cn/` 返回 `401 Unauthorized`（Basic Auth 生效）
- [ ] `curl -u malladmin:<P1密码> https://admin.qianchengsuoju.cn/` 返回 `200 OK`
- [ ] certbot 续期 cron 已配置（`0 3 * * 1 renew-certs.sh`）

## 微信配置

- [ ] 小程序 request 合法域名已添加 `https://shop.qianchengsuoju.cn`
- [ ] 微信支付商户平台授权目录已添加 `shop.qianchengsuoju.cn`
- [ ] `.env` 中 `WECHAT_PAY_NOTIFY_URL=https://shop.qianchengsuoju.cn/api/user/pay/callback`
- [ ] `.env` 中 `CORS_ALLOWED_ORIGINS=https://admin.qianchengsuoju.cn`

## 安全

- [ ] `/etc/nginx/.htpasswd` 存在，用户名 `malladmin`
- [ ] admin Basic Auth 密码 P1 ≠ 后端 ADMIN_PASSWORD P2
- [ ] nginx `limit_req` 生效（连续 6 次无密码 curl admin → 503）
- [ ] 后端登录限流生效（连续 6 次错误密码 → 429）
- [ ] MySQL bind 127.0.0.1（`sudo netstat -tlnp | grep 3306`）
- [ ] Redis bind 127.0.0.1 + requirepass（`redis-cli -a <密码> ping`）

## 业务验收

### 用户端（小程序）

- [ ] 打开小程序，商品列表正常加载
- [ ] 点击商品 → 详情页正常
- [ ] 加入购物车 → 结算
- [ ] 提交订单（生成订单号）
- [ ] 微信支付拉起 → 支付成功 → 订单状态变为待发货
- [ ] 订单列表页、订单详情页正常

### 管理后台

- [ ] 浏览器打开 `https://admin.qianchengsuoju.cn`
- [ ] Basic Auth 弹窗 → 输入 malladmin 密码 → 进入登录页
- [ ] admin 账号登录成功 → dashboard
- [ ] 订单列表 → 订单详情 → 发货（填物流单号）
- [ ] 订单列表 → 退款审核 → 同意退款
- [ ] 退款回调 → 订单状态变为已退款

### 退款流程

- [ ] 用户申请退款 → 管理员同意 → PaymentRecord REFUNDING(4)
- [ ] 微信退款回调 → PaymentRecord REFUNDED(3) + Order REFUNDED(7)
- [ ] 用户收到退款

### 自动取消 + 兜底退款

- [ ] 待支付订单超时 → 自动取消 → 库存恢复
- [ ] 已取消订单收到支付回调 → 自动退款（钉钉告警触发）

## 备份 & 恢复

- [ ] `scripts/backup/db-daily-backup.sh` 手工执行成功（生成 .sql.gz）
- [ ] 恢复测试：`gunzip < 备份文件 | mysql -u... hardware_mall_test`
- [ ] 备份 cron 已配置（`30 2 * * *`）
- [ ] OSS 冷备脚本可执行（如有 OSS 配置）

## 可观测性

- [ ] `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`
- [ ] `curl http://localhost:8080/actuator/prometheus` 返回 metrics
- [ ] 日志含 traceId（`docker logs hw-backend | grep '\[.*\]'`）
- [ ] 钉钉告警：手动触发异常 → 收到钉钉通知

## 性能

- [ ] 首页商品列表响应 < 500ms
- [ ] 下单接口响应 < 2s
- [ ] 支付回调响应 < 1s
- [ ] 服务器资源：`free -m` 内存使用 < 1.5GB

## 发布清单

- [ ] GitHub `main` 分支为最新版本
- [ ] `.env` 已配齐（`chmod 600`）
- [ ] `docker compose -f docker-compose.prod.yml up -d --build` 成功
- [ ] 小程序代码已上传体验版 → 扫码验收通过 → 提交审核

---

**验收结论：** ⬜ 通过 / ⬜ 不通过

**遗留问题：**（无则写无）
