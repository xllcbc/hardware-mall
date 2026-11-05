#!/usr/bin/env bash
set -euo pipefail

API_BASE="${RELEASE_API_BASE:-http://127.0.0.1:8080}"
ADMIN_BASE="${RELEASE_ADMIN_BASE:-http://127.0.0.1}"
MAX_TIME="${RELEASE_SMOKE_MAX_TIME_SECONDS:-15}"
ATTEMPTS="${RELEASE_SMOKE_ATTEMPTS:-24}"
RETRY_SECONDS="${RELEASE_SMOKE_RETRY_SECONDS:-5}"

request_json() {
  local url="$1"
  local body
  body="$(curl -fsS --max-time "$MAX_TIME" "$url")"
  if [[ "$body" != *'"code":200'* && "$body" != *'"code": 200'* ]]; then
    echo "Unexpected API response from $url" >&2
    echo "$body" >&2
    return 1
  fi
}

smoke_once() {
  curl -fsS --max-time "$MAX_TIME" "$API_BASE/actuator/health" >/dev/null &&
    request_json "$API_BASE/api/user/product/list?page=1&limit=1" &&
    request_json "$API_BASE/api/user/category/list" &&
    curl -fsS --max-time "$MAX_TIME" "$ADMIN_BASE/" >/dev/null
}

attempt=1
while ! smoke_once; do
  if (( attempt >= ATTEMPTS )); then
    echo "Release smoke failed after ${ATTEMPTS} attempts" >&2
    exit 1
  fi
  echo "Release smoke attempt ${attempt}/${ATTEMPTS} failed; retrying in ${RETRY_SECONDS}s" >&2
  sleep "$RETRY_SECONDS"
  attempt=$((attempt + 1))
done

echo "PASS: release smoke (attempt ${attempt})"
