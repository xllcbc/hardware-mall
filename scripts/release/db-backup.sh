#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
MYSQL_BIN="${MYSQL_BIN:-mysql}"
MYSQLDUMP_BIN="${MYSQLDUMP_BIN:-mysqldump}"
DF_BIN="${DF_BIN:-df}"
MIN_FREE_PERCENT="${RELEASE_MIN_FREE_PERCENT:-250}"

: "${DB_HOST:?set DB_HOST}"
: "${DB_PORT:?set DB_PORT}"
: "${DB_NAME:?set DB_NAME}"
: "${DB_USERNAME:?set DB_USERNAME}"
: "${MYSQL_PWD:?set MYSQL_PWD}"

backup="${1:?backup path required}"

mkdir -p "$(dirname "$backup")"

db_bytes="$("$MYSQL_BIN" --protocol=TCP -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" \
  -N -B "$DB_NAME" -e \
  "SELECT COALESCE(SUM(data_length + index_length), 0) FROM information_schema.tables WHERE table_schema = DATABASE()")"
[[ "$db_bytes" =~ ^[0-9]+$ ]] || db_bytes=0
required_bytes=$(( db_bytes * MIN_FREE_PERCENT / 100 ))

avail_bytes="$("$DF_BIN" -P -B1 "$(dirname "$backup")" | awk 'NR == 2 { print $4 }')"
[[ "$avail_bytes" =~ ^[0-9]+$ ]] || avail_bytes=0
if (( avail_bytes < required_bytes )); then
  echo "insufficient free space for backup: need ${required_bytes} bytes (${MIN_FREE_PERCENT}% of ${db_bytes}), have ${avail_bytes}" >&2
  exit 7
fi

"$MYSQLDUMP_BIN" --protocol=TCP --default-character-set=utf8mb4 \
  --single-transaction --routines --triggers --events --set-gtid-purged=OFF \
  -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" \
  "$DB_NAME" > "$backup"

sha256sum "$backup" > "$backup.sha256"
cat "$backup.sha256"
