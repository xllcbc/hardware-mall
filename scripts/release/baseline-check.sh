#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MYSQL_BIN="${MYSQL_BIN:-mysql}"
MYSQLDUMP_BIN="${MYSQLDUMP_BIN:-mysqldump}"

: "${DB_HOST:?set DB_HOST}"
: "${DB_PORT:?set DB_PORT}"
: "${DB_NAME:?set DB_NAME}"
: "${DB_USERNAME:?set DB_USERNAME}"
: "${MYSQL_PWD:?set MYSQL_PWD}"
: "${FLYWAY_IMAGE:?set FLYWAY_IMAGE}"

scratch="${DB_NAME}_baseline_check"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

MYSQL=("$MYSQL_BIN" --protocol=TCP --default-character-set=utf8mb4
  -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME")
MYSQLDUMP=("$MYSQLDUMP_BIN" --no-data --skip-comments --routines --triggers
  --protocol=TCP --default-character-set=utf8mb4
  -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME")

"${MYSQL[@]}" -e "DROP DATABASE IF EXISTS \`$scratch\`; CREATE DATABASE \`$scratch\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

if ! bash "$SCRIPT_DIR/db-migrate.sh" migrate-schema "$scratch"; then
  echo "Baseline migration failed; scratch database retained: $scratch" >&2
  exit 1
fi

"${MYSQLDUMP[@]}" --ignore-table="$scratch.flyway_schema_history" "$scratch" > "$tmp/v1.sql"
"${MYSQLDUMP[@]}" --ignore-table="$DB_NAME.flyway_schema_history" "$DB_NAME" > "$tmp/prod.sql"

sed -E 's/ AUTO_INCREMENT=[0-9]+//g' "$tmp/v1.sql" > "$tmp/v1.norm"
sed -E 's/ AUTO_INCREMENT=[0-9]+//g' "$tmp/prod.sql" > "$tmp/prod.norm"

if ! diff -u "$tmp/prod.norm" "$tmp/v1.norm"; then
  echo "Baseline schema differs from production; scratch database retained: $scratch" >&2
  exit 1
fi

"${MYSQL[@]}" -e "DROP DATABASE \`$scratch\`"
echo "PASS: baseline schema matches production"
