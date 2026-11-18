#!/usr/bin/env bash
set -euo pipefail

MYSQL_BIN="${MYSQL_BIN:-mysql}"
MYSQLCHECK_BIN="${MYSQLCHECK_BIN:-mysqlcheck}"
DOCKER_BIN="${DOCKER_BIN:-docker}"
backup="${1:?backup path required}"
previous_image="${2:-}"

: "${DB_HOST:?set DB_HOST}"
: "${DB_PORT:?set DB_PORT}"
: "${DB_NAME:?set DB_NAME}"
: "${DB_USERNAME:?set DB_USERNAME}"
: "${MYSQL_PWD:?set MYSQL_PWD}"

if [[ ! -s "$backup" ]]; then
  echo "Required backup is missing or empty: $backup" >&2
  exit 2
fi
if [[ ! -s "$backup.sha256" ]]; then
  echo "Required checksum file is missing or empty: $backup.sha256" >&2
  exit 2
fi
sha256sum -c "$backup.sha256"

if [[ -n "$previous_image" ]] && ! "$DOCKER_BIN" image inspect "$previous_image" >/dev/null 2>&1; then
  echo "Previous release image is unavailable: $previous_image" >&2
  exit 5
fi

scratch="${DB_NAME}_rollback_drill"
MYSQL=("$MYSQL_BIN" --protocol=TCP --default-character-set=utf8mb4
  -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME")

"${MYSQL[@]}" -e "DROP DATABASE IF EXISTS \`$scratch\`; CREATE DATABASE \`$scratch\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
"${MYSQL[@]}" "$scratch" < "$backup"

"$MYSQLCHECK_BIN" --protocol=TCP --default-character-set=utf8mb4 \
  -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" --check "$scratch" >/dev/null

table_count="$("${MYSQL[@]}" -N -B -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$scratch';")"
if [[ "${table_count:-0}" -lt 1 ]]; then
  echo "Rollback drill restored no tables in $scratch" >&2
  exit 6
fi
echo "PASS: rollback backup restored to $scratch (${table_count} tables)"
if [[ -n "$previous_image" ]]; then
  echo "PASS: previous release image present: $previous_image"
fi
