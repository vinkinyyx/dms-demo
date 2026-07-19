#!/bin/bash
# 本次改造验证脚本（分页/授权/调拨/调整/元数据/导入导出）
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

echo "=== 分页+授权+调拨+调整+元数据+批量 冒烟测试 ==="
TOKEN=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"admin","password":"'"${DMS_PWD:-Sh123456}"'"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AH="Authorization: Bearer $TOKEN"

echo "[1] 分页 - orders 默认 20 条..."
R=$(curl -s -H "$AH" "$API/api/orders?page=1&size=20&sort=updatedAt,desc")
check "订单分页" "$R"

echo "[2] 分页 - orders size=5..."
R=$(curl -s -H "$AH" "$API/api/orders?page=1&size=5")
check "订单 size=5" "$R"

echo "[3] 分页 - orders 末页跳转..."
TOTAL=$(curl -s -H "$AH" "$API/api/orders?page=1&size=10" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['total'])")
LAST=$(( (TOTAL + 9) / 10 ))
R=$(curl -s -H "$AH" "$API/api/orders?page=$LAST&size=10")
check "订单末页 page=$LAST" "$R"

echo "[4] 调拨移库列表 (原本打不开)..."
R=$(curl -s -H "$AH" "$API/api/stock-moves?page=1&size=10")
check "调拨移库列表" "$R"

echo "[5] 库存调整列表 (原本打不开)..."
R=$(curl -s -H "$AH" "$API/api/inventory-adjustments?page=1&size=10")
check "库存调整列表" "$R"

echo "[6] 收货入库列表 (合并后)..."
R=$(curl -s -H "$AH" "$API/api/receipts?page=1&size=10")
check "收货入库列表" "$R"

echo "[7] 授权 - 缺产品线应报错..."
DEALER=$(curl -s -H "$AH" "$API/api/lookups/dealers?limit=1" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['id'])")
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"dealerId\":$DEALER,\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\"}" \
  "$API/api/authorizations")
CODE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))" 2>/dev/null)
[ "$CODE" = "40001" -o "$CODE" = "10001" -o "$CODE" != "0" ] && pass "缺产品线正确报错 (code=$CODE)" || fail "应报错" "code=$CODE"

echo "[8] 授权 - 完整字段创建..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"dealerId\":$DEALER,\"productLines\":\"IMPLANT,REAGENT\",\"terminalIds\":\"1,2\",\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\",\"authType\":\"ORDER\"}" \
  "$API/api/authorizations")
check "授权创建（完整字段）" "$R"

echo "[9] 授权 - 查询经销商有效授权..."
R=$(curl -s -H "$AH" "$API/api/authorizations/effective/$DEALER")
check "查询有效授权" "$R"
HAS=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['hasEffective'])" 2>/dev/null)
echo "     经销商是否有有效授权: $HAS"

echo "[10] 授权 - 产品线校验..."
PROD=$(curl -s -H "$AH" "$API/api/lookups/products?limit=1" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['id'])")
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"dealerId\":$DEALER,\"productIds\":[$PROD]}" \
  "$API/api/authorizations/check-products")
check "产品线授权检查" "$R"

echo "[11] 元数据 - orders 返回 createdAt/updatedAt..."
R=$(curl -s -H "$AH" "$API/api/orders?page=1&size=1")
HAS_TIME=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data']['list'];print('yes' if d and 'createdAt' in d[0] else 'no')" 2>/dev/null)
[ "$HAS_TIME" = "yes" ] && pass "订单包含 createdAt" || fail "订单缺 createdAt" "$HAS_TIME"

echo "[12] 元数据 - dealers 返回时间字段..."
R=$(curl -s -H "$AH" "$API/api/dealers?page=1&size=1")
HAS_TIME=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data']['list'];print('yes' if d and ('createdAt' in d[0] or 'created_at' in d[0]) else 'no')" 2>/dev/null)
[ "$HAS_TIME" = "yes" ] && pass "经销商包含时间字段" || fail "经销商缺时间字段" "$HAS_TIME"

echo ""
echo "================================"
echo "  结果：通过 $PASS · 失败 $FAIL"
echo "================================"
