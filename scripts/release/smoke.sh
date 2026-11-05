#!/usr/bin/env bash
set -euo pipefail

API_BASE="${RELEASE_API_BASE:-http://127.0.0.1:8080}"
ADMIN_BASE="${RELEASE_ADMIN_BASE:-http://127.0.0.1}"
MAX_TIME="${RELEASE_SMOKE_MAX_TIME_SECONDS:-15}"

request_json() {
  local url="$1"
  local body
  body="$(curl -fsS --max-time "$MAX_TIME" "$url")"
  if [[ "$body" != *'"code":200'* && "$body" != *'"code": 200'* ]]; then
    echo "Unexpected API response from $url" >&2
    echo "$body" >&2
    exit 1
  fi
}

curl -fsS --max-time "$MAX_TIME" "$API_BASE/actuator/health" >/dev/null
request_json "$API_BASE/api/user/product/list?page=1&limit=1"
request_json "$API_BASE/api/user/category/list"
curl -fsS --max-time "$MAX_TIME" "$ADMIN_BASE/" >/dev/null

echo "PASS: release smoke"
