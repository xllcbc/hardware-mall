#!/usr/bin/env bash
set -e

cd "$(dirname "$0")/../.."

if [ ! -f .env ]; then
  echo "错误: .env 不存在, 请 cp .env.example .env 并填入真实值"
  exit 1
fi

# 依赖检查: MySQL/Redis 必须在宿主机上跑
if ! systemctl is-active --quiet mysql; then
  echo "错误: mysql 服务未运行, 请参考 docs/server-setup.md 安装与启动"
  exit 1
fi
if ! systemctl is-active --quiet redis-server; then
  echo "错误: redis-server 服务未运行, 请参考 docs/server-setup.md 安装与启动"
  exit 1
fi

# 证书目录检查
if [ ! -d /etc/nginx/certs ] || [ -z "$(ls /etc/nginx/certs 2>/dev/null)" ]; then
  echo "警告: /etc/nginx/certs 下未发现证书, 请先执行 Phase 6' certbot 签发"
fi

# htpasswd 检查
if [ ! -f /etc/nginx/.htpasswd ]; then
  echo "警告: /etc/nginx/.htpasswd 不存在, admin Basic Auth 会失效, 请参考 docs/server-setup.md 生成"
fi

echo "[1/2] 拉取最新代码..."
git pull --ff-only

echo "[2/2] 构建并启动..."
docker compose -f docker-compose.prod.yml up -d --build

echo "完成。状态:"
docker compose -f docker-compose.prod.yml ps
