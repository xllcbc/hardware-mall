#!/usr/bin/env bash
set -e

DOMAIN_MAIN="qianchengsuoju.cn"
DOMAIN_SHOP="shop.${DOMAIN_MAIN}"
DOMAIN_ADMIN="admin.${DOMAIN_MAIN}"
CERT_DIR="/etc/nginx/certs"
EMAIL="${CERTBOT_EMAIL:-}"

if [ -z "$EMAIL" ]; then
  echo "错误: 请设置 CERTBOT_EMAIL 环境变量 (Let's Encrypt 通知用)"
  echo "用法: CERTBOT_EMAIL=you@example.com ./scripts/deploy/issue-certs.sh"
  exit 1
fi

if [ "$(id -u)" -ne 0 ]; then
  echo "错误: 请用 root 执行 (certbot 需 80 端口权限)"
  exit 1
fi

if ! command -v certbot >/dev/null 2>&1; then
  echo "安装 certbot..."
  apt update && apt install -y certbot
fi

mkdir -p "$CERT_DIR"

echo "[1/2] 签发 ${DOMAIN_SHOP} 证书..."
certbot certonly --standalone \
  --agree-tos --non-interactive \
  --email "$EMAIL" \
  -d "$DOMAIN_SHOP"

echo "[2/2] 签发 ${DOMAIN_ADMIN} 证书..."
certbot certonly --standalone \
  --agree-tos --non-interactive \
  --email "$EMAIL" \
  -d "$DOMAIN_ADMIN"

cp /etc/letsencrypt/live/${DOMAIN_SHOP}/fullchain.pem "${CERT_DIR}/shop.fullchain.pem"
cp /etc/letsencrypt/live/${DOMAIN_SHOP}/privkey.pem   "${CERT_DIR}/shop.privkey.pem"
cp /etc/letsencrypt/live/${DOMAIN_ADMIN}/fullchain.pem "${CERT_DIR}/admin.fullchain.pem"
cp /etc/letsencrypt/live/${DOMAIN_ADMIN}/privkey.pem   "${CERT_DIR}/admin.privkey.pem"

chmod 644 "${CERT_DIR}"/*.pem

echo "完成。证书位于 ${CERT_DIR}/"
ls -la "${CERT_DIR}/"
