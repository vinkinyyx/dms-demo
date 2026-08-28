import json
import sys
import urllib.request
import urllib.error

BASE = "http://43.128.145.141"

results = []

def call(method, path, token=None, body=None, expect=(200,)):
    url = BASE + path
    data = None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            status = resp.getcode()
            raw = resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        status = e.code
        raw = e.read().decode("utf-8", "replace")
    except Exception as e:
        status = -1
        raw = str(e)
    try:
        parsed = json.loads(raw)
    except Exception:
        parsed = None
    ok = status in expect
    results.append((ok, method, path, status, (raw[:180] if not ok else "")))
    return status, parsed, raw

print("=" * 70)
print("DMS v4.4.1 API smoke test (via nginx entry)")
print("=" * 70)

# 0. health
st, _, _ = call("GET", "/actuator/health")

# 1. login
st, parsed, raw = call("POST", "/api/auth/login", body={
    "tenantCode": "default",
    "username": "admin",
    "password": "Sh123456"
})
token = None
if parsed and isinstance(parsed, dict):
    d = parsed.get("data") or parsed
    token = (d or {}).get("accessToken") or (d or {}).get("access_token") or (d or {}).get("token")
print("login token:", (token[:24] + "...") if token else "NONE -> " + raw[:200])
if not token:
    print("LOGIN FAILED, abort")
    for ok, m, p, s, err in results:
        print(("PASS" if ok else "FAIL"), m, p, s, err)
    sys.exit(1)

# 2. products list
call("GET", "/api/products?page=1&size=10", token=token)
# 3. consignment available (new endpoint)
st, parsed, raw = call("GET", "/api/consignment/available", token=token)
if parsed and isinstance(parsed, dict):
    rows = parsed.get("data") or []
    print(f"consignment/available rows: {len(rows) if isinstance(rows, list) else rows}")
# 4. suppliers (BUG-02 page exists; menu config is frontend but list endpoint sanity)
call("GET", "/api/suppliers?page=1&size=10", token=token)
# 5. dealers
call("GET", "/api/dealers?page=1&size=10", token=token)
# 6. warehouses
call("GET", "/api/warehouses?page=1&size=10", token=token)
# 7. orders list
call("GET", "/api/orders?page=1&size=10", token=token)
# 8. sales-outs list
call("GET", "/api/sales-outs?page=1&size=10", token=token)
# 9. approval templates (business types incl RMA_ORDER / INVOICE_ORDER)
st, parsed, raw = call("GET", "/api/approval/templates", token=token)
if parsed and isinstance(parsed, dict):
    d = parsed.get("data")
    print("approval/templates data type:", type(d).__name__)
# 10. me
call("GET", "/api/auth/me", token=token)
# 11. menu configs (regression: correct path is /api/menu-configs)
call("GET", "/api/menu-configs", token=token)
# 12. inventory
call("GET", "/api/inventory?page=1&size=10", token=token)
# 13. dealer credit profiles (consignment credit)
call("GET", "/api/dealer-credit", token=token)
# 14. tenant features (module switch: inventoryEnabled)
st, parsed, raw = call("GET", "/api/tenant/features", token=token)
if parsed and isinstance(parsed, dict):
    print("tenant/features:", raw[:200])

print()
fails = 0
for ok, m, p, s, err in results:
    mark = "PASS" if ok else "FAIL"
    if not ok:
        fails += 1
    print(f"{mark}  {m:5s} {s:>4}  {p}  {err}")

print()
print(f"TOTAL {len(results)}  FAIL {fails}")
sys.exit(1 if fails else 0)
