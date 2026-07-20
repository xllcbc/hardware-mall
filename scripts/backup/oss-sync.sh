#!/usr/bin/env bash
set -e

BACKUP_DIR="${BACKUP_DIR:-/var/backups/hardware-mall}"
OSS_BUCKET="${OSS_BUCKET:?需设置 OSS_BUCKET}"
OSS_PATH="${OSS_PATH:-backups/hardware-mall/}"
OSS_REGION="${OSS_REGION:-cn-beijing}"

if ! command -v ossutil >/dev/null 2>&1; then
  echo "[oss-sync] ossutil 未安装, 跳过. 请参考 https://help.aliyun.com/zh/oss/developer-reference/ossutil"
  exit 0
fi

echo "[oss-sync] 同步 $BACKUP_DIR -> oss://$OSS_BUCKET/$OSS_PATH"
ossutil sync "$BACKUP_DIR/" "oss://$OSS_BUCKET/$OSS_PATH" \
  -e "oss-$OSS_REGION.aliyuncs.com" \
  --recursive --update

echo "[oss-sync] done"
