#!/bin/bash
# v3.4.6 补充测试
set +e
API="http://localhost"
PASS=0; FAIL=0
pass(){ echo "  ✅ $1"; PASS=$((PASS+1)); }
fail(){ echo "  ❌ $1  $2"; FAIL=$((FAIL+1)); }

echo "=== v3.4.6 补充测试 ==="
TOKEN=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"admin","password":"'"${DMS_PWD:-Sh123456}"'"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AH="Authorization: Bearer $TOKEN"

echo ""
echo "== 销售出库列表含来源单号字段 =="
R=$(curl -s -H "$AH" "$API/api/sales-outs?page=1&size=1")
FIELDS=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['list']
print(','.join(d[0].keys()) if d else '')")
for f in dealerName sourceOrderCode sourceOrderId autoCreated; do
  echo "$FIELDS" | grep -q "$f" && pass "sales-outs 含 $f" || fail "缺 $f" "$FIELDS"
done

echo ""
echo "== 收货入库列表含来源单号字段 =="
R=$(curl -s -H "$AH" "$API/api/receipts?page=1&size=1")
FIELDS=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['list']
print(','.join(d[0].keys()) if d else '')")
for f in warehouseName sourcePoCode sourcePoId autoCreated; do
  echo "$FIELDS" | grep -q "$f" && pass "receipts 含 $f" || fail "缺 $f" "$FIELDS"
done

echo ""
echo "== 岗位每页 500+ 支持 =="
R=$(curl -s -H "$AH" "$API/api/sales-positions?page=1&size=500")
CODE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))")
[ "$CODE" = "0" ] && pass "岗位 size=500 通过" || fail "岗位 size=500 失败" "$R"

R=$(curl -s -H "$AH" "$API/api/dealers?page=1&size=1000")
CODE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))")
[ "$CODE" = "0" ] && pass "经销商 size=1000 通过" || fail "经销商 size=1000 失败" "$R"

echo ""
echo "== 岗位 PUT 编辑 =="
POS_ID=$(curl -s -H "$AH" "$API/api/sales-positions?page=1&size=1" | python3 -c "
import sys,json;d=json.load(sys.stdin)['data']['list'];print(d[0]['id'] if d else '')")
if [ -n "$POS_ID" ]; then
  R=$(curl -s -X PUT -H "$AH" -H "Content-Type: application/json" \
    "$API/api/sales-positions/$POS_ID" \
    --data-binary '{"name":"测试编辑名称","level":1,"region":"east","status":"active"}')
  CODE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))")
  [ "$CODE" = "0" ] && pass "岗位 PUT 编辑" || fail "岗位 PUT 失败" "$R"
fi

echo ""
echo "== 列表按 filter 参数过滤 =="
# 销售出库按 sourceOrderId 过滤（应能查到自动生成的单）
ORDER_ID=$(curl -s -H "$AH" "$API/api/orders?page=1&size=1" | python3 -c "
import sys,json;d=json.load(sys.stdin)['data']['list'];print(d[0]['id'] if d else '')")
R=$(curl -s -H "$AH" "$API/api/sales-outs?sourceOrderId=$ORDER_ID&page=1&size=10")
CODE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))")
[ "$CODE" = "0" ] && pass "销售出库 filter sourceOrderId 可用" || fail "filter 报错" "$R"

echo ""
echo "结果：通过 $PASS · 失败 $FAIL"
