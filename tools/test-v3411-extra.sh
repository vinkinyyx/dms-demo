#!/bin/bash
set +e
API="http://localhost"
PASS=0; FAIL=0
pass(){ echo "  ✅ $1"; PASS=$((PASS+1)); }
fail(){ echo "  ❌ $1  $2"; FAIL=$((FAIL+1)); }

echo "=== v3.4.11 补充测试 ==="
TOKEN=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"admin","password":"'"${DMS_PWD:-Sh123456}"'"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AH="Authorization: Bearer $TOKEN"

echo ""
echo "== #1 采购审批→自动建RK（UI路径 /api/purchase-orders/{id}/approve）+ 仓库非空 =="
SUP=$(curl -s -H "$AH" "$API/api/lookups/suppliers?limit=1" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d[0]['id'] if d else '')")
WH=$(curl -s -H "$AH" "$API/api/lookups/warehouses?limit=1" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d[0]['id'] if d else '')")
PID=$(curl -s -H "$AH" "$API/api/lookups/products?limit=1" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d[0]['id'] if d else '')")
# 建采购单（带仓库）
POC=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" "$API/api/purchase-orders" \
  --data-binary "{\"orderType\":\"NORMAL\",\"supplierId\":$SUP,\"warehouseId\":$WH,\"lines\":[{\"productId\":$PID,\"qty\":10,\"unitPrice\":50,\"taxRate\":0.13}]}")
POID=$(echo "$POC" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
curl -s -X POST -H "$AH" "$API/api/purchase-orders/$POID/submit" >/dev/null
APR=$(curl -s -X POST -H "$AH" "$API/api/purchase-orders/$POID/approve")
RKID=$(echo "$APR" | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('autoCreatedReceiptId',''))")
[ -n "$RKID" ] && pass "采购单 $POID 审批自动建 RK=$RKID" || fail "审批未返回 RK" "$APR"
# 校验 RK 仓库非空
RKWH=$(curl -s -H "$AH" "$API/api/receipts?sourcePoId=$POID" | python3 -c "
import sys,json;d=json.load(sys.stdin)['data']['list'];print(d[0]['warehouseId'] if d else '')")
[ -n "$RKWH" ] && [ "$RKWH" != "None" ] && pass "RK 仓库非空 =$RKWH" || fail "RK 仓库为空" "$RKWH"

echo ""
echo "== #2 多行采购单：只收其中一行（其余行本次量0不报错）=="
PID2=$(curl -s -H "$AH" "$API/api/lookups/products?limit=3" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d[1]['id'] if len(d)>1 else d[0]['id'])")
POC2=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" "$API/api/purchase-orders" \
  --data-binary "{\"orderType\":\"NORMAL\",\"supplierId\":$SUP,\"warehouseId\":$WH,\"lines\":[{\"productId\":$PID,\"qty\":5,\"unitPrice\":50,\"taxRate\":0.13},{\"productId\":$PID2,\"qty\":8,\"unitPrice\":60,\"taxRate\":0.13}]}")
POID2=$(echo "$POC2" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
curl -s -X POST -H "$AH" "$API/api/purchase-orders/$POID2/submit" >/dev/null
APR2=$(curl -s -X POST -H "$AH" "$API/api/purchase-orders/$POID2/approve")
RKID2=$(echo "$APR2" | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('autoCreatedReceiptId',''))")
DETAIL=$(curl -s -H "$AH" "$API/api/receipts/$RKID2/detail")
# 只收第一行全部，第二行 0
BODY=$(echo "$DETAIL" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['lines']
lines=[]
for i,l in enumerate(d):
    exp=float(l.get('expectedQty') or l.get('qty') or 0)
    q = exp if i==0 else 0
    lines.append({'receiptLineId':l['id'],'batchNo':'BN-M-%d'%i,'qty':q})
print(json.dumps({'lines':lines}))")
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" "$API/api/receipts/$RKID2/execute" --data-binary "$BODY")
ST=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('status','?'))")
[ "$ST" = "PARTIAL_RECEIVED" ] && pass "多行单只收一行→PARTIAL_RECEIVED（不报错）" || fail "多行部分收货失败" "$R"

echo ""
echo "== #4 操作日志：采购单详情有 CREATE/审批记录 =="
LOGS=$(curl -s -H "$AH" "$API/api/operation-logs?resourceType=purchase_order&resourceId=$POID")
LOGN=$(echo "$LOGS" | python3 -c "import sys,json;print(len(json.load(sys.stdin).get('data',[])))")
[ "$LOGN" -ge 2 ] && pass "采购单 $POID 操作日志 $LOGN 条" || fail "操作日志不足" "$LOGS"

echo ""
echo "== #5 采退（红字）审批→红字入库 =="
RPO=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" "$API/api/purchase-orders" \
  --data-binary "{\"orderType\":\"RETURN\",\"isRed\":true,\"supplierId\":$SUP,\"warehouseId\":$WH,\"lines\":[{\"productId\":$PID,\"qty\":2,\"unitPrice\":50,\"taxRate\":0.13}]}")
RPOID=$(echo "$RPO" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
curl -s -X POST -H "$AH" "$API/api/purchase-orders/$RPOID/submit" >/dev/null
RAPR=$(curl -s -X POST -H "$AH" "$API/api/purchase-orders/$RPOID/approve")
RRKID=$(echo "$RAPR" | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('autoCreatedReceiptId',''))")
if [ -n "$RRKID" ]; then
  ISRED=$(curl -s -H "$AH" "$API/api/receipts?sourcePoId=$RPOID" | python3 -c "
import sys,json;d=json.load(sys.stdin)['data']['list'];print(d[0]['isRed'] if d else '')")
  [ "$ISRED" = "True" ] && pass "采退审批→红字RK isRed=true" || fail "采退RK非红字" "$ISRED"
else
  fail "采退审批未建RK" "$RAPR"
fi

echo ""
echo "结果：通过 $PASS · 失败 $FAIL"
