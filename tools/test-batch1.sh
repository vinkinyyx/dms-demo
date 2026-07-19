#!/bin/bash
# 批次 1 P0 冒烟测试
set +e
API="http://localhost"
PASS=0; FAIL=0
pass(){ echo "  ✅ $1"; PASS=$((PASS+1)); }
fail(){ echo "  ❌ $1  $2"; FAIL=$((FAIL+1)); }

check(){
  local d="$1"; local r="$2"
  local c=$(echo "$r" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))" 2>/dev/null)
  if [ "$c" = "0" ]; then pass "$d"; else fail "$d" "code=$c ${r:0:150}"; fi
}

echo "=== 批次 1 · P0 冒烟测试 ==="
TOKEN=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"admin","password":"'"${DMS_PWD:-Sh123456}"'"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AH="Authorization: Bearer $TOKEN"

echo "[US-A-13] 合同 HTML 打印视图..."
CODE=$(curl -sw '%{http_code}' -o /tmp/print.html -H "$AH" "$API/api/contracts/1/print-view")
if [ "$CODE" = "200" ]; then
  if grep -q "合同" /tmp/print.html; then pass "US-A-13 合同打印视图"; else fail "US-A-13" "无合同关键字"; fi
else
  fail "US-A-13" "http=$CODE"
fi

echo "[US-A-14] 发送签章验证码..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" --data-binary '{"phone":"13800001234"}' "$API/api/contracts/1/send-sign-code")
check "US-A-14 发送验证码" "$R"
CODE_VAL=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['mockCode'])" 2>/dev/null)

echo "[US-A-14] 用验证码签章..."
R=$(curl -s -X POST -H "$AH" -H "Content-Type: application/json" --data-binary "{\"code\":\"$CODE_VAL\"}" "$API/api/contracts/1/sign")
check "US-A-14 签章验证" "$R"

echo "[US-A-15] ERP 归档..."
R=$(curl -s -X POST -H "$AH" "$API/api/contracts/1/archive-to-erp")
check "US-A-15 ERP 归档" "$R"

echo "[US-B-22] UDI 追溯（序列号）..."
R=$(curl -s -H "$AH" "$API/api/traceability/by-serial?serialNo=SN-2026-070001")
check "US-B-22 序列号追溯" "$R"

echo "[US-B-22] UDI 追溯（批次号）..."
R=$(curl -s -H "$AH" "$API/api/traceability/by-batch?batchNo=BATCH-2026-01")
check "US-B-22 批次号追溯" "$R"

echo "[US-A-02] 产品引用检查..."
R=$(curl -s -H "$AH" "$API/api/reference-check/product/1")
check "US-A-02 产品引用检查" "$R"

echo "[US-A-02] 经销商引用检查..."
R=$(curl -s -H "$AH" "$API/api/reference-check/dealer/1")
check "US-A-02 经销商引用检查" "$R"

echo "[US-M-01] 微信登录 H5 页..."
CODE=$(curl -sw '%{http_code}' -o /dev/null $API/mobile/login.html)
[ "$CODE" = "200" ] && pass "US-M-01 mobile/login.html" || fail "US-M-01" "http=$CODE"

echo "[US-M-02~07] H5 6 张页..."
for p in home orders inventory receipt report messages; do
  CODE=$(curl -sw '%{http_code}' -o /dev/null $API/mobile/$p.html)
  [ "$CODE" = "200" ] && pass "mobile/$p.html" || fail "mobile/$p.html" "http=$CODE"
done

echo ""
echo "================================"
echo "  批次 1 结果：通过 $PASS · 失败 $FAIL"
echo "================================"
