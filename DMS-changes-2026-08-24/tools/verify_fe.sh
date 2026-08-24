#!/bin/bash
echo '---INDEX---'
curl -s -o /dev/null -w '%{http_code}\n' http://localhost/
echo '---NEW CHUNK---'
curl -s -o /dev/null -w '%{http_code}\n' http://localhost/assets/index-ClBLXgdv.js
echo '---LOGIN API---'
RESP=$(curl -s -X POST http://localhost/api/auth/login -H 'Content-Type: application/json' -d '{"tenantCode":"default","username":"admin","password":"Sh123456"}')
echo "$RESP" | head -c 300; echo
TOKEN=$(echo "$RESP" | python3 -c 'import sys,json
d=json.load(sys.stdin)
print(d.get("data",{}).get("accessToken") or d.get("data",{}).get("token") or d.get("accessToken") or "")')
echo "TOKEN_LEN=${#TOKEN}"
echo '---SAMPLE APIs---'
for ep in "products?page=1&size=3" "dealers?page=1&size=3" "product-prices?page=1&size=3" "authorizations?page=1&size=3" "orders?page=1&size=3" "inventory?page=1&size=3"; do
  code=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "http://localhost/api/$ep")
  echo "$code  /api/$ep"
done
echo '---DETAIL SAMPLES---'
for kind in products dealers product-prices authorizations orders; do
  id=$(curl -s -H "Authorization: Bearer $TOKEN" "http://localhost/api/$kind?page=1&size=1" | python3 -c 'import sys,json
d=json.load(sys.stdin)
recs=(d.get("data",{}).get("records") or d.get("data",{}).get("list") or d.get("records") or [])
print(recs[0].get("id") if recs else "")')
  if [ -n "$id" ]; then
    code=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "http://localhost/api/$kind/$id")
    echo "$code  /api/$kind/$id"
  else
    echo "NO_ID /api/$kind"
  fi
done
