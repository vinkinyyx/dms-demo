#!/bin/bash
# ==============================================================
# v3.1 全面回归测试脚本
# 覆盖:
#   A. 授权 (产品分类 categoryIds)
#   B. 库存状态机 (合格/待检/不合格)
#   C. 销售出库/红字销售出库 (库存增减 + 状态变化)
#   D. 采购入库/红字采购入库
#   E. 库存调整 IN/OUT
#   F. 分页/元数据/权限
# ==============================================================
set +e
API="http://localhost"
PASS=0; FAIL=0
pass(){ echo "  ✅ $1"; PASS=$((PASS+1)); }
fail(){ echo "  ❌ $1  $2"; FAIL=$((FAIL+1)); }
check_ok(){
  local d="$1"; local r="$2"
  local c=$(echo "$r" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))" 2>/dev/null)
  if [ "$c" = "0" ]; then pass "$d"; else fail "$d" "code=$c body=${r:0:200}"; fi
}
check_fail(){
  local d="$1"; local r="$2"
  local c=$(echo "$r" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))" 2>/dev/null)
  if [ "$c" != "0" ]; then pass "$d (正确拒绝, code=$c)"; else fail "$d" "应该失败但通过了"; fi
}
jval(){
  echo "$1" | python3 -c "import sys,json,pathlib;d=json.load(sys.stdin);p='$2'.split('.');v=d
for k in p:
 if v is None: break
 v = v[int(k)] if k.isdigit() else v.get(k)
print(v if v is not None else '')" 2>/dev/null
}

echo "==================================================="
echo "  v3.1 完整回归测试 (32 场景)"
echo "==================================================="
TOKEN=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"admin","password":"Sh123456"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AH="Authorization: Bearer $TOKEN"

echo ""
echo "== A. 授权(产品分类) =="

echo "[A1] 授权 - 缺产品分类应报错..."
DEALER=$(curl -s -H "$AH" "$API/api/lookups/dealers?limit=1" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['id'])")
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"dealerId\":$DEALER,\"terminalIds\":\"1\",\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\"}" \
  "$API/api/authorizations")
check_fail "缺产品分类拒绝" "$R"

echo "[A2] 授权 - 缺终端应报错..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"dealerId\":$DEALER,\"categoryIds\":\"1,2\",\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\"}" \
  "$API/api/authorizations")
check_fail "缺终端拒绝" "$R"

echo "[A3] 授权 - 缺有效期应报错..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"dealerId\":$DEALER,\"categoryIds\":\"1,2\",\"terminalIds\":\"1\"}" \
  "$API/api/authorizations")
check_fail "缺有效期拒绝" "$R"

echo "[A4] 授权 - validTo < validFrom 应报错..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"dealerId\":$DEALER,\"categoryIds\":\"1\",\"terminalIds\":\"1\",\"validFrom\":\"2026-12-31\",\"validTo\":\"2026-01-01\"}" \
  "$API/api/authorizations")
check_fail "有效期倒置拒绝" "$R"

echo "[A5] 授权 - 完整字段创建..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"dealerId\":$DEALER,\"categoryIds\":\"1,2,3\",\"terminalIds\":\"1,2\",\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\",\"authType\":\"ORDER\"}" \
  "$API/api/authorizations")
check_ok "授权创建成功" "$R"

echo "[A6] 授权 - 查询有效授权..."
R=$(curl -s -H "$AH" "$API/api/authorizations/effective/$DEALER")
HAS_CAT=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print('yes' if 'categoryIds' in d else 'no')" 2>/dev/null)
[ "$HAS_CAT" = "yes" ] && pass "返回 categoryIds" || fail "缺 categoryIds" "$HAS_CAT"

echo "[A7] 授权 - 产品分类检查..."
PROD=$(curl -s -H "$AH" "$API/api/lookups/products?limit=1" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['id'])")
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"dealerId\":$DEALER,\"productIds\":[$PROD]}" \
  "$API/api/authorizations/check-products")
check_ok "产品分类检查" "$R"

echo ""
echo "== B. 库存状态机 =="

echo "[B1] 库存状态字典..."
R=$(curl -s -H "$AH" "$API/api/dicts/INVENTORY_STATUS/items")
HAS_Q=$(echo "$R" | grep -c 'QUALIFIED')
HAS_P=$(echo "$R" | grep -c 'PENDING')
HAS_D=$(echo "$R" | grep -c 'DEFECTIVE')
[ "$HAS_Q" -gt 0 ] && [ "$HAS_P" -gt 0 ] && [ "$HAS_D" -gt 0 ] && pass "3种状态齐全" || fail "字典缺失" "Q=$HAS_Q P=$HAS_P D=$HAS_D"

echo "[B2] 库存返回 stockStatus 字段..."
R=$(curl -s -H "$AH" "$API/api/inventory?page=1&size=1")
HAS=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data']['list'];print('yes' if d and 'stockStatus' in d[0] else 'no')" 2>/dev/null)
[ "$HAS" = "yes" ] && pass "含 stockStatus" || fail "缺 stockStatus" "$HAS"

echo "[B3] 库存按状态汇总..."
R=$(curl -s -H "$AH" "$API/api/inventory-status/product/1")
check_ok "分状态汇总查询" "$R"

echo "[B4] 可用库存(仅QUALIFIED)..."
R=$(curl -s -H "$AH" "$API/api/inventory-status/available?productId=$PROD")
check_ok "可用库存查询" "$R"

echo ""
echo "== C. 销售出库业务逻辑 =="

# 准备：找有 QUALIFIED 库存的产品+仓库
INFO=$(curl -s -H "$AH" "$API/api/inventory?page=1&size=200" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['list']
for r in d:
  if r.get('stockStatus')=='QUALIFIED' and r.get('qty',0) and float(r['qty'])>10:
    print(f\"{r['productId']} {r['warehouseId']} {r['qty']}\")
    break")
QPROD=$(echo "$INFO" | awk '{print $1}')
QWH=$(echo "$INFO" | awk '{print $2}')
QQTY=$(echo "$INFO" | awk '{print $3}')
echo "  测试品: productId=$QPROD warehouseId=$QWH 合格库存=$QQTY"

TERM=$(curl -s -H "$AH" "$API/api/lookups/hospitals?limit=1" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['id'])")

echo "[C1] 销售出库 - 缺经销商应报错..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"terminalId\":$TERM,\"warehouseId\":$QWH,\"lines\":[{\"productId\":$QPROD,\"qty\":1}]}" \
  "$API/api/sales-out-ops")
check_fail "缺经销商拒绝" "$R"

echo "[C2] 销售出库 - 缺明细应报错..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"dealerId\":$DEALER,\"terminalId\":$TERM,\"warehouseId\":$QWH,\"lines\":[]}" \
  "$API/api/sales-out-ops")
check_fail "缺明细拒绝" "$R"

echo "[C3] 销售出库 - 红字未关联原单应报错..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"isRed\":true,\"dealerId\":$DEALER,\"terminalId\":$TERM,\"warehouseId\":$QWH,\"lines\":[{\"productId\":$QPROD,\"qty\":1}]}" \
  "$API/api/sales-out-ops")
check_fail "红字缺关联拒绝" "$R"

echo "[C4] 销售出库 - 正常出库(扣减合格库存)..."
BEFORE=$(curl -s -H "$AH" "$API/api/inventory-status/available?productId=$QPROD&warehouseId=$QWH" | jval "$(cat)" data.available 2>/dev/null || curl -s -H "$AH" "$API/api/inventory-status/available?productId=$QPROD&warehouseId=$QWH" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['available'])")
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"dealerId\":$DEALER,\"terminalId\":$TERM,\"warehouseId\":$QWH,\"lines\":[{\"productId\":$QPROD,\"qty\":2,\"unitPrice\":100}]}" \
  "$API/api/sales-out-ops")
check_ok "销售出库创建" "$R"
SO_ID=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])" 2>/dev/null)
echo "     创建出库单 ID=$SO_ID"

echo "[C5] 销售出库后 - 合格库存已扣减..."
AFTER=$(curl -s -H "$AH" "$API/api/inventory-status/available?productId=$QPROD&warehouseId=$QWH" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['available'])")
DIFF=$(python3 -c "print(float('$BEFORE') - float('$AFTER'))")
if [ "$(python3 -c "print(abs($DIFF - 2) < 0.01)")" = "True" ]; then
  pass "合格库存扣减 2 (BEFORE=$BEFORE AFTER=$AFTER)"
else
  fail "合格库存未准确扣减" "BEFORE=$BEFORE AFTER=$AFTER DIFF=$DIFF"
fi

echo "[C6] 销售出库 - 关联非正向单(应报错)..."
# 先创建红字单，再尝试关联到红字单
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"isRed\":true,\"dealerId\":$DEALER,\"terminalId\":$TERM,\"warehouseId\":$QWH,\"refSalesOutId\":$SO_ID,\"lines\":[{\"productId\":$QPROD,\"qty\":1}]}" \
  "$API/api/sales-out-ops")
check_ok "红字关联正向单成功" "$R"
RED_ID=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])" 2>/dev/null)
# 尝试再红字关联红字单
R2=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"isRed\":true,\"dealerId\":$DEALER,\"terminalId\":$TERM,\"warehouseId\":$QWH,\"refSalesOutId\":$RED_ID,\"lines\":[{\"productId\":$QPROD,\"qty\":1}]}" \
  "$API/api/sales-out-ops")
check_fail "红字关联红字单拒绝" "$R2"

echo "[C7] 红字销售出库 - 增加PENDING库存..."
BEFORE_P=$(curl -s -H "$AH" "$API/api/inventory-status/product/$QPROD?warehouseId=$QWH" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['pending'])")
# 已创建 RED_ID 增加了 1，验证 PENDING 已增加
# 再来一次
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"isRed\":true,\"dealerId\":$DEALER,\"terminalId\":$TERM,\"warehouseId\":$QWH,\"refSalesOutId\":$SO_ID,\"lines\":[{\"productId\":$QPROD,\"qty\":3}]}" \
  "$API/api/sales-out-ops")
check_ok "红字销售出库(+3 PENDING)" "$R"
AFTER_P=$(curl -s -H "$AH" "$API/api/inventory-status/product/$QPROD?warehouseId=$QWH" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['pending'])")
DIFF=$(python3 -c "print(float('$AFTER_P') - float('$BEFORE_P'))")
if [ "$(python3 -c "print(abs($DIFF - 3) < 0.01)")" = "True" ]; then
  pass "PENDING 库存增加 3 (BEFORE=$BEFORE_P AFTER=$AFTER_P)"
else
  fail "PENDING 库存未按预期增加" "DIFF=$DIFF"
fi

echo ""
echo "== D. 库存状态迁移 (待检→合格) =="

echo "[D1] 状态迁移 PENDING→QUALIFIED (合格)..."
BEFORE_Q=$(curl -s -H "$AH" "$API/api/inventory-status/product/$QPROD?warehouseId=$QWH" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['qualified'])")
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"productId\":$QPROD,\"warehouseId\":$QWH,\"qty\":1,\"srcStatus\":\"PENDING\",\"dstStatus\":\"QUALIFIED\"}" \
  "$API/api/inventory-status/move")
check_ok "状态迁移成功" "$R"
AFTER_Q=$(curl -s -H "$AH" "$API/api/inventory-status/product/$QPROD?warehouseId=$QWH" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['qualified'])")
DIFF=$(python3 -c "print(float('$AFTER_Q') - float('$BEFORE_Q'))")
if [ "$(python3 -c "print(abs($DIFF - 1) < 0.01)")" = "True" ]; then
  pass "QUALIFIED +1 (BEFORE=$BEFORE_Q AFTER=$AFTER_Q)"
else
  fail "QUALIFIED 未按预期增加" "DIFF=$DIFF"
fi

echo "[D2] 状态迁移 PENDING→DEFECTIVE (不合格)..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"productId\":$QPROD,\"warehouseId\":$QWH,\"qty\":1,\"srcStatus\":\"PENDING\",\"dstStatus\":\"DEFECTIVE\"}" \
  "$API/api/inventory-status/move")
check_ok "PENDING→DEFECTIVE" "$R"

echo "[D3] 状态迁移 - 源=目标应拒绝..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"productId\":$QPROD,\"warehouseId\":$QWH,\"qty\":1,\"srcStatus\":\"QUALIFIED\",\"dstStatus\":\"QUALIFIED\"}" \
  "$API/api/inventory-status/move")
check_fail "同状态迁移拒绝" "$R"

echo ""
echo "== E. 库存调整 =="

echo "[E1] 库存调整 IN (增加QUALIFIED)..."
BEFORE=$(curl -s -H "$AH" "$API/api/inventory-status/available?productId=$QPROD&warehouseId=$QWH" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['available'])")
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"warehouseId\":$QWH,\"category\":\"IN\",\"type\":\"STOCKTAKE\",\"remark\":\"盘盈\",\"lines\":[{\"productId\":$QPROD,\"qty\":5}]}" \
  "$API/api/inventory-adj-ops")
check_ok "库存调整 IN" "$R"
AFTER=$(curl -s -H "$AH" "$API/api/inventory-status/available?productId=$QPROD&warehouseId=$QWH" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['available'])")
DIFF=$(python3 -c "print(float('$AFTER') - float('$BEFORE'))")
if [ "$(python3 -c "print(abs($DIFF - 5) < 0.01)")" = "True" ]; then
  pass "QUALIFIED +5 (BEFORE=$BEFORE AFTER=$AFTER)"
else
  fail "IN 调整未准确增加" "DIFF=$DIFF"
fi

echo "[E2] 库存调整 OUT (扣减QUALIFIED)..."
BEFORE=$(curl -s -H "$AH" "$API/api/inventory-status/available?productId=$QPROD&warehouseId=$QWH" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['available'])")
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"warehouseId\":$QWH,\"category\":\"OUT\",\"type\":\"DAMAGE\",\"remark\":\"报损\",\"lines\":[{\"productId\":$QPROD,\"qty\":2}]}" \
  "$API/api/inventory-adj-ops")
check_ok "库存调整 OUT" "$R"
AFTER=$(curl -s -H "$AH" "$API/api/inventory-status/available?productId=$QPROD&warehouseId=$QWH" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['available'])")
DIFF=$(python3 -c "print(float('$BEFORE') - float('$AFTER'))")
if [ "$(python3 -c "print(abs($DIFF - 2) < 0.01)")" = "True" ]; then
  pass "QUALIFIED -2 (BEFORE=$BEFORE AFTER=$AFTER)"
else
  fail "OUT 调整未准确扣减" "DIFF=$DIFF"
fi

echo "[E3] 库存调整 - 非法 category 拒绝..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"warehouseId\":$QWH,\"category\":\"UNKNOWN\",\"lines\":[{\"productId\":$QPROD,\"qty\":1}]}" \
  "$API/api/inventory-adj-ops")
check_fail "非法category拒绝" "$R"

echo ""
echo "== F. 通用 (分页/列表) =="

echo "[F1] orders 列表..."
R=$(curl -s -H "$AH" "$API/api/orders?page=1&size=20&sort=updatedAt,desc")
check_ok "订单列表" "$R"

echo "[F2] purchase-orders 列表..."
R=$(curl -s -H "$AH" "$API/api/purchase-orders?page=1&size=20")
check_ok "采购单列表" "$R"

echo "[F3] stock-moves 列表..."
R=$(curl -s -H "$AH" "$API/api/stock-moves?page=1&size=10")
check_ok "库存移动列表" "$R"

echo "[F4] inventory-adjustments 列表..."
R=$(curl -s -H "$AH" "$API/api/inventory-adjustments?page=1&size=10")
check_ok "库存调整列表" "$R"

echo "[F5] receipts 列表..."
R=$(curl -s -H "$AH" "$API/api/receipts?page=1&size=10")
check_ok "收货入库列表" "$R"

echo "[F6] sales-outs 列表..."
R=$(curl -s -H "$AH" "$API/api/sales-outs?page=1&size=10")
check_ok "销售出库列表" "$R"

echo "[F7] 列表包含时间元数据..."
R=$(curl -s -H "$AH" "$API/api/orders?page=1&size=1")
HAS=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data']['list'];print('yes' if d and 'createdAt' in d[0] else 'no')" 2>/dev/null)
[ "$HAS" = "yes" ] && pass "含 createdAt" || fail "缺 createdAt" "$HAS"

echo "[F8] 字典管理..."
R=$(curl -s -H "$AH" "$API/api/dicts/types")
check_ok "字典分类列表" "$R"

echo "[F9] form_configs 字段配置..."
R=$(curl -s -H "$AH" "$API/api/form-configs/order")
check_ok "订单字段配置" "$R"

echo "[F10] 分类字典..."
R=$(curl -s -H "$AH" "$API/api/lookups/products?limit=1")
check_ok "产品查找" "$R"

echo ""
echo "==================================================="
echo "  结果：通过 $PASS · 失败 $FAIL"
echo "==================================================="
[ "$FAIL" = "0" ] && echo "🎉 全部通过！" || echo "⚠️  有失败项，请查看错误"
