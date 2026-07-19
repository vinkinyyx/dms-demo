#!/bin/bash
set +e
API="http://localhost"
PASS=0; FAIL=0
pass(){ echo "  ✅ $1"; PASS=$((PASS+1)); }
fail(){ echo "  ❌ $1  $2"; FAIL=$((FAIL+1)); }

echo "=== v3.4.12 补充测试 ==="
TOKEN=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"admin","password":"Sh123456"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AH="Authorization: Bearer $TOKEN"

echo ""
echo "== #4 单据编号规则 PREFIX-YYYYMMDD-00001 =="
SUP=$(curl -s -H "$AH" "$API/api/lookups/suppliers?limit=1" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d[0]['id'] if d else '')")
WH=$(curl -s -H "$AH" "$API/api/lookups/warehouses?limit=1" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d[0]['id'] if d else '')")
PID=$(curl -s -H "$AH" "$API/api/lookups/products?limit=1" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d[0]['id'] if d else '')")
POC=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" "$API/api/purchase-orders" \
  --data-binary "{\"orderType\":\"NORMAL\",\"supplierId\":$SUP,\"warehouseId\":$WH,\"lines\":[{\"productId\":$PID,\"qty\":10,\"unitPrice\":50,\"taxRate\":0.13}]}")
POID=$(echo "$POC" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
POCODE=$(echo "$POC" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['code'])")
echo "$POCODE" | grep -qE '^PO-[0-9]{8}-[0-9]{5}$' && pass "采购单号规范 $POCODE" || fail "采购单号不规范" "$POCODE"

echo ""
echo "== #5 采购审批→自动建RK（编号 RK-YYYYMMDD-00001）=="
curl -s -X POST -H "$AH" "$API/api/purchase-orders/$POID/submit" >/dev/null
APR=$(curl -s -X POST -H "$AH" "$API/api/purchase-orders/$POID/approve")
RKID=$(echo "$APR" | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('autoCreatedReceiptId',''))")
[ -n "$RKID" ] && pass "审批自动建 RK id=$RKID" || fail "审批未建RK" "$APR"
RKCODE=$(curl -s -H "$AH" "$API/api/receipts/$RKID/detail" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'].get('code',''))")
echo "$RKCODE" | grep -qE '^RK-[0-9]{8}-[0-9]{5}$' && pass "RK 单号规范 $RKCODE" || fail "RK单号不规范" "$RKCODE"

echo ""
echo "== #8 分两次收货，执行明细各留一条 =="
DETAIL=$(curl -s -H "$AH" "$API/api/receipts/$RKID/detail")
LINEID=$(echo "$DETAIL" | python3 -c "import sys,json;d=json.load(sys.stdin)['data']['lines'];print(d[0]['id'])")
# 第一次收5，批次ABC
R1=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" "$API/api/receipts/$RKID/execute" \
  --data-binary "{\"lines\":[{\"receiptLineId\":$LINEID,\"batchNo\":\"ABC\",\"qty\":5}]}")
ST1=$(echo "$R1" | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('status','?'))")
[ "$ST1" = "PARTIAL_RECEIVED" ] && pass "第一次收5(ABC)→PARTIAL" || fail "第一次收货异常" "$R1"
# 第二次收5，批次DEF
R2=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" "$API/api/receipts/$RKID/execute" \
  --data-binary "{\"lines\":[{\"receiptLineId\":$LINEID,\"batchNo\":\"DEF\",\"qty\":5}]}")
ST2=$(echo "$R2" | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('status','?'))")
[ "$ST2" = "COMPLETED" ] && pass "第二次收5(DEF)→COMPLETED" || fail "第二次收货异常" "$R2"
# 详情执行明细应有2条 ABC/DEF
EXEC=$(curl -s -H "$AH" "$API/api/receipts/$RKID/detail")
ECNT=$(echo "$EXEC" | python3 -c "import sys,json;print(len(json.load(sys.stdin)['data'].get('executionLines',[])))")
[ "$ECNT" = "2" ] && pass "收货执行明细2条" || fail "执行明细数不对" "$ECNT"
BATCHES=$(echo "$EXEC" | python3 -c "
import sys,json
e=json.load(sys.stdin)['data'].get('executionLines',[])
print(','.join(sorted([x.get('batchNo','') for x in e])))")
[ "$BATCHES" = "ABC,DEF" ] && pass "执行明细批次含 ABC+DEF" || fail "批次不对" "$BATCHES"

echo ""
echo "== #1/#2 主数据编辑保存+操作日志（供应商）=="
UPD=$(curl -s -X PUT -H "$AH" -H "Content-Type: application/json" "$API/api/suppliers/$SUP" \
  --data-binary '{"contactPerson":"测试联系人V12","contactPhone":"13900000000"}')
CP=$(curl -s -H "$AH" "$API/api/suppliers/$SUP" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'].get('contactPerson',''))")
[ "$CP" = "测试联系人V12" ] && pass "供应商编辑已保存($CP)" || fail "供应商编辑未保存" "$CP"
LOG=$(curl -s -H "$AH" "$API/api/operation-logs?resourceType=supplier&resourceId=$SUP")
LOGN=$(echo "$LOG" | python3 -c "import sys,json;print(len(json.load(sys.stdin).get('data',[])))")
[ "$LOGN" -ge 1 ] && pass "供应商操作日志 $LOGN 条" || fail "供应商无操作日志" "$LOG"

echo ""
echo "== #6 采购详情返回供应商名+仓库名 =="
PODT=$(curl -s -H "$AH" "$API/api/purchase-orders/$POID/detail")
SNAME=$(echo "$PODT" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'].get('supplierName',''))")
WNAME=$(echo "$PODT" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'].get('warehouseName',''))")
[ -n "$SNAME" ] && pass "采购详情有供应商名($SNAME)" || fail "无供应商名" "$PODT"
[ -n "$WNAME" ] && [ "$WNAME" != "None" ] && pass "采购详情有仓库名($WNAME)" || fail "无仓库名" "$WNAME"

echo ""
echo "== #3 产品分类下拉数据 =="
CATS=$(curl -s -H "$AH" "$API/api/lookups/categories")
CATN=$(echo "$CATS" | python3 -c "import sys,json;print(len(json.load(sys.stdin).get('data',[])))")
[ "$CATN" -ge 8 ] && pass "分类下拉 $CATN 个" || fail "分类不足" "$CATN"

echo ""
echo "结果：通过 $PASS · 失败 $FAIL"
