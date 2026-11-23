#!/usr/bin/env bash
# 续期 Let's Encrypt 证书并同步到 Nginx 挂载目录
# HTTP-01 standalone 校验需要独占 80 端口, 而 hw-admin 容器常驻 80, 故续期期间停容器
set -euo pipefail

DOMAIN_SHOP="shop.qianchengsuoju.cn"
DOMAIN_ADMIN="admin.qianchengsuoju.cn"
CERT_DIR="/etc/nginx/certs"
NGINX_CONTAINER="hw-admin"
RENEW_AHEAD_DAYS=30

if [ "$(id -u)" -ne 0 ]; then
  echo "错误: 请用 root 执行"
  exit 1
fi

# 剩余天数不足才续期, 避免每周无谓地停/启容器
needs_renew() {
  local f end end_epoch
  for f in "${CERT_DIR}/admin.fullchain.pem" "${CERT_DIR}/shop.fullchain.pem"; do
    end="$(openssl x509 -in "$f" -noout -enddate 2>/dev/null | cut -d= -f2)"
    if [ -z "$end" ]; then
      return 0
    fi
    end_epoch="$(date -d "$end" +%s)"
    if [ $(( (end_epoch - $(date +%s)) / 86400 )) -le "$RENEW_AHEAD_DAYS" ]; then
      return 0
    fi
  done
  return 1
}

if ! needs_renew; then
  echo "证书剩余天数大于 ${RENEW_AHEAD_DAYS} 天, 跳过续期。"
  exit 0
fi

was_running=0
if [ "$(docker inspect -f '{{.State.Running}}' "$NGINX_CONTAINER" 2>/dev/null)" = "true" ]; then
  was_running=1
  echo "停止 ${NGINX_CONTAINER} 以释放 80 端口..."
  docker stop "$NGINX_CONTAINER" >/dev/null
fi

# 无论续期成功与否都必须把容器拉回来, 否则站点一直不可用
start_nginx() {
  if [ "$was_running" -eq 1 ]; then
    echo "启动 ${NGINX_CONTAINER}..."
    docker start "$NGINX_CONTAINER" >/dev/null
  fi
}
trap start_nginx EXIT

echo "[1/3] certbot renew..."
if ! certbot renew --standalone --non-interactive; then
  echo "错误: certbot renew 失败, 详见 /var/log/letsencrypt/letsencrypt.log" >&2
  exit 1
fi

echo "[2/3] 同步证书到 ${CERT_DIR}..."
cp "/etc/letsencrypt/live/${DOMAIN_SHOP}/fullchain.pem" "${CERT_DIR}/shop.fullchain.pem"
cp "/etc/letsencrypt/live/${DOMAIN_SHOP}/privkey.pem" "${CERT_DIR}/shop.privkey.pem"
cp "/etc/letsencrypt/live/${DOMAIN_ADMIN}/fullchain.pem" "${CERT_DIR}/admin.fullchain.pem"
cp "/etc/letsencrypt/live/${DOMAIN_ADMIN}/privkey.pem" "${CERT_DIR}/admin.privkey.pem"
chmod 644 "${CERT_DIR}"/*.pem

# 容器在 EXIT trap 中启动, 启动即重新读取证书, 无需额外 reload
echo "[3/3] 续期完成。"
