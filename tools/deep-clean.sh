#!/bin/bash
# 深度清理：停止 hermes-agent + medical-ai-knowledge-base 相关服务后删除
set +e

echo "======================================"
echo "  深度清理（已获用户确认）"
echo "======================================"
df -h /
BEFORE_KB=$(df / | awk 'NR==2{print $4}')
echo ""

echo "--- 1. 停止 hermes-agent 相关服务 ---"
# 结束 hermes gateway 主进程
pkill -f 'hermes_cli.main gateway' 2>&1
# 结束 tmux 会话
tmux kill-session -t rag_server 2>&1
# 结束 medical-ai 相关 python
pkill -f '/root/medical-ai-knowledge-base' 2>&1
pkill -f 'start_server.py' 2>&1
sleep 2
echo "剩余 hermes/medical 进程:"
ps aux | grep -E 'hermes|medical-ai' | grep -v grep | head

echo ""
echo "--- 2. 删除 /root/.hermes ---"
if [ -d /root/.hermes ]; then
  BEFORE=$(du -sh /root/.hermes 2>/dev/null | awk '{print $1}')
  rm -rf /root/.hermes
  echo "已删除 (原大小 $BEFORE)"
fi

echo "--- 3. 删除 /root/OMS_Knowledge_Base ---"
if [ -d /root/OMS_Knowledge_Base ]; then
  BEFORE=$(du -sh /root/OMS_Knowledge_Base 2>/dev/null | awk '{print $1}')
  rm -rf /root/OMS_Knowledge_Base
  echo "已删除 (原大小 $BEFORE)"
fi

echo "--- 4. 删除 /root/medical-ai-knowledge-base ---"
if [ -d /root/medical-ai-knowledge-base ]; then
  BEFORE=$(du -sh /root/medical-ai-knowledge-base 2>/dev/null | awk '{print $1}')
  rm -rf /root/medical-ai-knowledge-base
  echo "已删除 (原大小 $BEFORE)"
fi

echo "--- 5. 删除 /root/.cache/huggingface ---"
if [ -d /root/.cache/huggingface ]; then
  BEFORE=$(du -sh /root/.cache/huggingface 2>/dev/null | awk '{print $1}')
  rm -rf /root/.cache/huggingface
  echo "已删除 (原大小 $BEFORE)"
fi

echo "--- 6. 删除 /root/.agent-browser ---"
if [ -d /root/.agent-browser ]; then
  BEFORE=$(du -sh /root/.agent-browser 2>/dev/null | awk '{print $1}')
  rm -rf /root/.agent-browser
  echo "已删除 (原大小 $BEFORE)"
fi

echo "--- 7. 删除 /root/.cache/ms-playwright ---"
if [ -d /root/.cache/ms-playwright ]; then
  BEFORE=$(du -sh /root/.cache/ms-playwright 2>/dev/null | awk '{print $1}')
  rm -rf /root/.cache/ms-playwright
  echo "已删除 (原大小 $BEFORE)"
fi

echo "--- 8. 清理 /root/.cache 剩余 ---"
if [ -d /root/.cache ]; then
  BEFORE=$(du -sh /root/.cache 2>/dev/null | awk '{print $1}')
  find /root/.cache -type f -mtime +30 -delete 2>/dev/null
  echo "已清理 30 天前的文件 (原 $BEFORE)"
fi

echo "--- 9. 检查是否有其他隐藏的大目录 ---"
du -sh /root/.* 2>/dev/null | sort -rh | head -10

echo ""
echo "======================================"
echo "  清理完成"
echo "======================================"
df -h /
AFTER_KB=$(df / | awk 'NR==2{print $4}')
FREED_KB=$((AFTER_KB - BEFORE_KB))
echo ""
echo "本次释放: ${FREED_KB} KB ≈ $((FREED_KB / 1024)) MB ≈ $((FREED_KB / 1024 / 1024)) GB"
echo ""
echo "=== DMS 服务状态检查 ==="
docker ps --format 'table {{.Names}}\t{{.Status}}'
