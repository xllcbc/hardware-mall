#!/usr/bin/env bash
set -euo pipefail

REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_CLI_BIN="${REDIS_CLI_BIN:-redis-cli}"

redis_cli=("$REDIS_CLI_BIN" -h "$REDIS_HOST" -p "$REDIS_PORT" --no-auth-warning)
if [[ -n "${REDIS_PASSWORD:-}" ]]; then
  export REDISCLI_AUTH="$REDIS_PASSWORD"
fi

if ! "${redis_cli[@]}" ping >/dev/null; then
  echo "Redis ping failed" >&2
  exit 3
fi

scan_file="$(mktemp)"
trap 'rm -f "$scan_file"' EXIT
: > "$scan_file"

for pattern in 'pricing:*' 'product:list:*' 'product:detail:*' 'product:recommend:*' \
  'product:search:*' 'product:cache:generation' 'sku:info:*'; do
  if ! "${redis_cli[@]}" --scan --pattern "$pattern" >> "$scan_file"; then
    echo "Redis cache scan failed for pattern: $pattern" >&2
    exit 4
  fi
done
sort -u "$scan_file" -o "$scan_file"

deleted=0
while IFS= read -r key; do
  [[ -z "$key" ]] && continue
  "${redis_cli[@]}" DEL "$key" >/dev/null
  deleted=$((deleted + 1))
done < "$scan_file"

echo "Deleted $deleted cache keys"
