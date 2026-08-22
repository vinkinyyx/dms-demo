#!/usr/bin/env python3
"""
L2 API Smoke Test - tests backend endpoints directly.
Verifies login, key APIs, health, and common CRUD endpoints.
Usage: python automation_test/api_smoke.py [--base=http://43.128.145.141]
"""
import argparse
import json
import sys
import urllib.request
import urllib.error

DEFAULT_BASE = "http://43.128.145.141"
TIMEOUT = 10

results = []

def check(name, condition, detail=""):
    results.append({"name": name, "pass": bool(condition), "detail": str(detail)[:200]})
    print(("PASS" if condition else "FAIL") + " | " + name + (" | " + str(detail)[:150] if detail else ""))

def api_get(base, path, token=None):
    url = base + path
    headers = {}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, headers=headers, method="GET")
    try:
        r = urllib.request.urlopen(req, timeout=TIMEOUT)
        return r.status, json.loads(r.read().decode("utf-8", errors="replace"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")[:300]
        return e.code, body
    except Exception as e:
        return -1, str(e)

def api_post(base, path, data, token=None):
    url = base + path
    body = json.dumps(data).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=body, headers=headers, method="POST")
    try:
        r = urllib.request.urlopen(req, timeout=TIMEOUT)
        return r.status, json.loads(r.read().decode("utf-8", errors="replace"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")[:300]
        return e.code, body
    except Exception as e:
        return -1, str(e)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default=DEFAULT_BASE)
    args = parser.parse_args()
    base = args.base.rstrip("/")

    print("=== L2 API Smoke Test ===")
    print("Base:", base)
    print()

    # Health
    st, body = api_get(base, "/actuator/health")
    check("health endpoint", st == 200, str(body)[:100])

    # PC Login
    st, body = api_post(base, "/api/auth/login", {
        "username": "admin", "password": "Sh123456", "tenantCode": "default"
    })
    pc_token = None
    if st == 200 and isinstance(body, dict) and body.get("code") == 0:
        pc_token = body.get("data", {}).get("accessToken")
        check("PC login", bool(pc_token), "token length=" + str(len(pc_token or "")))
    else:
        check("PC login", False, "status=" + str(st) + " " + str(body)[:150])

    # Admin Login
    st, body = api_post(base, "/api/admin/auth/login", {
        "username": "admin", "password": "Sh123456"
    })
    admin_token = None
    if st == 200 and isinstance(body, dict) and body.get("code") == 0:
        admin_token = body.get("data", {}).get("accessToken")
        check("Admin login", bool(admin_token), "token length=" + str(len(admin_token or "")))
    else:
        check("Admin login", False, "status=" + str(st) + " " + str(body)[:150])

    # Key PC APIs
    if pc_token:
        pc_apis = [
            ("/api/products?page=1&size=5", "products list"),
            ("/api/dealers?page=1&size=5", "dealers list"),
            ("/api/hospitals?page=1&size=5", "hospitals list"),
            ("/api/warehouses?page=1&size=5", "warehouses list"),
            ("/api/sales-orders?page=1&size=5", "sales orders list"),
            ("/api/inventory?page=1&size=5", "inventory list"),
            ("/api/surgery-reports?page=1&size=5", "surgery reports list"),
            ("/api/promotions?page=1&size=5", "promotions list"),
            ("/api/contracts?page=1&size=5", "contracts list"),
            ("/api/authorizations?page=1&size=5", "authorizations list"),
            ("/api/roles", "roles list"),
            ("/api/users?page=1&size=5", "users list"),
            ("/api/sales-positions", "positions list"),
            ("/api/menus", "menus list"),
            ("/api/dicts/types", "dict types"),
            ("/api/operation-logs/fullchain/by-biz?bizType=product&bizId=1", "operation logs"),
            ("/api/approval/tasks/my-todo?page=1&size=5", "approval todo"),
            ("/api/ui/layout/products", "page layout products"),
            ("/api/dashboard/summary", "dashboard summary"),
            ("/api/dashboard/kpi", "dashboard kpi"),
        ]
        for path, label in pc_apis:
            st, body = api_get(base, path, pc_token)
            ok = st == 200 and (isinstance(body, dict) and body.get("code") in [0, None, 200] or isinstance(body, list))
            detail = "status=" + str(st)
            if not ok and isinstance(body, str):
                detail += " " + body[:80]
            elif isinstance(body, dict) and body.get("message"):
                detail += " " + str(body.get("message"))[:80]
            check("API: " + label, ok, detail)

    # Admin APIs
    if admin_token:
        admin_apis = [
            ("/api/admin/tenants?page=1&size=5", "admin tenants"),
            ("/api/admin/menus", "admin menus"),
            ("/api/admin/dicts/types", "admin dict types"),
            ("/api/admin/logs/api?page=1&size=5", "admin api logs"),
        ]
        for path, label in admin_apis:
            st, body = api_get(base, path, admin_token)
            ok = st == 200
            check("Admin API: " + label, ok, "status=" + str(st))

    # Summary
    failed = [r for r in results if not r["pass"]]
    print()
    print("=== Summary ===")
    print("Total:", len(results), "Passed:", len(results) - len(failed), "Failed:", len(failed))
    if failed:
        print("\nFailed:")
        for f in failed:
            print("  FAIL:", f["name"], "-", f["detail"])
    return 1 if failed else 0

if __name__ == "__main__":
    sys.exit(main())
