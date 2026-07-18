#!/bin/bash
# DMS 演示环境磁盘清理（安全版：不动 DMS 业务数据、不动其他项目目录）
set +e

echo "======================================"
echo "  DMS 演示环境磁盘清理"
echo "======================================"
echo ""
echo "=== 清理前 ==="
df -h /
BEFORE_AVAIL=$(df / | awk 'NR==2{print $4}')
echo ""

echo "--- 1. Docker 构建缓存（可释放 5-15GB）---"
docker builder prune -af 2>&1 | tail -2

echo "--- 2. Docker 孤儿镜像 ---"
docker image prune -f 2>&1 | tail -1

echo "--- 3. Docker 停止的容器 ---"
docker container prune -f 2>&1 | tail -1

echo "--- 4. Docker 无用卷 ---"
docker volume prune -f 2>&1 | tail -1

echo "--- 5. DMS 构建日志 ---"
CNT=$(ls /root/dms/*.log 2>/dev/null | wc -l)
SIZE=$(du -sh /root/dms/*.log 2>/dev/null | tail -1 | awk '{print $1}')
rm -f /root/dms/*.log
echo "已删除 $CNT 个日志文件 ($SIZE)"

echo "--- 6. apt 包缓存 ---"
BEFORE=$(du -sh /var/cache/apt 2>/dev/null | awk '{print $1}')
apt-get clean 2>&1 | tail -3
AFTER=$(du -sh /var/cache/apt 2>/dev/null | awk '{print $1}')
echo "apt 缓存 $BEFORE -> $AFTER"

echo "--- 7. systemd/journal 日志（保留最近 100M）---"
BEFORE=$(du -sh /var/log/journal 2>/dev/null | awk '{print $1}')
journalctl --vacuum-size=100M 2>&1 | tail -3
AFTER=$(du -sh /var/log/journal 2>/dev/null | awk '{print $1}')
echo "journal 日志 $BEFORE -> $AFTER"

echo "--- 8. Docker 容器 JSON 日志清空 ---"
FOUND=0
for f in /var/lib/docker/containers/*/*-json.log; do
  if [ -f "$f" ]; then
    truncate -s 0 "$f"
    FOUND=$((FOUND+1))
  fi
done
echo "已清空 $FOUND 个容器日志文件"

echo "--- 9. /tmp 7天前的文件 ---"
find /tmp -type f -mtime +7 -delete 2>/dev/null
find /tmp -type d -empty -delete 2>/dev/null
echo "完成"

echo "--- 10. /var/log 中 7 天前的 rotate 日志 ---"
find /var/log -type f \( -name '*.gz' -o -name '*.1' -o -name '*.old' \) -mtime +7 -delete 2>/dev/null
echo "完成"

echo ""
echo "======================================"
echo "  清理完成"
echo "======================================"
df -h /
AFTER_AVAIL=$(df / | awk 'NR==2{print $4}')
echo ""
echo "本次释放约: $((AFTER_AVAIL - BEFORE_AVAIL)) KB ≈ $(((AFTER_AVAIL - BEFORE_AVAIL) / 1024)) MB ≈ $(((AFTER_AVAIL - BEFORE_AVAIL) / 1024 / 1024)) GB"
echo ""
echo "注意：以下目录未清理（属于其他项目，需要你自行确认后手动清理）："
du -sh /root/.hermes /root/OMS_Knowledge_Base /root/.cache/huggingface /root/.agent-browser 2>/dev/null | sort -rh
