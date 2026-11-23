#!/usr/bin/env bash
# 巡检 Nginx 实际对外提供的证书剩余天数, 低于阈值时发钉钉告警
# 用于兜住"续期失败但没人发现"的情况, 由 root cron 每日执行
set -uo pipefail

DOMAINS=("admin.qianchengsuoju.cn" "shop.qianchengsuoju.cn")
THRESHOLD_DAYS=14
ENV_FILE="${ENV_FILE:-/opt/hardware-mall/.env}"

urlencode() {
  local s="$1" out="" c i
  for (( i=0; i<${#s}; i++ )); do
    c="${s:i:1}"
    case "$c" in
      [a-zA-Z0-9.~_-]) out+="$c" ;;
      *) out+="$(printf '%%%02X' "'$c")" ;;
    esac
  done
  printf '%s' "$out"
}

dingtalk_alert() {
  local content="$1" webhook secret ts sign url body
  if [ ! -f "$ENV_FILE" ]; then
    echo "未找到 ${ENV_FILE}, 无法告警: ${content}" >&2
    return 0
  fi
  webhook="$(sed -n 's/^DINGTALK_WEBHOOK=//p' "$ENV_FILE" | tail -1)"
  secret="$(sed -n 's/^DINGTALK_SECRET=//p' "$ENV_FILE" | tail -1)"
  if [ -z "$webhook" ]; then
    echo "DINGTALK_WEBHOOK 未配置, 无法告警: ${content}" >&2
    return 0
  fi
  url="$webhook"
  if [ -n "$secret" ]; then
    ts="$(date +%s%3N)"
    sign="$(printf '%s\n%s' "$ts" "$secret" | openssl dgst -sha256 -hmac "$secret" -binary | base64)"
    url="${webhook}&timestamp=${ts}&sign=$(urlencode "$sign")"
  fi
  body="$(printf '{"msgtype":"text","text":{"content":"%s"}}' "$content")"
  curl -sS -m 10 -H 'Content-Type: application/json' -d "$body" "$url" >/dev/null
}

failed=0
for domain in "${DOMAINS[@]}"; do
  enddate="$(echo | openssl s_client -connect 127.0.0.1:443 -servername "$domain" 2>/dev/null \
    | openssl x509 -noout -enddate 2>/dev/null | cut -d= -f2)"
  if [ -z "$enddate" ]; then
    dingtalk_alert "[证书巡检] ${domain} 无法从本机 443 读取证书, 请检查 hw-admin 容器状态"
    failed=1
    continue
  fi
  end_epoch="$(date -d "$enddate" +%s)"
  now="$(date +%s)"
  if [ "$end_epoch" -le "$now" ]; then
    echo "${domain}: 证书已过期 (到期 ${enddate})"
    dingtalk_alert "[证书巡检] ${domain} 证书已于 ${enddate} 过期, 请执行 scripts/deploy/renew-certs.sh"
    failed=1
    continue
  fi
  days=$(( (end_epoch - now) / 86400 ))
  echo "${domain}: 剩余 ${days} 天 (到期 ${enddate})"
  if [ "$days" -lt "$THRESHOLD_DAYS" ]; then
    dingtalk_alert "[证书巡检] ${domain} 仅剩 ${days} 天 (到期 ${enddate}), 请执行 scripts/deploy/renew-certs.sh"
    failed=1
  fi
done

exit "$failed"
