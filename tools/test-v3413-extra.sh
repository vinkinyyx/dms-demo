#!/bin/bash
# v3.4.13 补充测试：时区/操作日志/库存调整/库存移动/详情源单/分类名称
set +e
BASE="http://localhost"
PASS=0; FAIL=0
chk(){ if [ "$1" = "$2" ]; then PASS=$((PASS+1)); echo "  ✅ $3 ($1)"; else FAIL=$((FAIL+1)); echo "  ❌ $3 (期望$2 实际$1)"; fi; }

TOKEN=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  --data-binary '{"tenantCode":"default","username":"admin","password":"'"${DMS_PWD:-Sh123456}"'"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AUTH="Authorization: Bearer $TOKEN"
echo "TOKEN_LEN=${#TOKEN}"

echo "== 基础 lookups =="
chk "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/lookups/warehouses" -H "$AUTH")" 200 "仓库下拉"
chk "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/lookups/products" -H "$AUTH")" 200 "产品下拉"
chk "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/lookups/categories" -H "$AUTH")" 200 "分类下拉"

WH=$(curl -s "$BASE/api/lookups/warehouses" -H "$AUTH" | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)
WH2=$(curl -s "$BASE/api/lookups/warehouses" -H "$AUTH" | tr ',' '\n' | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -2 | tail -1)
PROD=$(curl -s "$BASE/api/lookups/products" -H "$AUTH" | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)
echo "WH=$WH WH2=$WH2 PROD=$PROD"

echo "== 需求11 库存调整（扁平结构+序列号+详情）=="
ADJ=$(curl -s -X POST "$BASE/api/inventory-adjustments" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"warehouseId\":$WH,\"category\":\"IN\",\"type\":\"STOCKTAKE\",\"stockStatus\":\"QUALIFIED\",\"remark\":\"测试盘盈\",\"lines\":[{\"productId\":$PROD,\"batchNo\":\"ADJB1\",\"serialNo\":\"\",\"qty\":5}]}")
echo "  调整返回: $(echo $ADJ | cut -c1-120)"
ADJID=$(echo "$ADJ" | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)
chk "$(echo $ADJ | grep -o '"code":0' | head -1)" '"code":0' "库存调整创建"
chk "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/inventory-adjustments/$ADJID/detail" -H "$AUTH")" 200 "库存调整详情"
DET=$(curl -s "$BASE/api/inventory-adjustments/$ADJID/detail" -H "$AUTH")
chk "$(echo $DET | grep -o 'ADJB1' | head -1)" 'ADJB1' "调整详情含批次"

echo "== 需求12 库存移动（源→目标+详情）=="
MOV=$(curl -s -X POST "$BASE/api/stock-moves" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"fromWarehouseId\":$WH,\"toWarehouseId\":$WH2,\"stockStatus\":\"QUALIFIED\",\"remark\":\"测试移库\",\"lines\":[{\"productId\":$PROD,\"batchNo\":\"ADJB1\",\"serialNo\":\"\",\"qty\":2}]}")
echo "  移动返回: $(echo $MOV | cut -c1-120)"
MOVID=$(echo "$MOV" | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)
chk "$(echo $MOV | grep -o '"code":0' | head -1)" '"code":0' "库存移动创建"
chk "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/stock-moves/$MOVID/detail" -H "$AUTH")" 200 "库存移动详情"

echo "== 需求1/8 操作日志 =="
chk "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/operation-logs?resourceType=inventory_adjustment&resourceId=$ADJID" -H "$AUTH")" 200 "调整操作日志接口"
LOG=$(curl -s "$BASE/api/operation-logs?resourceType=inventory_adjustment&resourceId=$ADJID" -H "$AUTH")
chk "$(echo $LOG | grep -o 'CREATE' | head -1)" 'CREATE' "调整日志含CREATE"

echo "== 需求5 时区（列表时间为北京时间格式 yyyy-MM-dd HH:mm:ss）=="
LST=$(curl -s "$BASE/api/suppliers?page=1&size=1" -H "$AUTH")
chk "$(echo $LST | grep -oE '"createdAt":"20[0-9]{2}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}"' | head -1 | grep -c ':')" 1 "供应商列表时间为空格分隔北京时间"

echo "== 需求9 出库详情带源单 / 需求2 分类名称 =="
SO=$(curl -s "$BASE/api/sales-outs?page=1&size=1" -H "$AUTH" | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)
if [ -n "$SO" ]; then
  chk "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/sales-outs/$SO/detail" -H "$AUTH")" 200 "销售出库详情"
fi
PID=$(curl -s "$BASE/api/products?page=1&size=1" -H "$AUTH" | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)
if [ -n "$PID" ]; then
  PD=$(curl -s "$BASE/api/products/$PID" -H "$AUTH")
  chk "$(echo $PD | grep -o 'categoryName' | head -1)" 'categoryName' "产品详情含categoryName字段"
fi

echo ""
echo "====== v3.4.13 结果: PASS=$PASS FAIL=$FAIL ======"
