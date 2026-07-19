#!/bin/bash
echo "=== 通过 nginx 测试 API ==="
TOKEN=$(curl -s -X POST http://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantCode":"default","username":"admin","password":"'"${DMS_PWD:-Sh123456}"'"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
echo "Token length: ${#TOKEN}"

echo ""
echo "--- 直接测后端 /api/orders ---"
curl -sw "\nHTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" 'http://localhost:8080/api/orders?page=1&size=3' | head -20

echo ""
echo "--- 通过 nginx /api/orders ---"
curl -sw "\nHTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" 'http://localhost/api/orders?page=1&size=3' | head -20

echo ""
echo "--- 通过 nginx /api/dealers ---"
curl -sw "\nHTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" 'http://localhost/api/dealers?page=1&size=3' | head -20
