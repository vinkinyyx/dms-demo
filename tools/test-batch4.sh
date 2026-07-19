#!/bin/bash
set +e
API="http://localhost"
PASS=0; FAIL=0
pass(){ echo "  ✅ $1"; PASS=$((PASS+1)); }
fail(){ echo "  ❌ $1  $2"; FAIL=$((FAIL+1)); }
check(){
  local d="$1"; local r="$2"
  local c=$(echo "$r" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))" 2>/dev/null)
  if [ "$c" = "0" ]; then pass "$d"; else fail "$d" "code=$c ${r:0:200}"; fi
}

echo "=== 批次 4 · P3 冒烟测试 ==="
TOKEN=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"admin","password":"'"${DMS_PWD:-Sh123456}"'"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AH="Authorization: Bearer $TOKEN"

echo "[US-E-01/02/03/06] 集成配置..."
R=$(curl -s -H "$AH" $API/api/integration/config)
check "集成配置列表" "$R"

echo "[US-E-07] 切换 ERP 模式为 real..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" --data-binary '{"mode":"real"}' $API/api/integration/config/erp/mode)
check "切换模式" "$R"

echo "[US-E-01] ERP 同步..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" -d '{"entity":"orders"}' $API/api/integration/erp/sync)
check "ERP 同步" "$R"

echo "[US-E-02] WMS 收货回执..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" -d '{"receiptId":1}' $API/api/integration/wms/receive-confirm)
check "WMS 回执" "$R"

echo "[US-E-03] HR 员工列表..."
R=$(curl -s -H "$AH" $API/api/integration/hr/employees?limit=3)
check "HR 员工" "$R"

echo "[US-E-06] UDI 上报..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" -d '{"productCode":"P001"}' $API/api/integration/udi/report)
check "UDI 上报" "$R"

echo "[US-B-Promo-07] 促销提交审批..."
R=$(curl -s -X POST -H "$AH" $API/api/promotions/1/submit)
check "促销提交" "$R"

echo "[US-B-Promo-07] 促销审批通过..."
R=$(curl -s -X POST -H "$AH" $API/api/promotions/1/approve)
check "促销审批" "$R"

echo "[US-C-10] 返利计算..."
DEALER=$(curl -s -H "$AH" "$API/api/lookups/dealers?limit=1" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['id'])")
R=$(curl -s -H "$AH" "$API/api/rebates/calculate?dealerId=$DEALER")
check "返利计算 (dealerId=$DEALER)" "$R"

echo "[US-B-20] 借货单列表..."
R=$(curl -s -H "$AH" $API/api/loans)
check "借货单列表" "$R"

echo "[US-C-08] Excel 导出 (CSV)..."
CODE=$(curl -sw '%{http_code}' -H "$AH" -o /tmp/orders.csv $API/api/reports/orders/export-csv)
[ "$CODE" = "200" ] && pass "订单 CSV 导出" || fail "订单 CSV 导出" "http=$CODE"

echo "[US-S-03] SEED 状态..."
R=$(curl -s -H "$AH" $API/api/system-ops/seed-status)
check "SEED 状态" "$R"

echo ""
echo "================================"
echo "  批次 4 结果：通过 $PASS · 失败 $FAIL"
echo "================================"
