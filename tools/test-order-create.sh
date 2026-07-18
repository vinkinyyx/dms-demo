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

echo "=== 新下单页 + 实时库存测试 ==="
TOKEN=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"admin","password":"Sh123456"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AH="Authorization: Bearer $TOKEN"

echo "[1] 产品实时库存查询..."
R=$(curl -s -H "$AH" $API/api/inventory-summary/by-product/1)
check "产品 1 库存" "$R"
echo "     内容预览: $(echo $R | head -c 300)"

echo ""
echo "[2] 经销商概览..."
R=$(curl -s -H "$AH" $API/api/inventory-summary/dealer-overview/1)
check "经销商 1 概览" "$R"
echo "     内容预览: $(echo $R | head -c 300)"

echo ""
echo "[3] 下单页静态资源..."
CODE=$(curl -sw '%{http_code}' -o /dev/null $API/order-create.html)
[ "$CODE" = "200" ] && pass "order-create.html HTTP 200" || fail "" "http=$CODE"

echo ""
echo "================================"
echo "  测试结果：通过 $PASS · 失败 $FAIL"
echo "================================"
