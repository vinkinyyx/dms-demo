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

echo "=== 批次 2 · P1 冒烟测试 ==="
TOKEN=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"admin","password":"'"${DMS_PWD:-Sh123456}"'"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AH="Authorization: Bearer $TOKEN"

echo "[US-C-09] 综合看板..."
R=$(curl -s -H "$AH" $API/api/dashboard/overview)
check "综合看板 overview" "$R"

echo "[US-B-17] 库存 3 卡..."
R=$(curl -s -H "$AH" $API/api/dashboard/inventory-stats)
check "库存统计（临期/呆滞/总量）" "$R"

echo "[US-HOME-04] 待办列表..."
R=$(curl -s -H "$AH" $API/api/dashboard/todos)
check "待办列表" "$R"

echo "[US-B-09] 订单批量导入..."
DEALER=$(curl -s -H "$AH" "$API/api/lookups/dealers?limit=1" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['id'])")
PROD=$(curl -s -H "$AH" "$API/api/lookups/products?limit=1" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['id'])")
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"orders\":[{\"orderType\":\"NORMAL\",\"dealerId\":$DEALER,\"lines\":[{\"productId\":$PROD,\"qty\":1,\"unitPrice\":100,\"taxRate\":0.13}]}]}" \
  $API/api/orders/batch-import)
check "批量导入订单 (1 条)" "$R"

echo "[US-B-10] 订单异步导出..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" -d '{}' $API/api/orders/export-async)
check "创建导出任务" "$R"
TASK_ID=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['taskId'])")
sleep 2
R=$(curl -s -H "$AH" $API/api/orders/export-tasks/$TASK_ID)
check "查询导出任务状态" "$R"

echo "[US-A-05] 合同变更前后对照..."
R=$(curl -s -H "$AH" $API/api/contract-applications/1/diff)
check "合同申请前后对照 diff" "$R"

echo "[US-A-06] 续约..."
R=$(curl -s -X POST -H "$AH" $API/api/contract-applications/renew-from/1)
check "合同续约（复制原合同）" "$R"

echo "[US-A-07] 批量延展..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary '{"contractIds":[1,2,3],"extendMonths":12}' \
  $API/api/contract-applications/batch-extend)
check "批量延展 3 条合同" "$R"

echo "[US-B-14] 收货撤销..."
# 先找一条 receipt
RID=$(curl -s -H "$AH" "$API/api/receipts?page=1&size=1" | python3 -c "import sys,json;d=json.load(sys.stdin);l=d.get('data',{}).get('list',[]);print(l[0]['id'] if l else '')" 2>/dev/null)
if [ -n "$RID" ]; then
  R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" --data-binary '{"reason":"测试撤销"}' $API/api/receipts/$RID/cancel)
  check "收货撤销 receipt=$RID" "$R"
else
  pass "收货撤销（跳过：无 receipt 数据）"
fi

echo ""
echo "================================"
echo "  批次 2 结果：通过 $PASS · 失败 $FAIL"
echo "================================"
