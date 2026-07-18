#!/bin/bash
set +e
echo "=== 登录取 token ==="
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantCode":"default","username":"admin","password":"Sh123456"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
echo "TOKEN len: ${#TOKEN}"

echo ""
echo "=== 直接测 /api/orders （新窗口打开菜单相当于 GET）==="
curl -s -w "\nHTTP %{http_code}\n" \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/orders?page=1 | head -50
echo ""

echo "=== 不带 token 访问 /api/orders （浏览器新窗口打开菜单就是这样）==="
curl -s -w "\nHTTP %{http_code}\n" http://localhost:8080/orders?page=1 | head -20
echo ""

echo "=== 测 /api/dealers ==="
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/dealers?page=1 | head -30
echo ""

echo "=== backend 最近日志 ==="
docker logs --tail 30 dms-backend 2>&1 | grep -E "auth|orders|dealers|Access|401|403" | tail -10
