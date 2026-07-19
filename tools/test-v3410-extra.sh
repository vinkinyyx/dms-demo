#!/bin/bash
set +e
API="http://localhost"
PASS=0; FAIL=0
pass(){ echo "  ✅ $1"; PASS=$((PASS+1)); }
fail(){ echo "  ❌ $1  $2"; FAIL=$((FAIL+1)); }

echo "=== v3.4.10 补充测试 ==="
TOKEN=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"admin","password":"'"${DMS_PWD:-Sh123456}"'"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AH="Authorization: Bearer $TOKEN"

echo ""
echo "== 产品选择授权过滤（一致性）=="
# 找一个 dealerId，看它 lookups/products 返回的产品数（应 >=1 才能下单）
DEALER=$(curl -s -H "$AH" "$API/api/lookups/dealers?limit=1" | python3 -c "
import sys,json;d=json.load(sys.stdin)['data'];print(d[0]['id'] if d else '')")
if [ -n "$DEALER" ]; then
  R=$(curl -s -H "$AH" "$API/api/lookups/products?dealerId=$DEALER&limit=200")
  CNT=$(echo "$R" | python3 -c "import sys,json;print(len(json.load(sys.stdin).get('data',[])))")
  [ "$CNT" -gt 0 ] && pass "经销商 $DEALER 授权产品数 $CNT (>0)" || fail "经销商 $DEALER 无授权产品" "$R"

  # 产品去重: 同一 id 不重复
  UNIQ=$(echo "$R" | python3 -c "
import sys,json;d=json.load(sys.stdin)['data']
ids=[x['id'] for x in d]
print('yes' if len(ids)==len(set(ids)) else 'no')")
  [ "$UNIQ" = "yes" ] && pass "产品下拉无重复" || fail "产品下拉有重复" "$UNIQ"

  # 产品含 unitType 字段
  HAS=$(echo "$R" | python3 -c "
import sys,json;d=json.load(sys.stdin)['data'];print('yes' if d and 'unitType' in d[0] else 'no')")
  [ "$HAS" = "yes" ] && pass "产品含 unitType" || fail "无 unitType" "$HAS"
fi

echo ""
echo "== 销售订单授权前置过滤 - 真实下单 =="
# 用第一个 dealer 授权产品尝试建单，应能创建成功
if [ -n "$DEALER" ]; then
  PID=$(curl -s -H "$AH" "$API/api/lookups/products?dealerId=$DEALER&limit=1" | python3 -c "
import sys,json;d=json.load(sys.stdin)['data'];print(d[0]['id'] if d else '')")
  if [ -n "$PID" ]; then
    R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" "$API/api/orders" \
      --data-binary "{\"orderType\":\"NORMAL\",\"dealerId\":$DEALER,\"lines\":[{\"productId\":$PID,\"qty\":1,\"unitPrice\":100}]}")
    CODE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))")
    [ "$CODE" = "0" ] && pass "销售订单 NORMAL 建单成功" || fail "订单创建失败" "$R"
  fi
fi

echo ""
echo "== 分次收货 =="
RK_ID=$(curl -s -H "$AH" "$API/api/receipts?page=1&size=10&status=DRAFT" | python3 -c "
import sys,json;d=json.load(sys.stdin)['data']['list'];print(d[0]['id'] if d else '')")
if [ -n "$RK_ID" ]; then
  DETAIL=$(curl -s -H "$AH" "$API/api/receipts/$RK_ID/detail")
  # 构造 lines：第一行收一半，其余行本次量 0，全部带批次号
  BODY=$(echo "$DETAIL" | python3 -c "
import sys,json,math
d=json.load(sys.stdin)['data']['lines']
lines=[]
for i,l in enumerate(d):
    exp=float(l.get('qty') or l.get('expectedQty') or 0)
    q = max(1, math.floor(exp/2)) if i==0 else 0
    lines.append({'receiptLineId':l['id'],'batchNo':'BN-TEST-%d'%i,'qty':q})
print(json.dumps({'lines':lines}))")
  R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" "$API/api/receipts/$RK_ID/execute" --data-binary "$BODY")
  NEW_STATUS=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('status','?'))")
  [ "$NEW_STATUS" = "PARTIAL_RECEIVED" ] && pass "分次收货：单据变 PARTIAL_RECEIVED" || fail "分次收货未变 PARTIAL" "$R"

  # 再次收剩余 → 应变 COMPLETED
  DETAIL2=$(curl -s -H "$AH" "$API/api/receipts/$RK_ID/detail")
  BODY2=$(echo "$DETAIL2" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['lines']
lines=[]
for i,l in enumerate(d):
    exp=float(l.get('qty') or l.get('expectedQty') or 0)
    rcv=float(l.get('receivedQty') or 0)
    rem=exp-rcv
    lines.append({'receiptLineId':l['id'],'batchNo':'BN-TEST-%d'%i,'qty':rem})
print(json.dumps({'lines':lines}))")
  R2=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" "$API/api/receipts/$RK_ID/execute" --data-binary "$BODY2")
  ST2=$(echo "$R2" | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('status','?'))")
  [ "$ST2" = "COMPLETED" ] && pass "补收剩余：单据变 COMPLETED" || fail "补收未变 COMPLETED" "$R2"
else
  echo "  ⚠️  跳过 - 无 DRAFT RK"
fi

echo ""
echo "== 排序参数 =="
R=$(curl -s -H "$AH" "$API/api/orders?page=1&size=3&sort=code,asc")
CODE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))")
[ "$CODE" = "0" ] && pass "orders sort=code,asc 支持" || fail "sort" "$R"

echo ""
echo "结果：通过 $PASS · 失败 $FAIL"
