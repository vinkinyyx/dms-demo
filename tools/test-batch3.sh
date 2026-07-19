#!/bin/bash
set +e
API="http://localhost"
PASS=0; FAIL=0
pass(){ echo "  ✅ $1"; PASS=$((PASS+1)); }
fail(){ echo "  ❌ $1  $2"; FAIL=$((FAIL+1)); }
check(){
  local d="$1"; local r="$2"
  local c=$(echo "$r" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))" 2>/dev/null)
  if [ "$c" = "0" ]; then pass "$d"; else fail "$d" "code=$c ${r:0:200}"; fi
}

echo "=== 批次 3 · P2 冒烟测试 ==="
TOKEN=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"admin","password":"'"${DMS_PWD:-Sh123456}"'"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AH="Authorization: Bearer $TOKEN"

echo "[US-A-11] 邮件审批 Token 生成..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary '{"resourceType":"contract-application","resourceId":"1","approverEmail":"boss@dms.com"}' \
  $API/api/system-ops/approval-tokens/generate)
check "生成 Token" "$R"
TOK=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)

echo "[US-A-11] 用 Token 审批..."
R=$(curl -s $API/api/system-ops/approval-tokens/$TOK/approve)
check "Token 快捷审批" "$R"

echo "[US-A-12] 手动超时检查..."
R=$(curl -s -X POST -H "$AH" $API/api/system-ops/check-timeouts)
check "超时检查" "$R"

echo "[US-D-11] 缓存监视..."
R=$(curl -s -H "$AH" $API/api/system-ops/cache/status)
check "Redis 状态" "$R"

echo "[US-D-11] 缓存清理..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" --data-binary '{"pattern":"dms:test:*"}' $API/api/system-ops/cache/flush)
check "缓存清理" "$R"

echo "[US-D-05] 批量用户导入..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary '{"users":[{"username":"batch_ut_'$(date +%s)'","name":"批量用户","email":"bt@test.com","userType":"vendor"}]}' \
  $API/api/system-ops/users/batch-import)
check "批量用户导入" "$R"

echo "[US-B-27] 批量发票导入..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary '{"invoices":[{"invoiceNo":"INV-'$(date +%s)'","refOrderId":1,"amount":1000,"taxAmount":130,"taxRate":0.13,"issueDate":"2026-07-01"}]}' \
  $API/api/system-ops/invoices/batch-import)
check "批量发票导入" "$R"

echo "[US-D-07] 数据权限查询..."
R=$(curl -s -H "$AH" $API/api/system-ops/my-data-scope)
check "我的数据权限" "$R"

echo "[US-D-06] RBAC 权限矩阵..."
R=$(curl -s -H "$AH" $API/api/system-ops/rbac/matrix)
check "RBAC 矩阵" "$R"

echo "[US-D-08] 流程列表..."
R=$(curl -s -H "$AH" $API/api/system-ops/workflows)
check "流程列表" "$R"

echo ""
echo "================================"
echo "  批次 3 结果：通过 $PASS · 失败 $FAIL"
echo "================================"
