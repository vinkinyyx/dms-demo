#!/bin/bash
TOKEN=$(curl -s -X POST http://localhost/api/auth/login -H 'Content-Type: application/json' -d '{"tenantCode":"default","username":"admin","password":"Sh123456"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
echo "=== /api/lookups/dealers with 15s timeout ==="
curl -s --max-time 15 -w "\nHTTP=%{http_code} time=%{time_total}\n" -H "Authorization: Bearer $TOKEN" "http://localhost/api/lookups/dealers" -o /tmp/lookup_dealers.json
echo "exit=$?"
ls -la /tmp/lookup_dealers.json
head -c 300 /tmp/lookup_dealers.json
echo
echo "=== /api/product-categories with 15s timeout ==="
curl -s --max-time 15 -w "\nHTTP=%{http_code} time=%{time_total}\n" -H "Authorization: Bearer $TOKEN" "http://localhost/api/product-categories" -o /tmp/lookup_cat.json
echo "exit=$?"
head -c 300 /tmp/lookup_cat.json
