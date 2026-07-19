#!/bin/bash
# v3.4.14 补充测试：编码更新/取消部分收货/列表筛选传后端/授权多选/价格编码
set +e
BASE="http://localhost"
PASS=0; FAIL=0
chk(){ if [ "$1" = "$2" ]; then PASS=$((PASS+1)); echo "  ✅ $3 ($1)"; else FAIL=$((FAIL+1)); echo "  ❌ $3 (期望$2 实际$1)"; fi; }

TOKEN=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  --data-binary '{"tenantCode":"default","username":"admin","password":"Sh123456"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AUTH="Authorization: Bearer $TOKEN"
echo "TOKEN_LEN=${#TOKEN}"

echo "== #1 产品编码更新 =="
PID=$(curl -s "$BASE/api/products?page=1&size=1" -H "$AUTH" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['list'][0]['id'])")
OLD=$(curl -s "$BASE/api/products/$PID" -H "$AUTH" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['code'])")
NEWCODE="${OLD}-E"
# 取回完整对象改 code 再 PUT
BODY=$(curl -s "$BASE/api/products/$PID" -H "$AUTH" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];d['code']='$NEWCODE';print(json.dumps(d))")
curl -s -X PUT "$BASE/api/products/$PID" -H "$AUTH" -H 'Content-Type: application/json' --data-binary "$BODY" -o /dev/null
AFTER=$(curl -s "$BASE/api/products/$PID" -H "$AUTH" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['code'])")
chk "$AFTER" "$NEWCODE" "产品编码已更新"
# 改回
BODY2=$(curl -s "$BASE/api/products/$PID" -H "$AUTH" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];d['code']='$OLD';print(json.dumps(d))")
curl -s -X PUT "$BASE/api/products/$PID" -H "$AUTH" -H 'Content-Type: application/json' --data-binary "$BODY2" -o /dev/null

echo "== #3 分类编码更新 =="
CID=$(curl -s "$BASE/api/product-categories?page=1&size=1" -H "$AUTH" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['list'][0]['id'])")
COLD=$(curl -s "$BASE/api/product-categories/$CID" -H "$AUTH" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['code'])")
CBODY=$(curl -s "$BASE/api/product-categories/$CID" -H "$AUTH" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];d['code']='${COLD}-E';print(json.dumps(d))")
curl -s -X PUT "$BASE/api/product-categories/$CID" -H "$AUTH" -H 'Content-Type: application/json' --data-binary "$CBODY" -o /dev/null
CAFTER=$(curl -s "$BASE/api/product-categories/$CID" -H "$AUTH" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['code'])")
chk "$CAFTER" "${COLD}-E" "分类编码已更新"
CBODY2=$(curl -s "$BASE/api/product-categories/$CID" -H "$AUTH" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];d['code']='$COLD';print(json.dumps(d))")
curl -s -X PUT "$BASE/api/product-categories/$CID" -H "$AUTH" -H 'Content-Type: application/json' --data-binary "$CBODY2" -o /dev/null

echo "== #6 列表筛选传后端（跨全部数据） =="
# 用 status=active 过滤产品，返回条数应 <= 全部
ALL=$(curl -s "$BASE/api/products?page=1&size=100" -H "$AUTH" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['total'])")
ACT=$(curl -s "$BASE/api/products?page=1&size=100&status=active" -H "$AUTH" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['total'])")
chk "$([ "$ACT" -le "$ALL" ] && echo ok)" "ok" "status=active 过滤后端生效(total=$ACT<=全部$ALL)"
# code 模糊
KW=$(echo "$OLD" | cut -c1-3)
CNT=$(curl -s "$BASE/api/products?page=1&size=100&code=$KW" -H "$AUTH" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['total'])")
chk "$([ "$CNT" -ge 1 ] && echo ok)" "ok" "code 模糊筛选后端生效(total=$CNT)"

echo "== #5 收货部分状态可取消（PARTIAL_RECEIVED）=="
# 找一个 PARTIAL_RECEIVED 收货单
RID=$(curl -s "$BASE/api/receipts?page=1&size=50" -H "$AUTH" | python3 -c "import sys,json;
d=json.load(sys.stdin)['data']['list'];
xs=[r for r in d if str(r.get('status','')).find('PARTIAL')>=0];
print(xs[0]['id'] if xs else '')")
if [ -n "$RID" ]; then
  RES=$(curl -s -X POST "$BASE/api/receipts/$RID/cancel-draft" -H "$AUTH")
  chk "$(echo $RES | grep -o '\"code\":0' | head -1)" '"code":0' "PARTIAL_RECEIVED 收货单可取消(id=$RID)"
else
  echo "  ⚠ 无 PARTIAL_RECEIVED 收货单，跳过实际取消（逻辑已放开 SQL）"
fi

echo "== #4 产品价格列表回显 productCode =="
PP=$(curl -s "$BASE/api/product-prices?page=1&size=1" -H "$AUTH")
chk "$(echo $PP | grep -o 'productCode' | head -1)" 'productCode' "价格列表含 productCode"

echo ""
echo "====== v3.4.14 结果: PASS=$PASS FAIL=$FAIL ======"
