#!/bin/bash
TOKEN=$(curl -s -X POST http://localhost/api/auth/login -H 'Content-Type: application/json' -d '{"tenantCode":"default","username":"admin","password":"Sh123456"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
echo "=== /api/regions ==="
curl -sv -H "Authorization: Bearer $TOKEN" "http://localhost/api/regions" 2>&1 | tail -20
