#!/bin/bash
# 完整端到端验证脚本
set +e

echo "=== 1) 登录（通过 nginx /api/auth/login）==="
LOGIN_RESP=$(curl -s -X POST http://localhost/api/auth/login \
  -H 'Content-Type: application/json' \
  --data-binary '{"tenantCode":"default","username":"admin","password":"'"${DMS_PWD:-Sh123456}"'"}')
echo "$LOGIN_RESP" | head -c 400
echo
TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('accessToken',''))")
echo "Token len: ${#TOKEN}"

if [ -z "$TOKEN" ]; then
  echo "❌ 登录失败, 终止"
  exit 1
fi

echo ""
echo "=== 2) /api/orders 通过 nginx ==="
curl -sw "\nHTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" 'http://localhost/api/orders?page=1&size=3' | head -c 800
echo
echo ""
echo "=== 3) /api/dealers ==="
curl -sw "\nHTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" 'http://localhost/api/dealers?page=1&size=3' | head -c 800
echo
echo ""
echo "=== 4) /api/products ==="
curl -sw "\nHTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" 'http://localhost/api/products?page=1&size=3' | head -c 800
echo
echo ""
echo "=== 5) /api/inventory ==="
curl -sw "\nHTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" 'http://localhost/api/inventory?page=1&size=3' | head -c 800
echo
echo ""
echo "=== 6) /api/contracts ==="
curl -sw "\nHTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" 'http://localhost/api/contracts?page=1&size=3' | head -c 800
