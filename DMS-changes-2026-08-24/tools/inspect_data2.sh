#!/bin/bash
TOKEN=$(curl -s -X POST http://localhost/api/auth/login -H 'Content-Type: application/json' -d '{"tenantCode":"default","username":"admin","password":"Sh123456"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
echo '=== DEALER 7 FULL ==='
curl -s -H "Authorization: Bearer $TOKEN" http://localhost/api/dealers/7 | python3 -m json.tool
echo '=== PRICES partnerId distribution ==='
curl -s -H "Authorization: Bearer $TOKEN" 'http://localhost/api/product-prices?page=1&size=50' | python3 -c '
import sys,json
d=json.load(sys.stdin)
recs=d["data"].get("list") or d["data"].get("records") or []
for r in recs[:20]:
    print(r.get("id"), "partnerId=",r.get("partnerId"), "partnerName=",repr(r.get("partnerName")), r.get("productCode"))
'
