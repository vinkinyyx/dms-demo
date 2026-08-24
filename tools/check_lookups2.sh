#!/bin/bash
TOKEN=$(curl -s -X POST http://localhost/api/auth/login -H 'Content-Type: application/json' -d '{"tenantCode":"default","username":"admin","password":"Sh123456"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
for ep in "/api/regions?size=5" "/api/regions?page=1&size=5" "/api/lookups/dealers?size=5" "/api/product-categories?size=5" "/api/lookups/product-lines?size=5"; do
  echo "=== $ep ==="
  curl -s -w "\nHTTP=%{http_code}\n" -H "Authorization: Bearer $TOKEN" "$ep" | head -c 500
  echo
done
