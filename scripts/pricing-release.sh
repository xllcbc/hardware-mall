#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DB_DIR="$ROOT_DIR/hardware-mall-backend/src/main/resources/db"
MIGRATION="$DB_DIR/migration_2026-11-02_multi_level_pricing_release.sql"
VERIFY="$DB_DIR/pricing_release_verify.sql"

DB_PORT="${DB_PORT:-3306}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"

MYSQL=(mysql --protocol=TCP --default-character-set=utf8mb4
  -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME")
MYSQLDUMP=(mysqldump --protocol=TCP --default-character-set=utf8mb4
  --single-transaction --routines --triggers --events --set-gtid-purged=OFF
  -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME")

usage() {
  cat <<'EOF'
Usage:
  pricing-release.sh backup <backup.sql>
  pricing-release.sh preflight <backup.sql>
  pricing-release.sh migrate <backup.sql>
  pricing-release.sh verify
  pricing-release.sh restore <backup.sql>
  pricing-release.sh clean-cache
EOF
}

require_db_env() {
  : "${DB_HOST:?set DB_HOST}"
  : "${DB_NAME:?set DB_NAME}"
  : "${DB_USERNAME:?set DB_USERNAME}"
  : "${MYSQL_PWD:?set MYSQL_PWD}"
}

require_file() {
  local path="$1"
  if [[ ! -s "$path" ]]; then
    echo "Required file is missing or empty: $path" >&2
    exit 2
  fi
}

verify_backup_checksum() {
  local backup="$1"
  if [[ -f "$backup.sha256" ]]; then
    sha256sum -c "$backup.sha256"
  else
    echo "Warning: checksum file not found: $backup.sha256" >&2
  fi
}

case "${1:-}" in
  backup)
    require_db_env
    backup="${2:?backup path required}"
    mkdir -p "$(dirname "$backup")"
    "${MYSQLDUMP[@]}" "$DB_NAME" > "$backup"
    sha256sum "$backup" > "$backup.sha256"
    cat "$backup.sha256"
    ;;
  preflight)
    require_db_env
    backup="${2:?backup path required}"
    require_file "$backup"
    verify_backup_checksum "$backup"
    scratch="${DB_NAME}_pricing_preflight"
    "${MYSQL[@]}" -e "DROP DATABASE IF EXISTS \`$scratch\`; CREATE DATABASE \`$scratch\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
    "${MYSQL[@]}" "$scratch" < "$backup"
    "${MYSQL[@]}" "$scratch" < "$MIGRATION"
    "${MYSQL[@]}" "$scratch" < "$VERIFY"
    echo "Preflight database retained for inspection: $scratch"
    ;;
  migrate)
    require_db_env
    backup="${2:?backup path required}"
    require_file "$backup"
    verify_backup_checksum "$backup"
    "${MYSQL[@]}" "$DB_NAME" < "$MIGRATION"
    ;;
  verify)
    require_db_env
    "${MYSQL[@]}" "$DB_NAME" < "$VERIFY"
    ;;
  restore)
    require_db_env
    backup="${2:?backup path required}"
    require_file "$backup"
    verify_backup_checksum "$backup"
    "${MYSQL[@]}" -e "DROP DATABASE IF EXISTS \`$DB_NAME\`; CREATE DATABASE \`$DB_NAME\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
    "${MYSQL[@]}" "$DB_NAME" < "$backup"
    ;;
  clean-cache)
    redis_cli=(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --no-auth-warning)
    if [[ -n "${REDIS_PASSWORD:-}" ]]; then
      export REDISCLI_AUTH="$REDIS_PASSWORD"
    fi
    if ! "${redis_cli[@]}" ping >/dev/null; then
      echo "Redis ping failed" >&2
      exit 3
    fi

    scan_file="$(mktemp)"
    trap 'rm -f "$scan_file"' EXIT
    if ! "${redis_cli[@]}" --scan --pattern 'pricing:*' > "$scan_file"; then
      echo "Redis pricing cache scan failed" >&2
      exit 4
    fi

    deleted=0
    while IFS= read -r key; do
      [[ -z "$key" ]] && continue
      "${redis_cli[@]}" DEL "$key" >/dev/null
      deleted=$((deleted + 1))
    done < "$scan_file"
    echo "Deleted $deleted pricing cache keys"
    ;;
  *)
    usage
    exit 2
    ;;
esac
