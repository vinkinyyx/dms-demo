#!/bin/bash
echo "=== 1) 登录 ==="
TOKEN=$(curl -s -X POST http://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"admin","password":"'"${DMS_PWD:-Sh123456}"'"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
echo "Token len: ${#TOKEN}"

echo ""
echo "=== 2) /api/system/stats ==="
curl -sw "\nHTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" http://localhost/api/system/stats | head -c 500
echo ""

echo "=== 3) /api/system/audit-logs ==="
curl -sw "\nHTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" 'http://localhost/api/system/audit-logs?page=1&size=3' | head -c 500
echo ""

echo "=== 4) /api/system/login-logs ==="
curl -sw "\nHTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" 'http://localhost/api/system/login-logs?page=1&size=3' | head -c 500
echo ""

echo "=== 5) /api/system/notifications ==="
curl -sw "\nHTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" 'http://localhost/api/system/notifications?page=1&size=3' | head -c 500
echo ""

echo "=== 6) /api/system/settings ==="
curl -sw "\nHTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" http://localhost/api/system/settings | head -c 500
echo ""

echo "=== 7) /api/system/dicts ==="
curl -sw "\nHTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" http://localhost/api/system/dicts | head -c 500
echo ""

echo "=== 8) POST /api/products (create) ==="
curl -sw "\nHTTP %{http_code}\n" -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  --data-binary '{"code":"TEST-001","nameCn":"测试产品","spec":"S1","unit":"个","currentPrice":100,"taxRate":0.13,"udiRequired":false,"warnMonths":3,"safetyQty":5,"minOrderQty":1,"status":"active"}' \
  http://localhost/api/products | head -c 400
echo ""
