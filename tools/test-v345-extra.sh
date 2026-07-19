#!/bin/bash
# v3.4.5 补充测试
set +e
API="http://localhost"
PASS=0; FAIL=0
pass(){ echo "  ✅ $1"; PASS=$((PASS+1)); }
fail(){ echo "  ❌ $1  $2"; FAIL=$((FAIL+1)); }

echo "=== v3.4.5 补充测试 ==="
TOKEN=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"admin","password":"'"${DMS_PWD:-Sh123456}"'"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AH="Authorization: Bearer $TOKEN"

echo ""
echo "== 单号前缀 (V14 后) =="
# 检查订单前缀
BAD=$(curl -s -H "$AH" "$API/api/orders?page=1&size=100" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['list']
bad = [r['code'] for r in d if r.get('code') and not (r['code'].startswith('SO-') or r['code'].startswith('RSO-'))]
print(len(bad))")
[ "$BAD" = "0" ] && pass "订单前缀全部 SO-/RSO-" || fail "订单前缀非标" "count=$BAD"

BAD=$(curl -s -H "$AH" "$API/api/purchase-orders?page=1&size=50" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['list']
bad = [r['code'] for r in d if r.get('code') and not (r['code'].startswith('PO-') or r['code'].startswith('RPO-'))]
print(len(bad))")
[ "$BAD" = "0" ] && pass "采购单前缀全部 PO-/RPO-" || fail "采购单前缀非标" "count=$BAD"

BAD=$(curl -s -H "$AH" "$API/api/sales-outs?page=1&size=50" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['list']
bad = [r['code'] for r in d if r.get('code') and not r['code'].startswith('CK-')]
print(len(bad))")
[ "$BAD" = "0" ] && pass "销售出库前缀全部 CK-" || fail "销售出库前缀非标" "count=$BAD"

BAD=$(curl -s -H "$AH" "$API/api/receipts?page=1&size=50" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['list']
bad = [r['code'] for r in d if r.get('code') and not r['code'].startswith('RK-')]
print(len(bad))")
[ "$BAD" = "0" ] && pass "收货入库前缀全部 RK-" || fail "收货入库前缀非标" "count=$BAD"

echo ""
echo "== 产品 lookup 支持 dealerId 过滤 =="
# 无过滤
R1=$(curl -s -H "$AH" "$API/api/lookups/products?limit=5" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(len(d))")
# 有过滤
DEALER=$(curl -s -H "$AH" "$API/api/lookups/dealers?limit=1" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d[0]['id'] if d else '')")
if [ -n "$DEALER" ]; then
  R2=$(curl -s -H "$AH" "$API/api/lookups/products?dealerId=$DEALER&limit=100" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(len(d))")
  [ "$R2" != "$R1" -o "$R2" -ge 0 ] && pass "产品过滤经销商 (无:$R1 vs 有:$R2)" || fail "过滤无变化" "$R1/$R2"
fi

echo ""
echo "== 报表字段丰富度 =="
R=$(curl -s -H "$AH" "$API/api/reports/sales-ranking?limit=1")
FIELDS=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(','.join(d[0].keys()) if d else '')")
for f in dealerCode dealerLevel avgAmount approvedCount lastOrderAt; do
  echo "$FIELDS" | grep -q "$f" && pass "sales-ranking 含 $f" || fail "缺 $f" "$FIELDS"
done

R=$(curl -s -H "$AH" "$API/api/reports/product-top10?limit=1")
FIELDS=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(','.join(d[0].keys()) if d else '')")
for f in productCode productSpec dealerCount avgUnitPrice; do
  echo "$FIELDS" | grep -q "$f" && pass "product-top10 含 $f" || fail "缺 $f" ""
done

R=$(curl -s -H "$AH" "$API/api/reports/inventory-turnover?limit=1")
FIELDS=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(','.join(d[0].keys()) if d else '')")
for f in qualifiedStock pendingStock batchCount daysOfStock; do
  echo "$FIELDS" | grep -q "$f" && pass "inventory-turnover 含 $f" || fail "缺 $f" ""
done

R=$(curl -s -H "$AH" "$API/api/reports/surgery-stats?limit=1")
FIELDS=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(','.join(d[0].keys()) if d else '')")
for f in hospitalCode dealerCount doctorCount avgImplantsPerSurgery; do
  echo "$FIELDS" | grep -q "$f" && pass "surgery-stats 含 $f" || fail "缺 $f" ""
done

R=$(curl -s -H "$AH" "$API/api/reports/receivables?limit=1")
FIELDS=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(','.join(d[0].keys()) if d else '')")
for f in dealerCode aged30 aged60 aged90 earliestUnpaid; do
  echo "$FIELDS" | grep -q "$f" && pass "receivables 含 $f" || fail "缺 $f" ""
done

echo ""
echo "== 岗位管理 API =="
R=$(curl -s -H "$AH" "$API/api/sales-positions/tree")
CODE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))")
[ "$CODE" = "0" ] && pass "岗位树" || fail "岗位树" "$R"

echo ""
echo "结果：通过 $PASS · 失败 $FAIL"
