#!/bin/bash
TOKEN=$(curl -s -X POST http://localhost/api/auth/login -H 'Content-Type: application/json' -d '{"tenantCode":"default","username":"admin","password":"Sh123456"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
for ep in "/api/lookups/dealers" "/api/lookups/warehouses" "/api/lookups/products" "/api/product-categories"; do
  echo "=== $ep ==="
  curl -s -H "Authorization: Bearer $TOKEN" "$ep?size=3" | python3 -c '
import sys,json
d=json.load(sys.stdin)
data=d.get("data",d)
recs=data.get("list") or data.get("records") or (data if isinstance(data,list) else [])
print("type=",type(data).__name__,"count=",len(recs))
for r in recs[:3]:
    print({k:r.get(k) for k in ("id","value","code","name","label") if k in r})
'
done
