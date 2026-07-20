#!/usr/bin/env bash
set -e

BACKUP_DIR="${BACKUP_DIR:-/var/backups/hardware-mall}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-hardware_mall}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:?需设置 DB_PASSWORD}"

mkdir -p "$BACKUP_DIR"

DATE=$(date +%Y%m%d_%H%M%S)
FILE="$BACKUP_DIR/${DB_NAME}_${DATE}.sql.gz"

echo "[backup] 开始备份 $DB_NAME -> $FILE"
mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" \
  --single-transaction --routines --triggers --events "$DB_NAME" | gzip > "$FILE"

echo "[backup] 完成大小 $(du -h "$FILE" | awk '{print $1}')"

echo "[backup] 清理 $RETENTION_DAYS 天前的备份"
find "$BACKUP_DIR" -name "${DB_NAME}_*.sql.gz" -mtime +$RETENTION_DAYS -print -delete

echo "[backup] done"
