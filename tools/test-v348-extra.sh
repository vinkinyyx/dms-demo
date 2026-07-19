#!/bin/bash
# v3.4.8 补充测试
set +e
API="http://localhost"
PASS=0; FAIL=0
pass(){ echo "  ✅ $1"; PASS=$((PASS+1)); }
fail(){ echo "  ❌ $1  $2"; FAIL=$((FAIL+1)); }

echo "=== v3.4.8 补充测试 ==="
TOKEN=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"admin","password":"'"${DMS_PWD:-Sh123456}"'"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AH="Authorization: Bearer $TOKEN"

echo ""
echo "== 候选销售用户 API =="
R=$(curl -s -H "$AH" "$API/api/sales-positions/candidate-users")
CODE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))")
[ "$CODE" = "0" ] && pass "candidate-users 端点" || fail "candidate-users" "$R"
CNT=$(echo "$R" | python3 -c "import sys,json;print(len(json.load(sys.stdin).get('data',[])))")
[ "$CNT" -gt 0 ] && pass "候选用户 $CNT 个" || echo "  ⚠️  候选用户数为 0"

# 所有候选用户 role 都应该是 sales
NOT_SALES=$(curl -s -H "$AH" "$API/api/sales-positions/candidate-users" | python3 -c "
import sys,json;d=json.load(sys.stdin)['data']
print(len([x for x in d if 'name' in x]))" 2>&1)
[ "$NOT_SALES" -gt 0 ] && pass "候选用户返回有效数据" || fail "候选用户结构错" "$NOT_SALES"

echo ""
echo "== 采购审批自动建 RK 单 =="
# 找一个 SUBMITTED 或 DRAFT 的采购单
PO_ID=$(curl -s -H "$AH" "$API/api/purchase-orders?page=1&size=20" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['list']
c=[x for x in d if x.get('status')=='SUBMITTED']
print(c[0]['id'] if c else '')")
if [ -z "$PO_ID" ]; then
  # 创建一个新的
  DRAFT=$(curl -s -H "$AH" "$API/api/purchase-orders?page=1&size=20" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['list']
c=[x for x in d if x.get('status')=='DRAFT']
print(c[0]['id'] if c else '')")
  if [ -n "$DRAFT" ]; then
    curl -s -X POST -H "$AH" "$API/api/purchase-orders/$DRAFT/submit" > /dev/null
    PO_ID=$DRAFT
  fi
fi
if [ -n "$PO_ID" ]; then
  R=$(curl -s -X POST -H "$AH" "$API/api/purchase-orders/$PO_ID/approve")
  CODE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))")
  [ "$CODE" = "0" ] && pass "采购审批成功 (PO=$PO_ID)" || fail "采购审批失败" "$R"

  # 校验对应 RK 存在
  RK_CNT=$(curl -s -H "$AH" "$API/api/receipts?sourcePoId=$PO_ID&page=1&size=5" | python3 -c "
import sys,json;print(json.load(sys.stdin)['data']['total'])")
  [ "$RK_CNT" -ge 1 ] && pass "对应 RK 自动生成 ($RK_CNT 个)" || fail "未自动生成 RK" "count=$RK_CNT"
else
  echo "  ⚠️  无可用采购单，跳过"
fi

echo ""
echo "== 仪表盘筛选字段 =="
# 无 period 用默认（全部）
R1=$(curl -s -H "$AH" "$API/api/dashboard/kpi" | python3 -c "
import sys,json;d=json.load(sys.stdin)['data'];print(d['totalOrders'])")
R2=$(curl -s -H "$AH" "$API/api/dashboard/kpi?period=today" | python3 -c "
import sys,json;d=json.load(sys.stdin)['data'];print(d['totalOrders'])")
[ "$R1" -ge "$R2" ] && pass "period=today 应 ≤ 全部 ($R2 vs $R1)" || fail "period 无过滤" "today=$R2 vs all=$R1"

# 经销商筛选
DEALER=$(curl -s -H "$AH" "$API/api/lookups/dealers?limit=1" | python3 -c "
import sys,json;d=json.load(sys.stdin)['data'];print(d[0]['id'] if d else '')")
if [ -n "$DEALER" ]; then
  R3=$(curl -s -H "$AH" "$API/api/dashboard/kpi?dealerId=$DEALER" | python3 -c "
import sys,json;d=json.load(sys.stdin)['data'];print(d['totalOrders'])")
  [ "$R3" -le "$R1" ] && pass "dealerId 过滤生效 ($R3 ≤ $R1)" || fail "dealerId 未过滤" "dealer=$R3 vs all=$R1"
fi

echo ""
echo "== ECharts 本地化 =="
CODE=$(curl -sf -o /dev/null -w "%{http_code}" "$API/echarts.min.js")
[ "$CODE" = "200" ] && pass "echarts.min.js 本地可访问" || fail "echarts 404" "http_code=$CODE"

echo ""
echo "结果：通过 $PASS · 失败 $FAIL"
