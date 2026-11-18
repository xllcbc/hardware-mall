#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
MYSQLDUMP_BIN="${MYSQLDUMP_BIN:-mysqldump}"

: "${DB_HOST:?set DB_HOST}"
: "${DB_PORT:?set DB_PORT}"
: "${DB_NAME:?set DB_NAME}"
: "${DB_USERNAME:?set DB_USERNAME}"
: "${MYSQL_PWD:?set MYSQL_PWD}"

backup="${1:?backup path required}"

mkdir -p "$(dirname "$backup")"

"$MYSQLDUMP_BIN" --protocol=TCP --default-character-set=utf8mb4 \
  --single-transaction --routines --triggers --events --set-gtid-purged=OFF \
  -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" \
  "$DB_NAME" > "$backup"

sha256sum "$backup" > "$backup.sha256"
cat "$backup.sha256"
