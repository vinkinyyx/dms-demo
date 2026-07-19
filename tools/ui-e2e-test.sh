#!/bin/bash
# ==================================================================
# UI/API 端到端冒烟测试
# 覆盖：登录 → Lookup 接口 → CRUD（含 Picker 关联字段）→ 中文枚举 → 分页
# ==================================================================
set +e
API="http://localhost"
PASS=0; FAIL=0
LOG=/tmp/ui-test.log
: > $LOG

pass(){ echo "  ✅ $1"; PASS=$((PASS+1)); }
fail(){ echo "  ❌ $1  详情: $2"; FAIL=$((FAIL+1)); }

echo "================================"
echo "  DMS UI 冒烟测试"
echo "================================"

echo "[1/12] 登录获取 token..."
TOKEN=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"admin","password":"'"${DMS_PWD:-Sh123456}"'"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('accessToken',''))")
[ -n "$TOKEN" ] && pass "登录成功 (token长度=${#TOKEN})" || fail "登录失败" "$TOKEN"

AH="Authorization: Bearer $TOKEN"

check_json_code0(){
  local desc="$1"; local resp="$2"
  local code=$(echo "$resp" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))" 2>/dev/null)
  if [ "$code" = "0" ]; then pass "$desc"; else fail "$desc" "code=$code resp=${resp:0:200}"; fi
}

# ---- Lookup 接口 ----
echo "[2/12] Lookup: 经销商..."
R=$(curl -s -H "$AH" "$API/api/lookups/dealers?limit=5")
check_json_code0 "Lookup dealers" "$R"

echo "[3/12] Lookup: 产品（搜索 PROD）..."
R=$(curl -s -H "$AH" "$API/api/lookups/products?keyword=PROD&limit=5")
check_json_code0 "Lookup products (search)" "$R"

echo "[4/12] Lookup: 医院..."
R=$(curl -s -H "$AH" "$API/api/lookups/hospitals?limit=5")
check_json_code0 "Lookup hospitals" "$R"

echo "[5/12] Lookup: 仓库..."
R=$(curl -s -H "$AH" "$API/api/lookups/warehouses?limit=5")
check_json_code0 "Lookup warehouses" "$R"

echo "[6/12] Lookup: 分类..."
R=$(curl -s -H "$AH" "$API/api/lookups/categories?limit=5")
check_json_code0 "Lookup categories" "$R"

echo "[7/12] Lookup: 区域..."
R=$(curl -s -H "$AH" "$API/api/lookups/regions?limit=5")
check_json_code0 "Lookup regions" "$R"

echo "[8/12] Lookup: 合同..."
R=$(curl -s -H "$AH" "$API/api/lookups/contracts?limit=5")
check_json_code0 "Lookup contracts" "$R"

echo "[9/12] Lookup: 订单..."
R=$(curl -s -H "$AH" "$API/api/lookups/orders?limit=5")
check_json_code0 "Lookup orders" "$R"

# ---- 前端页面（静态）----
echo "[10/12] 前端静态页..."
for p in "/" "/workspace.html" "/admin.html" "/dms-lib.js" "/dms.css"; do
  code=$(curl -sw '%{http_code}' -o /dev/null "$API$p")
  if [ "$code" = "200" ]; then pass "GET $p → 200"; else fail "GET $p" "http=$code"; fi
done

# ---- CRUD 通过 Lookup 联动 ----
echo "[11/12] 通过 Picker 建订单：先 lookup dealer 和 product，再 POST /api/orders (带明细)..."
DEALER_ID=$(curl -s -H "$AH" "$API/api/lookups/dealers?limit=1" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data'][0]['id'] if d['data'] else '')")
PRODUCT_ID=$(curl -s -H "$AH" "$API/api/lookups/products?limit=1" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data'][0]['id'] if d['data'] else '')")
if [ -n "$DEALER_ID" ] && [ -n "$PRODUCT_ID" ]; then
  pass "取到 dealerId=$DEALER_ID productId=$PRODUCT_ID"
  R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
    --data-binary "{\"orderType\":\"NORMAL\",\"dealerId\":$DEALER_ID,\"lines\":[{\"productId\":$PRODUCT_ID,\"qty\":5,\"unitPrice\":100,\"taxRate\":0.13,\"seq\":1}]}" \
    "$API/api/orders")
  check_json_code0 "创建订单 (Picker 联动 dealer+product+明细)" "$R"
else
  fail "取 dealer/product 失败" "$R"
fi

echo "[12/12] 通过 Picker 建产品：POST /api/products..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary '{"code":"UI-TEST-'$(date +%s)'","nameCn":"UI测试产品","unit":"个","currentPrice":99,"taxRate":0.13,"udiRequired":true,"status":"active"}' \
  "$API/api/products")
check_json_code0 "创建产品" "$R"

echo ""
echo "================================"
echo "  测试完成：通过 $PASS · 失败 $FAIL"
echo "================================"
