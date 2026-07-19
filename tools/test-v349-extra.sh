#!/bin/bash
set +e
API="http://localhost"
PASS=0; FAIL=0
pass(){ echo "  ✅ $1"; PASS=$((PASS+1)); }
fail(){ echo "  ❌ $1  $2"; FAIL=$((FAIL+1)); }

echo "=== v3.4.9 补充测试 ==="
TOKEN=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"admin","password":"Sh123456"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AH="Authorization: Bearer $TOKEN"

echo ""
echo "== 供应商主数据 =="
R=$(curl -s -H "$AH" "$API/api/suppliers?page=1&size=20")
CODE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))")
[ "$CODE" = "0" ] && pass "GET /api/suppliers" || fail "suppliers 端点" "$R"
CNT=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['total'])")
[ "$CNT" -ge 10 ] && pass "供应商数 $CNT >= 10" || fail "供应商数不足" "$CNT"

R=$(curl -s -H "$AH" "$API/api/lookups/suppliers")
CNT2=$(echo "$R" | python3 -c "import sys,json;print(len(json.load(sys.stdin).get('data',[])))")
[ "$CNT2" -ge 10 ] && pass "lookups/suppliers 返回 $CNT2 个" || fail "lookups/suppliers" "$CNT2"

echo ""
echo "== 产品价格主数据 =="
R=$(curl -s -H "$AH" "$API/api/product-prices?page=1&size=20")
CODE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))")
[ "$CODE" = "0" ] && pass "GET /api/product-prices" || fail "product-prices" "$R"
CNT=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['total'])")
[ "$CNT" -ge 200 ] && pass "价格记录 $CNT >= 200" || fail "价格记录不足" "$CNT"

echo ""
echo "== 产品单位 unit_type =="
R=$(curl -s -H "$AH" "$API/api/lookups/products?limit=3")
HAS_UNIT=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print('yes' if d and 'unitType' in d[0] else 'no')")
[ "$HAS_UNIT" = "yes" ] && pass "lookups/products 含 unitType" || fail "unitType 未返回" "$R"

echo ""
echo "== 5 个销售用户 =="
R=$(curl -s -H "$AH" "$API/api/sales-positions/candidate-users")
CNT=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
salesN=[x for x in d if x.get('username','').startswith('sales')]
print(len(salesN))")
[ "$CNT" -ge 5 ] && pass "销售用户 sales* 数 $CNT >= 5" || fail "销售用户不足" "$CNT"

echo ""
echo "== 30 天数据链 =="
R=$(curl -s -H "$AH" "$API/api/orders?page=1&size=5")
TOTAL=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['total'])")
[ "$TOTAL" -ge 60 ] && pass "订单总数 $TOTAL >= 60" || fail "订单不足" "$TOTAL"

R=$(curl -s -H "$AH" "$API/api/purchase-orders?page=1&size=5")
POCNT=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['total'])")
[ "$POCNT" -ge 20 ] && pass "采购订单总数 $POCNT >= 20" || fail "采购单不足" "$POCNT"

R=$(curl -s -H "$AH" "$API/api/receipts?page=1&size=5")
RKCNT=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['total'])")
[ "$RKCNT" -ge 5 ] && pass "RK 单 $RKCNT" || fail "RK 单不足" "$RKCNT"

R=$(curl -s -H "$AH" "$API/api/sales-outs?page=1&size=5")
CKCNT=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['total'])")
[ "$CKCNT" -ge 10 ] && pass "CK 单 $CKCNT" || fail "CK 单不足" "$CKCNT"

echo ""
echo "结果：通过 $PASS · 失败 $FAIL"
