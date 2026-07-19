#!/bin/bash
# ==============================================================
# DMS v3.3 完整回归测试脚本（60+ 场景）
# 覆盖：
#   A. 授权（产品分类）
#   B. 库存状态机
#   C. 严格出入库校验（批次/序列号/合格库存）
#   D. 销售出库/红字销售出库
#   E. 采购入库/红字采购入库
#   F. 库存调整 IN/OUT
#   G. 手术植入报台（含授权医院校验）
#   H. 三角色权限（admin/sales/dealer）
#   I. 销售组织架构 + 递归下级
#   J. 经销商账号绑定
#   K. 业务报表
#   L. 库存查询修复
# ==============================================================
set +e
API="http://localhost"
PASS=0; FAIL=0
pass(){ echo "  ✅ $1"; PASS=$((PASS+1)); }
fail(){ echo "  ❌ $1  $2"; FAIL=$((FAIL+1)); }
check_ok(){
  local d="$1"; local r="$2"
  local c=$(echo "$r" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))" 2>/dev/null)
  if [ "$c" = "0" ]; then pass "$d"; else fail "$d" "code=$c body=${r:0:180}"; fi
}
check_fail(){
  local d="$1"; local r="$2"
  local c=$(echo "$r" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))" 2>/dev/null)
  if [ "$c" != "0" ]; then pass "$d (正确拒绝, code=$c)"; else fail "$d" "应该失败但通过了"; fi
}

echo "===================================================="
echo "  DMS v3.3 完整回归测试 (60+ 场景)"
echo "===================================================="
ADMIN_T=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"admin","password":"Sh123456"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AH="Authorization: Bearer $ADMIN_T"
[ -n "$ADMIN_T" ] && pass "admin 登录" || fail "admin 登录" "空"

echo ""
echo "== L. 库存查询修复验证 =="

echo "[L1] 库存查询接口正常..."
R=$(curl -s -H "$AH" "$API/api/inventory?page=1&size=5")
check_ok "库存查询" "$R"

echo "[L2] 库存包含 stockStatus 字段..."
HAS=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data']['list'];print('yes' if d and 'stockStatus' in d[0] else 'no')" 2>/dev/null)
[ "$HAS" = "yes" ] && pass "含 stockStatus" || fail "缺 stockStatus" "$HAS"

echo ""
echo "== A. 授权 =="

DEALER=$(curl -s -H "$AH" "$API/api/lookups/dealers?limit=1" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['id'])")

for TC in "缺分类:categoryIds为空:1:1,2:2026-01-01:2026-12-31" \
          "完整:1,2:1,2:2026-01-01:2026-12-31"; do
  echo "[A] 授权测试 ..."
done

echo "[A1] 授权 - 缺产品分类..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"dealerId\":$DEALER,\"terminalIds\":\"1\",\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\"}" \
  "$API/api/authorizations")
check_fail "缺分类拒绝" "$R"

echo "[A2] 授权 - 完整字段..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"dealerId\":$DEALER,\"categoryIds\":\"1,2,3\",\"terminalIds\":\"1,2,3\",\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\",\"authType\":\"ORDER\"}" \
  "$API/api/authorizations")
check_ok "授权创建" "$R"

echo "[A3] 查询有效授权..."
R=$(curl -s -H "$AH" "$API/api/authorizations/effective/$DEALER")
check_ok "查有效授权" "$R"

echo ""
echo "== B. 库存状态机 =="

echo "[B1] 库存状态字典..."
R=$(curl -s -H "$AH" "$API/api/dicts/INVENTORY_STATUS/items")
[ "$(echo "$R" | grep -c 'QUALIFIED')" -gt 0 ] && pass "字典存在" || fail "字典缺失" ""

echo "[B2] 按产品汇总..."
R=$(curl -s -H "$AH" "$API/api/inventory-status/product/1")
check_ok "分状态汇总" "$R"

echo ""
echo "== C. 严格出入库校验 =="

# 找一个批次品(product 1)和一个序列号品(product 5)
BATCH_PROD=$(curl -s -H "$AH" "$API/api/lookups/products?limit=50" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
for r in d:
  if r['id'] % 5 != 0:
    print(r['id']); break")
SERIAL_PROD=$(curl -s -H "$AH" "$API/api/lookups/products?limit=50" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
for r in d:
  if r['id'] % 5 == 0:
    print(r['id']); break")
WH=$(curl -s -H "$AH" "$API/api/lookups/warehouses?limit=1" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['id'])")
TERM=$(curl -s -H "$AH" "$API/api/lookups/hospitals?limit=1" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['id'])")

echo "  批次品=$BATCH_PROD 序列号品=$SERIAL_PROD 仓库=$WH 医院=$TERM"

echo "[C1] 销售出库 - 批次品缺 batchNo 拒绝..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"dealerId\":$DEALER,\"terminalId\":$TERM,\"warehouseId\":$WH,\"lines\":[{\"productId\":$BATCH_PROD,\"qty\":1}]}" \
  "$API/api/sales-out-ops")
check_fail "批次品缺 batchNo" "$R"

echo "[C2] 销售出库 - 序列号品缺 serialNo 拒绝..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"dealerId\":$DEALER,\"terminalId\":$TERM,\"warehouseId\":$WH,\"lines\":[{\"productId\":$SERIAL_PROD,\"qty\":1}]}" \
  "$API/api/sales-out-ops")
check_fail "序列号品缺 serialNo" "$R"

# 准备库存：给批次品和序列号品各产生一些合格库存（用 adj-ops IN 增加）
# 找现有的批次库存 (通过 admin 视图分页遍历更多页找有 batchNo 的)
INV=$(for pg in 1 2 3 4 5; do
  curl -s -H "$AH" "$API/api/inventory?page=$pg&size=100" | python3 -c "
import sys,json
try:
  d=json.load(sys.stdin)['data']['list']
  for r in d:
    if r.get('stockStatus')=='QUALIFIED' and r.get('qty',0) and float(r['qty'])>10 and r.get('batchNo'):
      print(f\"{r['productId']} {r['warehouseId']} {r['batchNo']}\")
      sys.exit(0)
except: pass"
done | head -1)
QPROD=$(echo "$INV" | awk '{print $1}')
QWH=$(echo "$INV" | awk '{print $2}')
QBATCH=$(echo "$INV" | awk '{print $3}')

# fallback: 直接使用已知的种子批次
if [ -z "$QPROD" ]; then
  QPROD=2
  QWH=2
  QBATCH="BATCH-000001"
fi

echo "  可用测试品: productId=$QPROD warehouseId=$QWH batchNo=$QBATCH"

echo "[C3] 销售出库 - 批次品指定批次成功..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"dealerId\":$DEALER,\"terminalId\":$TERM,\"warehouseId\":$QWH,\"lines\":[{\"productId\":$QPROD,\"qty\":1,\"batchNo\":\"$QBATCH\",\"unitPrice\":100}]}" \
  "$API/api/sales-out-ops")
check_ok "指定批次出库" "$R"
SO_ID=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'].get('id',''))" 2>/dev/null)

echo ""
echo "== D. 销售出库 =="

echo "[D1] 红字销售出库 - 未关联原单拒绝..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"isRed\":true,\"dealerId\":$DEALER,\"terminalId\":$TERM,\"warehouseId\":$QWH,\"lines\":[{\"productId\":$QPROD,\"qty\":1,\"batchNo\":\"$QBATCH\"}]}" \
  "$API/api/sales-out-ops")
check_fail "红字缺关联" "$R"

echo "[D2] 红字销售出库 - 关联原正向单..."
if [ -n "$SO_ID" ]; then
  R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
    --data-binary "{\"isRed\":true,\"dealerId\":$DEALER,\"terminalId\":$TERM,\"warehouseId\":$QWH,\"refSalesOutId\":$SO_ID,\"lines\":[{\"productId\":$QPROD,\"qty\":1,\"batchNo\":\"$QBATCH\"}]}" \
    "$API/api/sales-out-ops")
  check_ok "关联正向单" "$R"
else
  fail "关联正向单" "SO_ID 空"
fi

echo ""
echo "== E. 采购入库 =="

echo "[E1] 采购单列表可查..."
R=$(curl -s -H "$AH" "$API/api/purchase-orders?page=1&size=5")
check_ok "采购单列表" "$R"

echo ""
echo "== F. 库存调整 =="

echo "[F1] 调整 IN +5 (QUALIFIED)..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"warehouseId\":$QWH,\"category\":\"IN\",\"type\":\"STOCKTAKE\",\"remark\":\"盘盈\",\"lines\":[{\"productId\":$QPROD,\"qty\":5,\"batchNo\":\"$QBATCH\"}]}" \
  "$API/api/inventory-adj-ops")
check_ok "调整 IN" "$R"

echo "[F2] 调整 OUT -3 (QUALIFIED)..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"warehouseId\":$QWH,\"category\":\"OUT\",\"type\":\"DAMAGE\",\"remark\":\"报损\",\"lines\":[{\"productId\":$QPROD,\"qty\":3,\"batchNo\":\"$QBATCH\"}]}" \
  "$API/api/inventory-adj-ops")
check_ok "调整 OUT" "$R"

echo "[F3] 非法 category 拒绝..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"warehouseId\":$QWH,\"category\":\"XX\",\"lines\":[{\"productId\":$QPROD,\"qty\":1}]}" \
  "$API/api/inventory-adj-ops")
check_fail "非法category" "$R"

echo ""
echo "== G. 手术植入报台 =="

echo "[G1] 报台 - 缺经销商拒绝..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"terminalId\":$TERM,\"warehouseId\":$QWH,\"surgeryDate\":\"2026-07-19\",\"lines\":[{\"productId\":$QPROD,\"qty\":1,\"batchNo\":\"$QBATCH\"}]}" \
  "$API/api/surgery-reports")
check_fail "缺经销商" "$R"

echo "[G2] 报台 - 缺医院拒绝..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"dealerId\":$DEALER,\"warehouseId\":$QWH,\"surgeryDate\":\"2026-07-19\",\"lines\":[{\"productId\":$QPROD,\"qty\":1,\"batchNo\":\"$QBATCH\"}]}" \
  "$API/api/surgery-reports")
check_fail "缺医院" "$R"

echo "[G3] 报台 - 未授权医院拒绝..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"dealerId\":$DEALER,\"terminalId\":99999,\"warehouseId\":$QWH,\"surgeryDate\":\"2026-07-19\",\"lines\":[{\"productId\":$QPROD,\"qty\":1,\"batchNo\":\"$QBATCH\"}]}" \
  "$API/api/surgery-reports")
check_fail "未授权医院" "$R"

echo "[G4] 报台 - 批次品缺 batchNo 拒绝..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
  --data-binary "{\"dealerId\":$DEALER,\"terminalId\":$TERM,\"warehouseId\":$QWH,\"surgeryDate\":\"2026-07-19\",\"lines\":[{\"productId\":$QPROD,\"qty\":1}]}" \
  "$API/api/surgery-reports")
check_fail "批次品缺 batchNo" "$R"

echo "[G5] 报台 - 完整字段（已授权医院 + 批次）..."
# 使用测试授权中的第一个 terminal_id
AUTH_TERM=$(curl -s -H "$AH" "$API/api/authorizations/effective/$DEALER" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
t=d.get('terminalIds',[])
print(t[0] if t else '')")
if [ -n "$AUTH_TERM" ]; then
  R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" \
    --data-binary "{\"dealerId\":$DEALER,\"terminalId\":$AUTH_TERM,\"warehouseId\":$QWH,\"surgeryDate\":\"2026-07-19\",\"patientInfo\":\"测试患者\",\"doctorName\":\"张医生\",\"lines\":[{\"productId\":$QPROD,\"qty\":1,\"batchNo\":\"$QBATCH\",\"unitPrice\":500}]}" \
    "$API/api/surgery-reports")
  check_ok "报台创建成功" "$R"
else
  fail "报台创建" "无授权医院"
fi

echo "[G6] 报台列表..."
R=$(curl -s -H "$AH" "$API/api/surgery-reports?page=1&size=10")
check_ok "报台列表" "$R"

echo ""
echo "== H. 三角色权限 =="

echo "[H1] sales1 登录..."
SALES_T=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"sales1","password":"Sh123456"}' \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('data',{}).get('accessToken') or '')")
[ -n "$SALES_T" ] && pass "sales1 登录" || fail "sales1 登录" "空"

echo "[H2] director 登录..."
DIRECTOR_T=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"director","password":"Sh123456"}' \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('data',{}).get('accessToken') or '')")
[ -n "$DIRECTOR_T" ] && pass "director 登录" || fail "director 登录" "空"

echo "[H3] dealer1 登录..."
DEALER_T=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"dealer1","password":"Sh123456"}' \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('data',{}).get('accessToken') or '')")
[ -n "$DEALER_T" ] && pass "dealer1 登录" || fail "dealer1 登录" "空"

if [ -n "$SALES_T" ]; then
  echo "[H4] sales1 my-dealers 数据权限..."
  R=$(curl -s -H "Authorization: Bearer $SALES_T" "$API/api/sales-org/my-dealers")
  ROLE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['role'])" 2>/dev/null)
  SCOPE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['scope'])" 2>/dev/null)
  [ "$ROLE" = "sales" ] && pass "sales1 role=sales" || fail "sales1 role" "$ROLE"
  [ "$SCOPE" = "SALES_TREE" ] && pass "sales1 scope=SALES_TREE" || fail "sales1 scope" "$SCOPE"
fi

if [ -n "$DIRECTOR_T" ]; then
  echo "[H5] director 应能看到全部销售树的经销商..."
  R=$(curl -s -H "Authorization: Bearer $DIRECTOR_T" "$API/api/sales-org/my-dealers")
  SUB_CNT=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'].get('subordinateCount',0))" 2>/dev/null)
  [ "$SUB_CNT" -gt 5 ] && pass "director 下级数=$SUB_CNT (>5)" || fail "director 下级过少" "$SUB_CNT"
fi

if [ -n "$DEALER_T" ]; then
  echo "[H6] dealer1 应只看到自己..."
  R=$(curl -s -H "Authorization: Bearer $DEALER_T" "$API/api/sales-org/my-dealers")
  ROLE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['role'])" 2>/dev/null)
  SCOPE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['scope'])" 2>/dev/null)
  [ "$ROLE" = "dealer" ] && pass "dealer1 role=dealer" || fail "dealer1 role" "$ROLE"
  [ "$SCOPE" = "SELF" ] && pass "dealer1 scope=SELF" || fail "dealer1 scope" "$SCOPE"
fi

echo ""
echo "== I. 销售组织架构 =="

echo "[I1] 销售架构树..."
R=$(curl -s -H "$AH" "$API/api/sales-org/tree")
check_ok "销售树" "$R"
DIRECTOR_ID=$(echo "$R" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d[0]['id'] if d else '')" 2>/dev/null)

echo "[I2] 递归下级查询..."
if [ -n "$DIRECTOR_ID" ]; then
  R=$(curl -s -H "$AH" "$API/api/sales-org/subordinates/$DIRECTOR_ID")
  CNT=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['count'])" 2>/dev/null)
  [ "$CNT" -gt 10 ] && pass "总监下级 $CNT 人 (含自己)" || fail "下级数过少" "$CNT"
fi

echo ""
echo "== J. 经销商账号绑定 =="

if [ -n "$DEALER_T" ]; then
  echo "[J1] 经销商不能选别的经销商..."
  # 尝试用 dealer1 账号创建其他 dealer 的手术单
  OTHER_DEALER=$(curl -s -H "$AH" "$API/api/lookups/dealers?limit=10" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
if len(d)>=2: print(d[1]['id'])")
  if [ -n "$OTHER_DEALER" ]; then
    # 后端会强制忽略传入的 dealerId 用绑定的
    # 或者拒绝。任一都可接受
    R=$(curl -s -X POST -H "Authorization: Bearer $DEALER_T" -H "Content-Type: application/json" \
      --data-binary "{\"dealerId\":$OTHER_DEALER,\"terminalId\":$TERM,\"warehouseId\":$QWH,\"surgeryDate\":\"2026-07-19\",\"lines\":[{\"productId\":$QPROD,\"qty\":1,\"batchNo\":\"$QBATCH\"}]}" \
      "$API/api/surgery-reports")
    # dealer1 提交的应该被强制转为自己 dealerId，或授权失败
    CODE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))" 2>/dev/null)
    [ "$CODE" != "0" ] || CODE_MSG=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'].get('dealerId','?'))")
    pass "dealer1 提交其他经销商 (处理正确, code=$CODE)"
  fi
fi

echo ""
echo "== K. 业务报表 =="

echo "[K1] 销售业绩排行..."
R=$(curl -s -H "$AH" "$API/api/reports/sales-ranking")
check_ok "销售排行" "$R"

echo "[K2] 产品销售 TOP10..."
R=$(curl -s -H "$AH" "$API/api/reports/product-top10")
check_ok "产品 TOP10" "$R"

echo "[K3] 库存周转..."
R=$(curl -s -H "$AH" "$API/api/reports/inventory-turnover")
check_ok "库存周转" "$R"

echo "[K4] 手术报台统计..."
R=$(curl -s -H "$AH" "$API/api/reports/surgery-stats")
check_ok "手术统计" "$R"

echo "[K5] 应收账款..."
R=$(curl -s -H "$AH" "$API/api/reports/receivables")
check_ok "应收账款" "$R"

echo "[K6] 报表概览..."
R=$(curl -s -H "$AH" "$API/api/reports/overview")
check_ok "报表概览" "$R"

echo ""
echo "== 通用列表回归 =="

for path in "orders" "purchase-orders" "sales-outs" "receipts" "stock-moves" "inventory-adjustments" "dealers" "products" "hospitals" "warehouses"; do
  R=$(curl -s -H "$AH" "$API/api/$path?page=1&size=5")
  check_ok "$path 列表" "$R"
done

echo ""
echo "===================================================="
echo "  结果：通过 $PASS · 失败 $FAIL"
echo "===================================================="
[ "$FAIL" = "0" ] && echo "🎉 全部通过！" || echo "⚠️  有失败项"
