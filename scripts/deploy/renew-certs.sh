#!/usr/bin/env bash
set -e

DOMAIN_MAIN="qianchengsuoju.cn"
DOMAIN_SHOP="shop.${DOMAIN_MAIN}"
DOMAIN_ADMIN="admin.${DOMAIN_MAIN}"
CERT_DIR="/etc/nginx/certs"

if [ "$(id -u)" -ne 0 ]; then
  echo "错误: 请用 root 执行"
  exit 1
fi

echo "[1/2] certbot renew..."
certbot renew --quiet

echo "[2/2] 同步证书到 nginx 挂载目录..."
cp /etc/letsencrypt/live/${DOMAIN_SHOP}/fullchain.pem "${CERT_DIR}/shop.fullchain.pem"
cp /etc/letsencrypt/live/${DOMAIN_SHOP}/privkey.pem   "${CERT_DIR}/shop.privkey.pem"
cp /etc/letsencrypt/live/${DOMAIN_ADMIN}/fullchain.pem "${CERT_DIR}/admin.fullchain.pem"
cp /etc/letsencrypt/live/${DOMAIN_ADMIN}/privkey.pem   "${CERT_DIR}/admin.privkey.pem"
chmod 644 "${CERT_DIR}"/*.pem

docker exec hw-admin nginx -s reload 2>/dev/null || echo "警告: hw-admin 容器未运行, 跳过 reload"

echo "续期完成。"
