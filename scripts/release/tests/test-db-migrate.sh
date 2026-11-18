#!/usr/bin/env bash
set -euo pipefail

SCRIPT="$(cd "$(dirname "$0")/.." && pwd)/db-migrate.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

mkdir -p "$tmp/bin" "$tmp/migrations"
cat > "$tmp/bin/mysql" <<'STUB'
#!/usr/bin/env bash
sql="${*: -1}"
echo "$*" >> "${MYSQL_CALLS:-/dev/null}"
case "$sql" in
  *"table_name = 'flyway_schema_history'"*) echo "${HISTORY_EXISTS:-0}" ;;
  *"FROM flyway_schema_history"*) printf '%s\n' ${APPLIED:-} ;;
  *"COUNT(*) FROM information_schema.tables"*) echo "${TABLE_COUNT:-0}" ;;
  *) echo 1 ;;
esac
STUB
chmod +x "$tmp/bin/mysql"
cat > "$tmp/bin/docker" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$@" > "$DOCKER_ARGS"
exit 0
STUB
chmod +x "$tmp/bin/docker"

export MYSQL_BIN="$tmp/bin/mysql" MIGRATION_DIR="$tmp/migrations"
export DOCKER_BIN="$tmp/bin/docker" DOCKER_ARGS="$tmp/docker.args" MYSQL_CALLS="$tmp/mysql.calls"
export DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=hardware_mall DB_USERNAME=test MYSQL_PWD=test
export FLYWAY_IMAGE=example/flyway:13.12.2

run_expect() {
  local expected="$1"
  local command="$2"
  local out
  out="$(bash "$SCRIPT" "$command")"
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

# 6) 递归目录中的迁移同样必须被发现
mkdir -p "$tmp/migrations/sub"
touch "$tmp/migrations/sub/V20__nested.sql"
HISTORY_EXISTS=1 APPLIED=1 run_expect "2,10,20" pending
rm -rf "$tmp/migrations/sub"

# 7) 只读检查不依赖 Flyway 镜像
out="$(env -u FLYWAY_IMAGE HISTORY_EXISTS=1 APPLIED=1 bash "$SCRIPT" pending)"
[[ "$out" == "2,10" ]] || { echo "pending without FLYWAY_IMAGE: got [$out]"; exit 1; }

# 8) Flyway 密码不得出现在 docker 命令行参数中
MYSQL_PWD=super-secret bash "$SCRIPT" migrate-schema scratch >/dev/null
grep -qxF 'FLYWAY_PASSWORD' "$DOCKER_ARGS" \
  || { echo "expected bare FLYWAY_PASSWORD env reference"; exit 1; }
if grep -qF 'super-secret' "$DOCKER_ARGS"; then
  echo "password leaked into docker argv" >&2
  exit 1
fi

# 9) repeatable 迁移不能被静默跳过
touch "$tmp/migrations/R__refresh.sql"
set +e
bash "$SCRIPT" pending >/dev/null 2>&1
code=$?
set -e
rm -f "$tmp/migrations/R__refresh.sql"
[[ "$code" -eq 4 ]] || { echo "repeatable migration expected exit 4, got $code"; exit 1; }

# 10) 预检库名必须与生产库隔离
printf 'SELECT 1;\n' > "$tmp/backup.sql"
sha256sum "$tmp/backup.sql" > "$tmp/backup.sql.sha256"
: > "$MYSQL_CALLS"
set +e
PREFLIGHT_DB=hardware_mall bash "$SCRIPT" preflight "$tmp/backup.sql" >/dev/null 2>&1
code=$?
set -e
[[ "$code" -eq 4 ]] || { echo "same-name preflight expected exit 4, got $code"; exit 1; }
if grep -q "DROP DATABASE" "$MYSQL_CALLS"; then
  echo "preflight dropped a database before validating its name" >&2
  exit 1
fi

# 11) 非空库、无 history -> require-baseline exit 3
export HISTORY_EXISTS=0 TABLE_COUNT=7
set +e
bash "$SCRIPT" require-baseline >/dev/null 2>&1
code=$?
set -e
[[ "$code" -eq 3 ]] || { echo "expected exit 3, got $code"; exit 1; }

echo "PASS: db-migrate pending"
