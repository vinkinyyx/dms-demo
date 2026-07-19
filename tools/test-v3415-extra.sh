#!/bin/bash
# v3.4.15 补充测试
set +e
BASE="http://localhost"
PASS=0; FAIL=0
chk(){ if [ "$1" = "$2" ]; then PASS=$((PASS+1)); echo "  ✅ $3 ($1)"; else FAIL=$((FAIL+1)); echo "  ❌ $3 (期望$2 实际$1)"; fi; }

TOKEN=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  --data-binary '{"tenantCode":"default","username":"admin","password":"'"${DMS_PWD:-Sh123456}"'"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AUTH="Authorization: Bearer $TOKEN"
echo "TOKEN_LEN=${#TOKEN}"

echo "== #2 授权 GET by id + 名称 =="
AID=$(curl -s "$BASE/api/authorizations?page=1&size=1" -H "$AUTH" | python3 -c "import sys,json;d=json.load(sys.stdin)['data']['list'];print(d[0]['id'] if d else '')")
if [ -n "$AID" ]; then
  chk "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/authorizations/$AID" -H "$AUTH")" 200 "授权详情GET(id=$AID)"
  DET=$(curl -s "$BASE/api/authorizations/$AID" -H "$AUTH")
  chk "$(echo $DET | grep -o 'dealerName' | head -1)" 'dealerName' "授权详情含dealerName"
  LST=$(curl -s "$BASE/api/authorizations?page=1&size=1" -H "$AUTH")
  chk "$(echo $LST | grep -o 'dealerName' | head -1)" 'dealerName' "授权列表含dealerName"
else echo "  ⚠ 无授权数据"; fi

echo "== #3 价格新增(空日期不再500) =="
PID=$(curl -s "$BASE/api/lookups/products?limit=1" -H "$AUTH" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d[0]['id'] if d else '')")
DID=$(curl -s "$BASE/api/lookups/dealers?limit=1" -H "$AUTH" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d[0]['id'] if d else '')")
PR=$(curl -s -X POST "$BASE/api/product-prices" -H "$AUTH" -H 'Content-Type: application/json' \
  --data-binary "{\"productId\":$PID,\"partnerType\":\"DEALER\",\"partnerId\":$DID,\"purchasePrice\":10,\"salesPrice\":20,\"currency\":\"CNY\",\"effectiveDate\":\"\",\"expireDate\":\"\",\"status\":\"active\"}")
chk "$(echo $PR | grep -o '\"code\":0' | head -1)" '"code":0' "价格新增空日期成功"
chk "$(curl -s "$BASE/api/product-prices?page=1&size=1" -H "$AUTH" | grep -o 'partnerName' | head -1)" 'partnerName' "价格列表含partnerName"

echo "== #4 库存移动仓库下拉过滤软删(与仓库管理一致) =="
LK=$(curl -s "$BASE/api/lookups/warehouses?limit=500" -H "$AUTH" | python3 -c "import sys,json;print(len(json.load(sys.stdin)['data']))")
WM=$(curl -s "$BASE/api/warehouses?page=1&size=500" -H "$AUTH" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['total'])")
echo "  lookup仓库=$LK 仓库管理total=$WM"
chk "$([ "$LK" = "$WM" ] && echo ok)" "ok" "仓库下拉与管理清单一致(均=$LK)"

echo "== #7 仓库精简(每类<=2, 共<=6) =="
chk "$([ "$WM" -le 6 ] && echo ok)" "ok" "仓库精简后<=6(实际$WM)"

echo "== #5 inventory查询扩展serialNo/keyword =="
chk "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/inventory?keyword=PROD&size=5" -H "$AUTH")" 200 "inventory keyword查询"
chk "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/inventory?serialNo=SN&size=5" -H "$AUTH")" 200 "inventory serialNo查询"

echo "== #8 菜单配置接口 =="
chk "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/menu-configs" -H "$AUTH")" 200 "菜单配置GET"
UP=$(curl -s -X POST "$BASE/api/menu-configs/upsert" -H "$AUTH" -H 'Content-Type: application/json' \
  --data-binary '[{"menuKey":"stock-moves","group":"库存管理","label":"库存移动","sortOrder":5,"visible":true}]')
chk "$(echo $UP | grep -o '\"saved\":1' | head -1)" '"saved":1' "菜单配置保存"
chk "$(curl -s "$BASE/api/menu-configs" -H "$AUTH" | grep -o 'stock-moves' | head -1)" 'stock-moves' "菜单配置回读"

echo ""
echo "====== v3.4.15 结果: PASS=$PASS FAIL=$FAIL ======"
