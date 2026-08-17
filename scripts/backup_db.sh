#!/usr/bin/env bash
# DMS 数据库备份脚本（DAT-04）
# 用法:
#   ./backup_db.sh              # 备份测试库（默认容器 dms-test-postgres / 库 dms_test）
#   DB_NAME=dms ./backup_db.sh  # 备份指定库
#
# 建议 crontab（每日 02:30，保留 14 天）:
#   30 2 * * * /opt/dms/scripts/backup_db.sh >> /var/log/dms-backup.log 2>&1
set -euo pipefail

CONTAINER="${DB_CONTAINER:-dms-test-postgres}"
DB_USER="${DB_USER:-dms}"
DB_NAME="${DB_NAME:-dms_test}"
BACKUP_DIR="${BACKUP_DIR:-/opt/dms/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

mkdir -p "$BACKUP_DIR"
TS="$(date +%Y%m%d_%H%M%S)"
FILE="$BACKUP_DIR/${DB_NAME}_${TS}.sql.gz"

echo "[$(date '+%F %T')] backing up $DB_NAME from $CONTAINER -> $FILE"
docker exec "$CONTAINER" pg_dump -U "$DB_USER" "$DB_NAME" | gzip > "$FILE"

SIZE="$(du -h "$FILE" | cut -f1)"
echo "[$(date '+%F %T')] backup done: $FILE ($SIZE)"

# 删除超过保留期的备份
find "$BACKUP_DIR" -name "${DB_NAME}_*.sql.gz" -mtime +"$RETENTION_DAYS" -print -delete
echo "[$(date '+%F %T')] retention cleanup complete (>= ${RETENTION_DAYS} days removed)"