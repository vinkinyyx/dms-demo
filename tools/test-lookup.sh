#!/bin/bash
API="http://localhost"
TOKEN=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  --data-binary '{"tenantCode":"default","username":"admin","password":"Sh123456"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
echo "=== 无 dealerId ==="
curl -s -H "Authorization: Bearer $TOKEN" "$API/api/lookups/products?limit=3" | python3 -m json.tool | head -30
echo ""
echo "=== 有 dealerId=1 ==="
curl -s -H "Authorization: Bearer $TOKEN" "$API/api/lookups/products?dealerId=1&limit=3" | python3 -m json.tool | head -30
