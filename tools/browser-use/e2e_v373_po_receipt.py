"""v3.7.3 E2E: 采购订单 + 收货入库 全链路验证 (API 层)"""
import os, sys, json, requests, time
BACKEND = os.environ.get("DMS_BACKEND", "http://8.133.193.238:8082")
TENANT = "default"
USER = "admin"
PWD = "Sh123456"

def login():
    r = requests.post(BACKEND + "/api/auth/login",
                      json={"tenantCode":TENANT, "username":USER, "password":PWD}, timeout=10)
    r.raise_for_status()
    return r.json()["data"]["accessToken"]

def hdr(tok, ct=False):
    h = {"Authorization": f"Bearer {tok}"}
    if ct: h["Content-Type"] = "application/json"
    return h

def check(name, cond, extra=""):
    tag = "PASS" if cond else "FAIL"
    print(f"[{tag}] {name} {extra}")
    return cond

def main():
    print(f"BACKEND={BACKEND}")
    tok = login()
    print("[login] OK")

    # R9: seed data 4 scenes
    r = requests.get(BACKEND + "/api/receipts?page=1&size=50", headers=hdr(tok), timeout=10)
    receipts = r.json()["data"]["list"]
    v373 = [x for x in receipts if x["code"].startswith("RK-V373-")]
    statuses = {x["code"]: x["status"] for x in v373}
    ok = True
    ok &= check("R9.A COMPLETED", statuses.get("RK-V373-A") == "COMPLETED", statuses.get("RK-V373-A", "-"))
    ok &= check("R9.B PARTIAL_RECEIVED", statuses.get("RK-V373-B") == "PARTIAL_RECEIVED", statuses.get("RK-V373-B", "-"))
    ok &= check("R9.C DRAFT", statuses.get("RK-V373-C") == "DRAFT", statuses.get("RK-V373-C", "-"))
    ok &= check("R9.D CANCELLED", statuses.get("RK-V373-D") == "CANCELLED", statuses.get("RK-V373-D", "-"))

    # R6: receipt detail should contain sourcePo with full fields
    rk_a_id = next((x["id"] for x in v373 if x["code"] == "RK-V373-A"), None)
    if rk_a_id:
        r = requests.get(f"{BACKEND}/api/receipts/{rk_a_id}", headers=hdr(tok), timeout=10)
        d = r.json()["data"]
        sp = d.get("sourcePo") or {}
        for k in ["code","status","supplierName","warehouseName","expectedDate",
                  "amountInclTax","finalAmount","taxAmount","createdByName","approvedByName"]:
            ok &= check(f"R6 sourcePo.{k}", sp.get(k) is not None, str(sp.get(k)))

    # R1 + R2: create PO -> submit -> approve -> auto receipt
    body = {"orderType":"NORMAL","supplierId":5,"warehouseId":1,"expectedDate":"2026-08-01",
            "remark":"E2E R1+R2","lines":[{"productId":1,"qty":4,"unitPrice":50,"taxRate":0.13}]}
    r = requests.post(BACKEND+"/api/purchase-orders", json=body, headers=hdr(tok, True), timeout=10)
    ok &= check("R1.create", r.status_code==200 and r.json()["code"]==0, r.text[:120])
    po_id = r.json()["data"]["id"]

    r = requests.post(f"{BACKEND}/api/purchase-orders/{po_id}/submit", headers=hdr(tok), timeout=10)
    ok &= check("R1.submit->SUBMITTED", r.json()["data"]["newStatus"]=="SUBMITTED")

    r = requests.post(f"{BACKEND}/api/purchase-orders/{po_id}/approve", headers=hdr(tok), timeout=10)
    d = r.json()["data"]
    ok &= check("R1.approve->APPROVED", d.get("newStatus")=="APPROVED")
    rk_id = d.get("autoCreatedReceiptId")
    ok &= check("R2 auto-created receipt", rk_id is not None, f"rk_id={rk_id}")

    r = requests.get(f"{BACKEND}/api/receipts/{rk_id}", headers=hdr(tok), timeout=10)
    rc = r.json()["data"]
    ok &= check("R2.receipt DRAFT", rc.get("status")=="DRAFT")
    ok &= check("R2.receipt.sourcePoId matches", rc.get("sourcePoId")==po_id)
    ok &= check("R2.receipt.sourcePo present", rc.get("sourcePo") is not None)

    print("\n=====")
    print("OVERALL:", "PASS" if ok else "FAIL")
    return 0 if ok else 1

if __name__ == "__main__":
    sys.exit(main())
