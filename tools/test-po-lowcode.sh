#!/bin/bash
# 采购销售拆分 + 状态机 + 低代码 冒烟测试
set +e
API="http://localhost"
PASS=0; FAIL=0
pass(){ echo "  ✅ $1"; PASS=$((PASS+1)); }
fail(){ echo "  ❌ $1  $2"; FAIL=$((FAIL+1)); }
check(){
  local d="$1"; local r="$2"
  local c=$(echo "$r" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))" 2>/dev/null)
  if [ "$c" = "0" ]; then pass "$d"; else fail "$d" "code=$c ${r:0:250}"; fi
}

echo "=== 批次 · 采购销售拆分 + 低代码 冒烟测试 ==="
TOKEN=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"admin","password":"'"${DMS_PWD:-Sh123456}"'"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AH="Authorization: Bearer $TOKEN"

echo "[1] 采购订单列表..."
R=$(curl -s -H "$AH" "$API/api/purchase-orders")
check "采购订单列表" "$R"

echo "[2] 创建采购订单..."
DEALER=$(curl -s -H "$AH" "$API/api/lookups/dealers?limit=1" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['id'])")
PROD=$(curl -s -H "$AH" "$API/api/lookups/products?limit=1" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['id'])")
WH=$(curl -s -H "$AH" "$API/api/lookups/warehouses?limit=1" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['id'])")
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"orderType\":\"NORMAL\",\"supplierId\":$DEALER,\"warehouseId\":$WH,\"lines\":[{\"productId\":$PROD,\"qty\":50,\"unitPrice\":80,\"taxRate\":0.13}]}" \
  $API/api/purchase-orders)
check "创建采购单" "$R"
PO_ID=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])" 2>/dev/null)

echo "[3] 查看采购单详情（应含 allowedActions）..."
R=$(curl -s -H "$AH" "$API/api/purchase-orders/$PO_ID")
check "采购单详情" "$R"
ACTIONS=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin);print(','.join([a['key'] for a in d['data']['allowedActions']]))" 2>/dev/null)
echo "     DRAFT 状态允许操作: $ACTIONS"

echo "[4] 提交采购单审批..."
R=$(curl -s -X POST -H "$AH" $API/api/purchase-orders/$PO_ID/submit)
check "采购单提交" "$R"

echo "[5] 审批采购单..."
R=$(curl -s -X POST -H "$AH" $API/api/purchase-orders/$PO_ID/approve)
check "采购单审批" "$R"

echo "[6] 采购单收货入库..."
QTY_BEFORE=$(curl -s -H "$AH" "$API/api/inventory-summary/by-product/$PROD" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['totalQty'])")
R=$(curl -s -X POST -H "$AH" $API/api/purchase-orders/$PO_ID/receive)
check "采购单收货入库" "$R"
QTY_AFTER=$(curl -s -H "$AH" "$API/api/inventory-summary/by-product/$PROD" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['totalQty'])")
echo "     库存变化: $QTY_BEFORE -> $QTY_AFTER (应+50)"

echo "[7] 采购单完成状态..."
R=$(curl -s -H "$AH" $API/api/purchase-orders/$PO_ID)
STATUS=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['status'])")
[ "$STATUS" = "COMPLETED" ] && pass "采购单已 COMPLETED" || fail "采购单状态" "status=$STATUS"

echo "[8] 销售订单允许操作查询..."
R=$(curl -s -H "$AH" "$API/api/orders/actions-for-status?status=DRAFT")
check "销售订单 DRAFT 允许操作" "$R"

echo "[9] 表单字段配置查询..."
R=$(curl -s -H "$AH" "$API/api/form-configs/order")
check "订单字段配置" "$R"
FIELDS_COUNT=$(echo "$R" | python3 -c "import sys,json;print(len(json.load(sys.stdin)['data']))")
echo "     订单字段数: $FIELDS_COUNT"

echo "[10] 表单列表..."
R=$(curl -s -H "$AH" "$API/api/form-configs/forms")
check "表单列表" "$R"

echo "[11] 新增自定义字段..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary '[{"fieldKey":"custom_test_'$(date +%s)'","fieldLabel":"测试字段","fieldType":"text","isNative":false,"required":false,"showInForm":true,"showInList":false,"group":"扩展字段","sortOrder":200}]' \
  "$API/api/form-configs/order/upsert")
check "新增自定义字段" "$R"

echo "[12] 字典分类列表..."
R=$(curl -s -H "$AH" "$API/api/dicts/types")
check "字典分类" "$R"

echo "[13] 字典条目 - 库存状态..."
R=$(curl -s -H "$AH" "$API/api/dicts/INVENTORY_STATUS/items")
check "库存状态字典" "$R"

echo "[14] 新增字典条目..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"code\":\"TEST_$(date +%s)\",\"name\":\"测试项\",\"seq\":999}" \
  "$API/api/dicts/INVENTORY_STATUS/items")
check "新增字典条目" "$R"

echo ""
echo "================================"
echo "  结果：通过 $PASS · 失败 $FAIL"
echo "================================"
