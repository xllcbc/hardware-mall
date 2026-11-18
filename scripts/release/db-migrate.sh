#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
MIGRATION_DIR="${MIGRATION_DIR:-$ROOT_DIR/db/migration}"
MYSQL_BIN="${MYSQL_BIN:-mysql}"
DOCKER_BIN="${DOCKER_BIN:-docker}"

: "${DB_HOST:?set DB_HOST}"
: "${DB_PORT:?set DB_PORT}"
: "${DB_NAME:?set DB_NAME}"
: "${DB_USERNAME:?set DB_USERNAME}"
: "${MYSQL_PWD:?set MYSQL_PWD}"

PREFLIGHT_DB="${PREFLIGHT_DB:-${DB_NAME}_release_preflight}"

MYSQL=("$MYSQL_BIN" --protocol=TCP --default-character-set=utf8mb4
  -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME")

usage() {
  cat <<'EOF'
Usage:
  db-migrate.sh pending
  db-migrate.sh require-baseline
  db-migrate.sh status
  db-migrate.sh preflight <backup.sql>
  db-migrate.sh migrate
  db-migrate.sh migrate-schema <schema>
  db-migrate.sh baseline
EOF
}

jdbc_url() {
  printf 'jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8' \
    "$DB_HOST" "$DB_PORT" "$1"
}

mysql_scalar() {
  "${MYSQL[@]}" -N -B "$DB_NAME" -e "$1"
}

mysql_server() {
  "${MYSQL[@]}" -N -B -e "$1"
}

history_exists() {
  [[ "$(mysql_scalar "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'flyway_schema_history'")" != "0" ]]
}

table_count() {
  mysql_scalar "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()"
}

migration_versions() {
  find "$MIGRATION_DIR" -type f -name 'V*__*.sql' -printf '%f\n' \
    | sed -E 's/^V([^_]+)__.*/\1/' | sort -V
}

repeatable_migrations() {
  find "$MIGRATION_DIR" -type f -name 'R__*.sql' -print -quit
}

applied_versions() {
  mysql_scalar "SELECT version FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank"
}

flyway_run() {
  local url="$1"
  local image="${FLYWAY_IMAGE:?set FLYWAY_IMAGE}"
  shift
  # Pass the password via the docker client's environment, never in argv.
  FLYWAY_URL="$url" \
  FLYWAY_USER="$DB_USERNAME" \
  FLYWAY_PASSWORD="$MYSQL_PWD" \
    "$DOCKER_BIN" run --rm --network host \
      -v "$MIGRATION_DIR:/flyway/sql:ro" \
      -e FLYWAY_URL \
      -e FLYWAY_USER \
      -e FLYWAY_PASSWORD \
      -e FLYWAY_CONNECT_RETRIES=10 \
      "$image" -locations=filesystem:/flyway/sql "$@"
}

require_migrations() {
  if [[ ! -d "$MIGRATION_DIR" ]]; then
    echo "Migration directory is missing: $MIGRATION_DIR" >&2
    exit 2
  fi
}

assert_scratch_name() {
  local scratch="$1"
  if [[ "$scratch" != "${DB_NAME}_"* || "$scratch" == "$DB_NAME" ]]; then
    echo "refusing to touch non-scratch database: $scratch" >&2
    exit 4
  fi
}

pending_versions() {
  local applied pending_list="" version
  applied="$(applied_versions)"
  while IFS= read -r version; do
    [[ -z "$version" ]] && continue
    if ! grep -qxF "$version" <<< "$applied"; then
      pending_list+="$version"$'\n'
    fi
  done < <(migration_versions)
  printf '%s' "$pending_list" | sort -V | paste -sd, -
}

require_baseline() {
  if history_exists; then
    return 0
  fi
  if [[ "$(table_count)" == "0" ]]; then
    return 0
  fi
  echo "non-empty schema without flyway_schema_history; run 'db-migrate.sh baseline' first" >&2
  exit 3
}

case "${1:-}" in
  pending)
    require_migrations
    repeatable="$(repeatable_migrations)"
    if [[ -n "$repeatable" ]]; then
      echo "repeatable migrations are not supported by pending detection: $repeatable" >&2
      exit 4
    fi
    if history_exists; then
      pending="$(pending_versions)"
      if [[ -z "$pending" ]]; then
        echo "none"
      else
        echo "$pending"
      fi
    elif [[ "$(table_count)" == "0" ]]; then
      echo "ALL"
    else
      echo "NEED_BASELINE"
    fi
    ;;
  require-baseline)
    require_baseline
    ;;
  status)
    require_migrations
    flyway_run "$(jdbc_url "$DB_NAME")" info
    ;;
  migrate-schema)
    schema="${2:?schema required}"
    require_migrations
    flyway_run "$(jdbc_url "$schema")" migrate
    ;;
  preflight)
    backup="${2:?backup path required}"
    require_migrations
    assert_scratch_name "$PREFLIGHT_DB"
    if [[ ! -s "$backup" ]]; then
      echo "Required backup is missing or empty: $backup" >&2
      exit 2
    fi
    if [[ ! -s "$backup.sha256" ]]; then
      echo "Required checksum file is missing or empty: $backup.sha256" >&2
      exit 2
    fi
    sha256sum -c "$backup.sha256"
    mysql_server "DROP DATABASE IF EXISTS \`$PREFLIGHT_DB\`"
    mysql_server "CREATE DATABASE \`$PREFLIGHT_DB\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci"
    "${MYSQL[@]}" "$PREFLIGHT_DB" < "$backup"
    if ! flyway_run "$(jdbc_url "$PREFLIGHT_DB")" migrate; then
      echo "Preflight migration failed; scratch database retained: $PREFLIGHT_DB" >&2
      exit 1
    fi
    mysql_server "DROP DATABASE \`$PREFLIGHT_DB\`"
    echo "PASS: preflight migration on $PREFLIGHT_DB"
    ;;
  migrate)
    require_migrations
    require_baseline
    flyway_run "$(jdbc_url "$DB_NAME")" migrate
    ;;
  baseline)
    require_migrations
    if history_exists; then
      echo "flyway_schema_history already exists in $DB_NAME" >&2
      exit 2
    fi
    flyway_run "$(jdbc_url "$DB_NAME")" \
      -baselineVersion=1 \
      -baselineDescription="prod baseline 2026-11-18" \
      baseline
    ;;
  *)
    usage
    exit 2
    ;;
esac
