#!/bin/bash
# v3.4.7 补充测试
set +e
API="http://localhost"
PASS=0; FAIL=0
pass(){ echo "  ✅ $1"; PASS=$((PASS+1)); }
fail(){ echo "  ❌ $1  $2"; FAIL=$((FAIL+1)); }

echo "=== v3.4.7 补充测试 ==="
TOKEN=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"admin","password":"Sh123456"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AH="Authorization: Bearer $TOKEN"

echo ""
echo "== 仪表盘筛选参数 =="
for period in today week month quarter year all; do
  R=$(curl -s -H "$AH" "$API/api/dashboard/kpi?period=$period")
  CODE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))")
  [ "$CODE" = "0" ] && pass "kpi period=$period" || fail "kpi period=$period" "$R"
done

R=$(curl -s -H "$AH" "$API/api/dashboard/sales-trend?period=year")
CODE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))")
[ "$CODE" = "0" ] && pass "sales-trend period=year" || fail "sales-trend period=year" "$R"

R=$(curl -s -H "$AH" "$API/api/dashboard/order-funnel?status=APPROVED")
CODE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))")
[ "$CODE" = "0" ] && pass "order-funnel status=APPROVED" || fail "order-funnel status=APPROVED" "$R"

# 经销商过滤
DEALER=$(curl -s -H "$AH" "$API/api/lookups/dealers?limit=1" | python3 -c "
import sys,json;d=json.load(sys.stdin)['data'];print(d[0]['id'] if d else '')")
if [ -n "$DEALER" ]; then
  R=$(curl -s -H "$AH" "$API/api/dashboard/kpi?dealerId=$DEALER")
  CODE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))")
  [ "$CODE" = "0" ] && pass "kpi dealerId 过滤" || fail "kpi dealerId 过滤" "$R"
fi

echo ""
echo "== 岗位绑定接口修复 =="
# 岗位 list 返回 boundUserId
R=$(curl -s -H "$AH" "$API/api/sales-positions?page=1&size=5")
FIELDS=$(echo "$R" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['list']
print(','.join(d[0].keys()) if d else '')")
echo "$FIELDS" | grep -q "boundUserId" && pass "岗位列表含 boundUserId" || fail "缺 boundUserId" "$FIELDS"

# 岗位挂载经销商：先找一个未占用的经销商
POS_ID=$(curl -s -H "$AH" "$API/api/sales-positions?page=1&size=1" | python3 -c "
import sys,json;d=json.load(sys.stdin)['data']['list'];print(d[0]['id'] if d else '')")
FREE_DEALER=$(curl -s -H "$AH" "$API/api/dealers?page=1&size=1000" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['list']
free = [x for x in d if not x.get('salesPositionId')]
print(free[0]['id'] if free else '')")

if [ -n "$POS_ID" ] && [ -n "$FREE_DEALER" ]; then
  curl -s -X PUT -H "$AH" -H "Content-Type: application/json" \
    "$API/api/sales-positions/$POS_ID/bind-dealers" \
    --data-binary '{"dealerIds":['$FREE_DEALER']}' > /dev/null
  # 从 list 查
  BOUND=$(curl -s -H "$AH" "$API/api/dealers?page=1&size=1000" | python3 -c "
import sys,json,os
d=json.load(sys.stdin)['data']['list']
tgt=int(os.environ.get('FID','0'))
row=[x for x in d if x.get('id')==tgt]
print(row[0].get('salesPositionId') if row else '')" FID=$FREE_DEALER)
  [ "$BOUND" = "$POS_ID" ] && pass "挂载空闲经销商 $FREE_DEALER→岗位 $POS_ID" || fail "挂载失败" "sales_position_id=$BOUND expected=$POS_ID"

  curl -s -X PUT -H "$AH" -H "Content-Type: application/json" \
    "$API/api/sales-positions/$POS_ID/bind-dealers" \
    --data-binary '{"dealerIds":[]}' > /dev/null
  BOUND2=$(curl -s -H "$AH" "$API/api/dealers?page=1&size=1000" | python3 -c "
import sys,json,os
d=json.load(sys.stdin)['data']['list']
tgt=int(os.environ.get('FID','0'))
row=[x for x in d if x.get('id')==tgt]
print(row[0].get('salesPositionId') if row else 'X')" FID=$FREE_DEALER)
  { [ "$BOUND2" = "None" ] || [ -z "$BOUND2" ]; } && pass "全量解挂生效" || fail "解挂失败" "sales_position_id=$BOUND2"
elif [ -z "$FREE_DEALER" ]; then
  echo "  ⚠️  无空闲经销商可用，跳过挂载测试"
fi

# 岗位绑定用户：null 表示解绑
if [ -n "$POS_ID" ]; then
  R=$(curl -s -X PUT -H "$AH" -H "Content-Type: application/json" \
    "$API/api/sales-positions/$POS_ID/bind-user" \
    --data-binary '{"userId":null}')
  CODE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))")
  [ "$CODE" = "0" ] && pass "bind-user userId=null 允许解绑" || fail "userId=null 报错" "$R"
fi

echo ""
echo "结果：通过 $PASS · 失败 $FAIL"
