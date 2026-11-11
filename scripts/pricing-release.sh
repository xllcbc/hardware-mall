#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${PRICING_RELEASE_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
DB_DIR="${PRICING_RELEASE_DB_DIR:-$ROOT_DIR/hardware-mall-backend/src/main/resources/db}"
BASE_MIGRATION="${PRICING_RELEASE_MIGRATION:-$DB_DIR/migration_2026-11-02_multi_level_pricing_release.sql}"
RATE_MIGRATION="${PRICING_RATE_MIGRATION:-$DB_DIR/migration_2026-11-09_level_category_rate.sql}"
VERIFY="${PRICING_RELEASE_VERIFY:-$DB_DIR/pricing_release_verify.sql}"
IDEMPOTENCY_MIGRATION="${ORDER_IDEMPOTENCY_MIGRATION:-$DB_DIR/migration_2026-11-11_order_idempotency.sql}"
IDEMPOTENCY_VERIFY="${ORDER_IDEMPOTENCY_VERIFY:-$DB_DIR/order_idempotency_verify.sql}"

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
  pricing-release.sh schema-state
  pricing-release.sh verify
  pricing-release.sh restore <backup.sql>
  pricing-release.sh rollback-drill <backup.sql> [previous-image]
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

schema_state() {
  local schema="$1"
  local counts new_tables new_columns legacy_tables legacy_columns
  # Legacy shares four common pricing columns; discount_rate is its only distinguishing column.
  counts="$("${MYSQL[@]}" -N -B "$schema" -e "
    SELECT CONCAT(
      (SELECT COUNT(*) FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'level_category_rate'),
      ':',
      (SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND (
            (table_name = 'user' AND column_name = 'level_id')
            OR (table_name = 'spu' AND column_name = 'exclude_discount')
            OR (table_name = 'order_item'
              AND column_name IN ('level_id', 'level_name', 'pricing_rate'))
          )),
      ':',
      (SELECT COUNT(*) FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'level_category_discount'),
      ':',
      (SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'order_item'
          AND column_name = 'discount_rate')
    );
  ")"
  IFS=':' read -r new_tables new_columns legacy_tables legacy_columns <<< "$counts"

  if [[ "$new_tables" == "1" && "$new_columns" == "5" &&
        "$legacy_tables" == "0" && "$legacy_columns" == "0" ]]; then
    echo "target"
  elif [[ "$new_tables" == "0" && "$new_columns" == "4" &&
          "$legacy_tables" == "1" && "$legacy_columns" == "1" ]]; then
    echo "legacy"
  elif [[ "$new_tables" == "0" && "$new_columns" == "0" &&
          "$legacy_tables" == "0" && "$legacy_columns" == "0" ]]; then
    echo "original"
  else
    echo "partial"
  fi
}

apply_pricing_migrations() {
  local schema="$1"
  local state
  state="$(schema_state "$schema")"
  case "$state" in
    original)
      "${MYSQL[@]}" "$schema" < "$BASE_MIGRATION"
      "${MYSQL[@]}" "$schema" < "$RATE_MIGRATION"
      "${MYSQL[@]}" --init-command="SET @expect_empty_level_category_rate = 1" \
        "$schema" < "$VERIFY"
      ;;
    legacy)
      "${MYSQL[@]}" "$schema" < "$RATE_MIGRATION"
      "${MYSQL[@]}" --init-command="SET @expect_empty_level_category_rate = 1" \
        "$schema" < "$VERIFY"
      ;;
    target)
      echo "Pricing schema already uses level_category_rate and pricing_rate."
      ;;
    *)
      echo "Pricing schema is partial; refusing to migrate $schema." >&2
      exit 4
      ;;
  esac
}

idempotency_state() {
  local schema="$1"
  "${MYSQL[@]}" -N -B "$schema" -e "
    SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'order_idempotency';
  "
}

apply_idempotency_migration() {
  local schema="$1"
  if [[ "$(idempotency_state "$schema")" == "0" ]]; then
    "${MYSQL[@]}" "$schema" < "$IDEMPOTENCY_MIGRATION"
  else
    echo "Order idempotency schema already present."
  fi
  "${MYSQL[@]}" "$schema" < "$IDEMPOTENCY_VERIFY"
}

verify_backup_checksum() {
  local backup="$1"
  if [[ ! -s "$backup.sha256" ]]; then
    echo "Required checksum file is missing or empty: $backup.sha256" >&2
    exit 2
  fi
  sha256sum -c "$backup.sha256"
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
    require_file "$BASE_MIGRATION"
    require_file "$RATE_MIGRATION"
    require_file "$VERIFY"
    require_file "$IDEMPOTENCY_MIGRATION"
    require_file "$IDEMPOTENCY_VERIFY"
    verify_backup_checksum "$backup"
    scratch="${DB_NAME}_pricing_preflight"
    "${MYSQL[@]}" -e "DROP DATABASE IF EXISTS \`$scratch\`; CREATE DATABASE \`$scratch\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
    "${MYSQL[@]}" "$scratch" < "$backup"
    apply_pricing_migrations "$scratch"
    "${MYSQL[@]}" "$scratch" < "$VERIFY"
    apply_idempotency_migration "$scratch"
    echo "Preflight database retained for inspection: $scratch"
    ;;
  migrate)
    require_db_env
    backup="${2:?backup path required}"
    require_file "$backup"
    require_file "$BASE_MIGRATION"
    require_file "$RATE_MIGRATION"
    require_file "$VERIFY"
    require_file "$IDEMPOTENCY_MIGRATION"
    require_file "$IDEMPOTENCY_VERIFY"
    verify_backup_checksum "$backup"
    apply_pricing_migrations "$DB_NAME"
    apply_idempotency_migration "$DB_NAME"
    ;;
  schema-state)
    require_db_env
    schema_state "$DB_NAME"
    ;;
  verify)
    require_db_env
    require_file "$VERIFY"
    require_file "$IDEMPOTENCY_VERIFY"
    "${MYSQL[@]}" "$DB_NAME" < "$VERIFY"
    "${MYSQL[@]}" "$DB_NAME" < "$IDEMPOTENCY_VERIFY"
    ;;
  restore)
    require_db_env
    backup="${2:?backup path required}"
    require_file "$backup"
    verify_backup_checksum "$backup"
    "${MYSQL[@]}" -e "DROP DATABASE IF EXISTS \`$DB_NAME\`; CREATE DATABASE \`$DB_NAME\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
    "${MYSQL[@]}" "$DB_NAME" < "$backup"
    ;;
  rollback-drill)
    require_db_env
    backup="${2:?backup path required}"
    previous_image="${3:-}"
    require_file "$backup"
    verify_backup_checksum "$backup"

    if [[ -n "$previous_image" ]] && ! docker image inspect "$previous_image" >/dev/null 2>&1; then
      echo "Previous release image is unavailable: $previous_image" >&2
      exit 5
    fi

    scratch="${DB_NAME}_rollback_drill"
    "${MYSQL[@]}" -e "DROP DATABASE IF EXISTS \`$scratch\`; CREATE DATABASE \`$scratch\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
    "${MYSQL[@]}" "$scratch" < "$backup"
    mysqlcheck --protocol=TCP --default-character-set=utf8mb4 \
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
    : > "$scan_file"
    for pattern in 'pricing:*' 'product:list:*' 'product:detail:*' 'product:recommend:*' \
      'product:search:*' 'product:cache:generation' 'sku:info:*'; do
      if ! "${redis_cli[@]}" --scan --pattern "$pattern" >> "$scan_file"; then
        echo "Redis cache scan failed for pattern: $pattern" >&2
        exit 4
      fi
    done
    sort -u "$scan_file" -o "$scan_file"

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
