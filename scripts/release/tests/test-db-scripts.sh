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
exit 0
STUB
chmod +x "$tmp/bin/mysqldump" "$tmp/bin/mysql"

export MYSQL_BIN="$tmp/bin/mysql" MYSQLDUMP_BIN="$tmp/bin/mysqldump"
export MYSQL_CALLS="$tmp/mysql.calls"
export DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=hardware_mall DB_USERNAME=test MYSQL_PWD=test
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

echo "PASS: db-backup/db-restore"
