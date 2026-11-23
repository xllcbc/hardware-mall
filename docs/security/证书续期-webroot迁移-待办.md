# 待办：证书续期迁移到 webroot（上线后执行）

> 状态：待办
> 目标：消除"续期须停 nginx"的停机窗口，并让续期由系统级 timer 自动完成
> 当前方案：`scripts/deploy/renew-certs.sh`（standalone + 停 nginx）+ `check-cert-expiry.sh`（巡检兜底）

## 背景

证书最初用 `certbot certonly --standalone` 签发（`issue-certs.sh`）。standalone 认证器做 HTTP-01 校验时**必须独占 80 端口**，而 `hw-admin`（nginx）容器 `80:80` 常驻，因此续期前必须先停 nginx，续期后再拉回（`renew-certs.sh` 已实现 stop→renew→start）。

代价：
- 每次续期有短暂停机窗口（HTTP-01 期间 shop/admin 站点不可用）。
- 必须依赖**主机 root cron 手动触发**，cron 是手动配的、服务器重建会丢。

## 目标

改用 **webroot** 验证方式：nginx 直接服务 `.well-known/acme-challenge/` 目录，certbot 用 `--webroot` 签发/续期，**不再需要独占 80 端口、不再需要停 nginx**。之后由 certbot 自带的 `certbot.timer`（Ubuntu 安装 certbot 包时自动安装，每天自动跑两次）完成自动续期，彻底摆脱手动 cron 和停机窗口。

## 步骤

- [ ] **docker-compose.prod.yml**：给 `hw-admin` 增加 webroot 共享卷
  ```yaml
  volumes:
    - /etc/nginx/certs:/etc/nginx/certs:ro
    - /etc/nginx/.htpasswd:/etc/nginx/.htpasswd:ro
    - /var/www/certbot:/var/www/certbot:ro   # 新增
  ```
- [ ] **nginx.conf**：增加 ACME challenge 的 location 块
  ```nginx
  location ^~ /.well-known/acme-challenge/ {
      root /var/www/certbot;
  }
  ```
- [ ] **用 webroot 重签证书**（替换现有 standalone 证书）
  ```bash
  mkdir -p /var/www/certbot
  certbot certonly --webroot -w /var/www/certbot \
    -d shop.qianchengsuoju.cn -d admin.qianchengsuoju.cn
  ```
- [ ] **配置续期后 reload nginx**：写入 `/etc/letsencrypt/cli.ini`
  ```ini
  deploy-hook = docker exec hw-admin nginx -s reload
  ```
- [ ] **验证自动续期**：`systemctl status certbot.timer` 确认 active；手工跑一次 `certbot renew --dry-run` 确认无 80 端口冲突、`notAfter` 更新。
- [ ] **退役旧脚本**：删除 `renew-certs.sh` 的 stop/start 逻辑（或整个脚本改用 certbot timer）；`check-cert-expiry.sh` 保留作兜底巡检。
- [ ] **清理旧 cron**：移除 `crontab` 中手动加的 `renew-certs.sh` 条目（巡检条目保留）。

## 收益

- 续期全程无停机。
- 自动续期由系统级 `certbot.timer` 保证，不依赖手动 cron，服务器重建后 certbot 包自动带 timer，无遗忘风险。
- 续期失败仍由 `check-cert-expiry.sh` 的钉钉告警兜底。

## 注意

- 重签会生成新的证书文件，需同步把新证书 copy 到 `/etc/nginx/certs/` 后 `docker exec hw-admin nginx -s reload`。
- 若后续引入 CDN / 多机，webroot 需放在各机都能访问的共享存储；届时可再评估 DNS-01（阿里云 DNS API）方案。
