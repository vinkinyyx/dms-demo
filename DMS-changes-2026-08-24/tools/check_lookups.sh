#!/bin/bash
TOKEN=$(curl -s -X POST http://localhost/api/auth/login -H 'Content-Type: application/json' -d '{"tenantCode":"default","username":"admin","password":"Sh123456"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
for ep in "/api/regions?size=5" "/api/lookups/dealers?size=5" "/api/product-categories?size=5" "/api/lookups/product-lines?size=5"; do
  echo "=== $ep ==="
  curl -s -H "Authorization: Bearer $TOKEN" "$ep" | python3 -c '
import sys,json
d=json.load(sys.stdin)
data=d.get("data",d)
recs=data.get("list") or data.get("records") or (data if isinstance(data,list) else [])
print("count=",len(recs))
for r in recs[:3]:
    print({k:r.get(k) for k in ("id","value","code","name","label") if k in r})
'
done
