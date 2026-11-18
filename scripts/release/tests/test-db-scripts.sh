#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

mkdir -p "$tmp/bin"
cat > "$tmp/bin/mysqldump" <<'STUB'
#!/usr/bin/env bash
echo "CREATE TABLE probe (id INT);"
STUB
cat > "$tmp/bin/mysql" <<'STUB'
#!/usr/bin/env bash
echo "$*" >> "$MYSQL_CALLS"
sql="${*: -1}"
case "$sql" in
  *"SUM(data_length+index_length)"*) echo "${DB_SIZE_BYTES:-1000}" ;;
  *"SELECT COUNT(*) FROM information_schema.tables"*) echo "${TABLE_COUNT:-5}" ;;
  *) echo 1 ;;
esac
STUB
cat > "$tmp/bin/mysqlcheck" <<'STUB'
#!/usr/bin/env bash
exit 0
STUB
cat > "$tmp/bin/df" <<'STUB'
#!/usr/bin/env bash
echo "Filesystem 1B-blocks Used Available Capacity Mounted on"
echo "stub 0 0 ${DF_AVAIL_BYTES:-100000000} 0% /"
STUB
cat > "$tmp/bin/docker" <<'STUB'
#!/usr/bin/env bash
exit 0
STUB
chmod +x "$tmp/bin/mysqldump" "$tmp/bin/mysql" "$tmp/bin/mysqlcheck" "$tmp/bin/df" "$tmp/bin/docker"

export MYSQL_BIN="$tmp/bin/mysql" MYSQLDUMP_BIN="$tmp/bin/mysqldump"
export MYSQLCHECK_BIN="$tmp/bin/mysqlcheck" DF_BIN="$tmp/bin/df" DOCKER_BIN="$tmp/bin/docker"
export MYSQL_CALLS="$tmp/mysql.calls"
export DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=hardware_mall DB_USERNAME=test MYSQL_PWD=test
export DF_AVAIL_BYTES=100000000 DB_SIZE_BYTES=1000
unset REDIS_HOST REDIS_PORT REDIS_PASSWORD

backup="$tmp/backup.sql"
bash "$DIR/db-backup.sh" "$backup" >/dev/null
[[ -s "$backup" ]] || { echo "backup missing"; exit 1; }
[[ -s "$backup.sha256" ]] || { echo "checksum missing"; exit 1; }
grep -q "CREATE TABLE probe" "$backup" || { echo "backup contents wrong"; exit 1; }

set +e
bash "$DIR/db-restore.sh" "$backup" >/dev/null 2>&1
code=$?
set -e
[[ "$code" -eq 2 ]] || { echo "restore without --yes expected exit 2, got $code"; exit 1; }

bash "$DIR/db-restore.sh" --yes "$backup" >/dev/null
grep -q "DROP DATABASE IF EXISTS .hardware_mall." "$MYSQL_CALLS" \
  || { echo "restore did not drop target database"; exit 1; }

# 备份前必须确认剩余磁盘能容纳备份 + 预检副本
set +e
DF_AVAIL_BYTES=1 DB_SIZE_BYTES=1000 bash "$DIR/db-backup.sh" "$tmp/low.sql" >/dev/null 2>&1
code=$?
set -e
[[ "$code" -ne 0 ]] || { echo "backup ignored insufficient free space"; exit 1; }

# 回滚演练成功后必须清理 scratch 库
: > "$MYSQL_CALLS"
bash "$DIR/rollback-drill.sh" "$backup" >/dev/null
grep -q "DROP DATABASE .hardware_mall_rollback_drill." "$MYSQL_CALLS" \
  || { echo "rollback drill left scratch database behind"; exit 1; }

echo "PASS: db-backup/db-restore"
