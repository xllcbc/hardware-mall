#!/usr/bin/env bash
set -euo pipefail

SCRIPT="$(cd "$(dirname "$0")/.." && pwd)/db-migrate.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

mkdir -p "$tmp/bin" "$tmp/migrations"
cat > "$tmp/bin/mysql" <<'STUB'
#!/usr/bin/env bash
sql="${*: -1}"
case "$sql" in
  *"table_name = 'flyway_schema_history'"*) echo "${HISTORY_EXISTS:-0}" ;;
  *"FROM flyway_schema_history"*) printf '%s\n' ${APPLIED:-} ;;
  *"COUNT(*) FROM information_schema.tables"*) echo "${TABLE_COUNT:-0}" ;;
  *) echo 1 ;;
esac
STUB
chmod +x "$tmp/bin/mysql"

export MYSQL_BIN="$tmp/bin/mysql" MIGRATION_DIR="$tmp/migrations"
export DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=hardware_mall DB_USERNAME=test MYSQL_PWD=test
export FLYWAY_IMAGE=example/flyway:13.12.2

run_expect() {
  local expected="$1"
  local command="$2"
  local out
  out="$("$SCRIPT" "$command")"
  if [[ "$out" != "$expected" ]]; then
    echo "expected [$expected] from '$command', got [$out]" >&2
    exit 1
  fi
}

touch "$tmp/migrations/V1__baseline.sql"

# 1) 空库、无 history -> ALL
HISTORY_EXISTS=0 TABLE_COUNT=0 run_expect "ALL" pending

# 2) 非空库、无 history -> NEED_BASELINE
HISTORY_EXISTS=0 TABLE_COUNT=7 run_expect "NEED_BASELINE" pending

# 3) history 有 V1、只有 V1 文件 -> none
HISTORY_EXISTS=1 APPLIED=1 run_expect "none" pending

# 4) history 有 V1、存在 V2 -> 2
touch "$tmp/migrations/V2__next.sql"
HISTORY_EXISTS=1 APPLIED=1 run_expect "2" pending

# 5) 多个待执行迁移按版本排序
touch "$tmp/migrations/V10__later.sql"
HISTORY_EXISTS=1 APPLIED=1 run_expect "2,10" pending

# 6) 非空库、无 history -> require-baseline exit 3
export HISTORY_EXISTS=0 TABLE_COUNT=7
set +e
"$SCRIPT" require-baseline >/dev/null 2>&1
code=$?
set -e
[[ "$code" -eq 3 ]] || { echo "expected exit 3, got $code"; exit 1; }

echo "PASS: db-migrate pending"
