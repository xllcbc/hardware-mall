#!/usr/bin/env bash
set -euo pipefail

# Manual-only disaster recovery. Never call this from the deploy workflow.
: "${DB_HOST:?set DB_HOST}"
: "${DB_PORT:?set DB_PORT}"
: "${DB_NAME:?set DB_NAME}"
: "${DB_USERNAME:?set DB_USERNAME}"
: "${MYSQL_PWD:?set MYSQL_PWD}"

if [[ "${1:-}" != "--yes" ]]; then
  echo "Refusing to overwrite $DB_NAME without --yes" >&2
  echo "Usage: db-restore.sh --yes <backup.sql>" >&2
  exit 2
fi

MYSQL_BIN="${MYSQL_BIN:-mysql}"
backup="${2:?backup path required}"

if [[ ! -s "$backup" ]]; then
  echo "Required backup is missing or empty: $backup" >&2
  exit 2
fi
if [[ ! -s "$backup.sha256" ]]; then
  echo "Required checksum file is missing or empty: $backup.sha256" >&2
  exit 2
fi
sha256sum -c "$backup.sha256"

MYSQL=("$MYSQL_BIN" --protocol=TCP --default-character-set=utf8mb4
  -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME")

"${MYSQL[@]}" -e "DROP DATABASE IF EXISTS \`$DB_NAME\`; CREATE DATABASE \`$DB_NAME\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
"${MYSQL[@]}" "$DB_NAME" < "$backup"

echo "Restored $DB_NAME from $backup"
