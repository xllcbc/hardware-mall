# 五金商城域名命名约定

## 主域名

`qianchengsuoju.cn`（已 ICP 备案）

## 子域名

| 子域名 | 用途 | nginx server block | HTTPS 证书 |
|--------|------|-------------------|-----------|
| `shop.qianchengsuoju.cn` | 小程序后端 API | `nginx.conf` shop server | `/etc/nginx/certs/shop.fullchain.pem` + `shop.privkey.pem` |
| `admin.qianchengsuoju.cn` | 管理后台 SPA | `nginx.conf` admin server | `/etc/nginx/certs/admin.fullchain.pem` + `admin.privkey.pem` |

## DNS 记录

在云控制台配两条 **A 记录**：

| 主机记录 | 类型 | 值 |
|----------|------|----|
| `shop` | A | `<服务器公网 IP>` |
| `admin` | A | `<服务器公网 IP>` |

## 证书

- 签发方式：Let's Encrypt（certbot HTTP-01 standalone）
- 操作脚本：`scripts/deploy/issue-certs.sh`（签发）、`scripts/deploy/renew-certs.sh`（续期）
- 挂载：admin 容器 `-v /etc/nginx/certs:/etc/nginx/certs:ro`
- 续期 cron：`0 3 * * 1 /opt/hardware-mall/scripts/deploy/renew-certs.sh`

## 相关配置位置

| 项目 | 文件 |
|------|------|
| nginx SSL 证书路径 | `hardware-mall-admin/nginx.conf` |
| 微信支付 notify_url | `.env` 中 `WECHAT_PAY_NOTIFY_URL=https://shop.qianchengsuoju.cn/api/user/pay/callback` |
| 小程序合法域名 | 微信公众平台 → 开发管理 → request 合法域名 → `https://shop.qianchengsuoju.cn` |
| CORS allowed origins | `.env` 中 `CORS_ALLOWED_ORIGINS=https://admin.qianchengsuoju.cn` |
| admin Basic Auth | `/etc/nginx/.htpasswd`，用户名 `malladmin` |
