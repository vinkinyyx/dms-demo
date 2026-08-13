#!/usr/bin/env bash
# DMS 数据库恢复脚本（DAT-04）
# 用法:
#   ./restore_db.sh /opt/dms/backups/dms_test_20260813_023000.sql.gz
#
# 警告: 恢复会覆盖目标库数据，请先确认。
set -euo pipefail

BACKUP_FILE="${1:?用法: restore_db.sh <backup.sql.gz>}"
CONTAINER="${DB_CONTAINER:-dms-test-postgres}"
DB_USER="${DB_USER:-dms}"
DB_NAME="${DB_NAME:-dms_test}"

if [ ! -f "$BACKUP_FILE" ]; then
  echo "备份文件不存在: $BACKUP_FILE" >&2
  exit 1
fi

echo "即将把 $BACKUP_FILE 恢复到容器 $CONTAINER 的库 $DB_NAME"
read -r -p "确认继续？输入 YES 继续: " CONFIRM
if [ "$CONFIRM" != "YES" ]; then echo "已取消"; exit 1; fi

gunzip -c "$BACKUP_FILE" | docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME"
echo "恢复完成。"