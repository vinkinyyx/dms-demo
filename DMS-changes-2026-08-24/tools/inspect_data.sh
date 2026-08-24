#!/bin/bash
TOKEN=$(curl -s -X POST http://localhost/api/auth/login -H 'Content-Type: application/json' -d '{"tenantCode":"default","username":"admin","password":"Sh123456"}' | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d["data"]["accessToken"])')
echo '=== AUTHORIZATION 1 (H1: dealer should show name) ==='
curl -s -H "Authorization: Bearer $TOKEN" http://localhost/api/authorizations/1 | python3 -m json.tool | grep -iE 'dealer|partner' | head
echo '=== PRODUCT-PRICE 24 (H2/M5/L2: partner name, currency, product) ==='
curl -s -H "Authorization: Bearer $TOKEN" http://localhost/api/product-prices/24 | python3 -m json.tool | grep -iE 'partner|dealer|currency|product|priceType|priceContext|sku' | head -20
echo '=== ORDER 234 (M1/M8: status/orderType) ==='
curl -s -H "Authorization: Bearer $TOKEN" http://localhost/api/orders/234 | python3 -m json.tool | grep -iE 'status|orderType' | head
echo '=== PRODUCT 1 (M2/M3: productType/category/productLine) ==='
curl -s -H "Authorization: Bearer $TOKEN" http://localhost/api/products/1 | python3 -m json.tool | grep -iE 'productType|category|productLine|productName' | head
echo '=== DEALER 7 (M4: region) ==='
curl -s -H "Authorization: Bearer $TOKEN" http://localhost/api/dealers/7 | python3 -m json.tool | grep -iE 'region' | head
echo '=== INVENTORY first record (M6/M7/L1) ==='
curl -s -H "Authorization: Bearer $TOKEN" "http://localhost/api/inventory?page=1&size=1" | python3 -m json.tool | head -60
