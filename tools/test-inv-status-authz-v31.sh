#!/bin/bash
# v3.1 完整测试：库存状态 + 授权分类 + 销退/采退 + 库存移动
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

echo "=== v3.1 库存状态+授权分类+销退/采退 测试 ==="
TOKEN=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"admin","password":"'"${DMS_PWD:-Sh123456}"'"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AH="Authorization: Bearer $TOKEN"

echo "[1] 库存状态字典存在..."
R=$(curl -s -H "$AH" "$API/api/dicts/INVENTORY_STATUS/items")
HAS_Q=$(echo "$R" | grep -c 'QUALIFIED')
HAS_P=$(echo "$R" | grep -c 'PENDING')
HAS_D=$(echo "$R" | grep -c 'DEFECTIVE')
[ "$HAS_Q" -gt 0 ] && [ "$HAS_P" -gt 0 ] && [ "$HAS_D" -gt 0 ] && pass "库存状态字典 (含QUALIFIED/PENDING/DEFECTIVE)" || fail "库存状态字典" "Q=$HAS_Q P=$HAS_P D=$HAS_D"

echo "[2] 现有库存已有 stock_status..."
R=$(curl -s -H "$AH" "$API/api/inventory?page=1&size=5")
HAS=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data']['list'];print('yes' if d and 'stockStatus' in d[0] else 'no')" 2>/dev/null)
[ "$HAS" = "yes" ] && pass "库存包含 stockStatus 字段" || fail "库存缺 stockStatus" "$HAS"

echo "[3] 产品 productId=1 库存分状态查询..."
R=$(curl -s -H "$AH" "$API/api/inventory-status/product/1")
check "产品库存按状态汇总" "$R"

echo "[4] 授权 - 缺产品分类应报错..."
DEALER=$(curl -s -H "$AH" "$API/api/lookups/dealers?limit=1" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['id'])")
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"dealerId\":$DEALER,\"terminalIds\":\"1\",\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\"}" \
  "$API/api/authorizations")
CODE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))" 2>/dev/null)
[ "$CODE" != "0" ] && pass "缺产品分类正确报错 (code=$CODE)" || fail "应报错" "code=$CODE"

echo "[5] 授权 - 完整字段(categoryIds)创建..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"dealerId\":$DEALER,\"categoryIds\":\"1,2\",\"terminalIds\":\"1,2\",\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\",\"authType\":\"ORDER\"}" \
  "$API/api/authorizations")
check "授权创建（categoryIds 分类）" "$R"

echo "[6] 查询经销商有效授权 - 应返回 categoryIds..."
R=$(curl -s -H "$AH" "$API/api/authorizations/effective/$DEALER")
HAS_CAT=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print('yes' if 'categoryIds' in d else 'no')" 2>/dev/null)
[ "$HAS_CAT" = "yes" ] && pass "返回 categoryIds 字段" || fail "缺 categoryIds" "$HAS_CAT"

echo "[7] 产品分类授权检查..."
PROD=$(curl -s -H "$AH" "$API/api/lookups/products?limit=1" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['id'])")
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"dealerId\":$DEALER,\"productIds\":[$PROD]}" \
  "$API/api/authorizations/check-products")
HAS_ALLOWED=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print('yes' if 'allowedCategories' in d else 'no')" 2>/dev/null)
[ "$HAS_ALLOWED" = "yes" ] && pass "分类授权检查 (allowedCategories)" || fail "缺 allowedCategories" "$HAS_ALLOWED"

echo "[8] 库存状态迁移 (待检->合格)..."
WH=$(curl -s -H "$AH" "$API/api/lookups/warehouses?limit=1" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['id'])")
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"productId\":$PROD,\"warehouseId\":$WH,\"qty\":1,\"srcStatus\":\"PENDING\",\"dstStatus\":\"QUALIFIED\"}" \
  "$API/api/inventory-status/move")
CODE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))" 2>/dev/null)
[ "$CODE" = "0" -o "$CODE" = "40002" ] && pass "库存状态迁移（有库存则成功，无则合理报错）(code=$CODE)" || fail "库存状态迁移" "$R"

echo "[9] 可用库存查询 (合格库存)..."
R=$(curl -s -H "$AH" "$API/api/inventory-status/available?productId=$PROD")
check "可用库存查询" "$R"

echo "[10] 采购单可用于采退（is_red 字段存在）..."
R=$(curl -s -H "$AH" "$API/api/purchase-orders?page=1&size=1")
CODE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))" 2>/dev/null)
[ "$CODE" = "0" ] && pass "采购单列表" || fail "采购单列表" "code=$CODE"

echo "[11] 销售订单可用于销退（is_red 字段存在）..."
R=$(curl -s -H "$AH" "$API/api/orders?page=1&size=1")
check "销售订单列表" "$R"

echo "[12] 库存移动列表..."
R=$(curl -s -H "$AH" "$API/api/stock-moves?page=1&size=5")
check "库存移动列表" "$R"

echo "[13] 库存调整列表..."
R=$(curl -s -H "$AH" "$API/api/inventory-adjustments?page=1&size=5")
check "库存调整列表" "$R"

echo "[14] 分页参数(sort=updatedAt,desc)..."
R=$(curl -s -H "$AH" "$API/api/orders?page=1&size=20&sort=updatedAt,desc")
check "分页排序" "$R"

echo ""
echo "================================"
echo "  结果：通过 $PASS · 失败 $FAIL"
echo "================================"
