#!/bin/bash
TOKEN=$(curl -s -X POST http://localhost/api/auth/login -H 'Content-Type: application/json' -d '{"tenantCode":"default","username":"admin","password":"Sh123456"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
for ep in "/api/lookups/dealers" "/api/product-categories"; do
  echo "=== $ep (no params) ==="
  curl -s -w "\nHTTP=%{http_code}\n" -H "Authorization: Bearer $TOKEN" "$ep" | head -c 400
  echo
done
