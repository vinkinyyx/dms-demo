import json
import sys
import uuid
import urllib.request
import urllib.error

import paramiko

BASE = "http://43.128.145.141"
SSH_HOST = "43.128.145.141"
SSH_USER = "ubuntu"
SSH_PWD = "Welcomeyyx0616"
SEED_TAG = "SEED-CONSIGNMENT-v441"

PLANS = [
    {
        "dealerId": 1,
        "warehouseId": 1,
        "lines": [
            {"productId": 1, "qty": 2, "serial": True,  "batch": "B2608-A1", "serials": ["SN-T2608-001", "SN-T2608-002"]},
            {"productId": 3, "qty": 10, "serial": False, "batch": "B2608-A2"},
        ],
    },
    {
        "dealerId": 1,
        "warehouseId": 13,
        "lines": [
            {"productId": 5, "qty": 1, "serial": True,  "batch": "B2608-B1", "serials": ["SN-T2608-003"]},
            {"productId": 7, "qty": 5, "serial": False, "batch": "B2608-B2"},
        ],
    },
    {
        "dealerId": 2,
        "warehouseId": 2,
        "lines": [
            {"productId": 9, "qty": 2, "serial": True,  "batch": "B2608-C1", "serials": ["SN-T2608-101", "SN-T2608-102"]},
            {"productId": 4, "qty": 20, "serial": False, "batch": "B2608-C2"},
        ],
    },
]


def ssh_exec(sql):
    cli = paramiko.SSHClient()
    cli.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    cli.connect(SSH_HOST, username=SSH_USER, password=SSH_PWD, timeout=20)
    escaped = sql.replace("'", "'\\''")
    cmd = f"docker exec dms-test-postgres psql -U dms -d dms_test -t -A -F '|' -c '{escaped}'"
    stdin, stdout, stderr = cli.exec_command(cmd)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    cli.close()
    return out, err


def call(method, path, token=None, body=None):
    url = BASE + path
    data = None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
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
    return status, parsed, raw


def data_of(parsed):
    if not parsed or not isinstance(parsed, dict):
        return None
    return parsed.get("data", parsed)


def step(msg):
    print("\n" + "=" * 66)
    print(msg)
    print("=" * 66)


CLEAN_SQL = f"""
DELETE FROM sales_out_batch_lines WHERE batch_id IN (
  SELECT b.id FROM sales_out_batches b JOIN sales_outs s ON s.id=b.sales_out_id
  WHERE s.source_order_id IN (SELECT id FROM orders WHERE remark LIKE '{SEED_TAG}%'));
DELETE FROM sales_out_batches WHERE sales_out_id IN (
  SELECT id FROM sales_outs WHERE source_order_id IN (SELECT id FROM orders WHERE remark LIKE '{SEED_TAG}%'));
DELETE FROM sales_out_lines WHERE sales_out_id IN (
  SELECT id FROM sales_outs WHERE source_order_id IN (SELECT id FROM orders WHERE remark LIKE '{SEED_TAG}%'));
DELETE FROM erp_outbound_callbacks WHERE source_order_id IN (
  SELECT id FROM orders WHERE remark LIKE '{SEED_TAG}%');
DELETE FROM sales_outs WHERE source_order_id IN (SELECT id FROM orders WHERE remark LIKE '{SEED_TAG}%');
DELETE FROM consignment_stock_movements WHERE batch_no LIKE 'B2608-%' OR serial_no LIKE 'SN-T2608-%';
DELETE FROM consignment_stock WHERE batch_no LIKE 'B2608-%' OR serial_no LIKE 'SN-T2608-%';
DELETE FROM order_lines WHERE order_id IN (SELECT id FROM orders WHERE remark LIKE '{SEED_TAG}%');
DELETE FROM approval_instances WHERE business_type='SALES_ORDER' AND business_id IN (SELECT id FROM orders WHERE remark LIKE '{SEED_TAG}%');
DELETE FROM orders WHERE remark LIKE '{SEED_TAG}%';
UPDATE dealers SET consignment_enabled=true, consignment_limit=500000 WHERE id IN (1,2);
UPDATE dealer_credit_profiles SET consignment_used=0 WHERE dealer_id=8 AND consignment_used>0
  AND NOT EXISTS (SELECT 1 FROM consignment_stock cs WHERE cs.dealer_id=8);
SELECT d.id, d.code, d.name, d.consignment_enabled, d.consignment_limit FROM dealers d WHERE d.id IN (1,2) ORDER BY d.id;
"""

VERIFY_SQL = """
SELECT cs.dealer_id, cs.product_id, cs.product_code, cs.batch_no, cs.serial_no, cs.warehouse_id,
       cs.on_hand_qty, cs.locked_qty, cs.std_unit_price, w.name
FROM consignment_stock cs LEFT JOIN warehouses w ON w.id=cs.warehouse_id
ORDER BY cs.dealer_id, cs.product_code, cs.batch_no, cs.serial_no;
"""


def main():
    step("[0] SSH 清理旧 seed 数据 + 开启经销商寄售开关")
    out, err = ssh_exec(CLEAN_SQL)
    print(out.strip())
    if err.strip():
        print("STDERR:", err.strip()[:500])

    step("[1] 登录")
    st, parsed, raw = call("POST", "/api/auth/login", body={
        "tenantCode": "default", "username": "admin", "password": "Sh123456"})
    token = None
    d = data_of(parsed)
    if isinstance(d, dict):
        token = d.get("accessToken") or d.get("access_token") or d.get("token")
    print("login:", st, (token[:20] + "...") if token else "FAIL " + raw[:200])
    if not token:
        sys.exit(1)

    created_orders = []
    for idx, plan in enumerate(PLANS, 1):
        dealer_id = plan["dealerId"]
        wh = plan["warehouseId"]
        step(f"[2.{idx}] 经销商 {dealer_id} 建补货单 (仓库 {wh})")
        body = {
            "orderType": "REPLENISHMENT",
            "dealerId": dealer_id,
            "remark": f"{SEED_TAG} dealer={dealer_id} wh={wh}",
            "lines": [{"productId": l["productId"], "qty": l["qty"]} for l in plan["lines"]],
        }
        st, parsed, raw = call("POST", "/api/sales-orders", token=token, body=body)
        d = data_of(parsed)
        if st != 200 or not d or "id" not in d:
            print("建单失败:", st, raw[:400])
            sys.exit(2)
        order_id = d["id"]
        print("补货单已建:", d, )

        st, parsed, raw = call("POST", f"/api/sales-orders/{order_id}/submit", token=token)
        d = data_of(parsed) or {}
        print("提交:", st, d if st == 200 else raw[:300])
        new_status = d.get("newStatus")
        if new_status != "APPROVED":
            st, parsed, raw = call("POST", f"/api/sales-orders/{order_id}/approve", token=token, body={"comment": "seed auto approve"})
            print("审批:", st, (data_of(parsed) if st == 200 else raw[:300]))

        st, parsed, raw = call("GET", f"/api/sales-orders/{order_id}", token=token)
        detail = data_of(parsed)
        line_map = {}
        for ln in (detail or {}).get("lines", []):
            line_map[ln.get("productId")] = ln.get("id")
        print("审批后订单行映射(submit会重建行):", line_map)

        step(f"[3.{idx}] ERP 出库回调（自定义批号/序列号/仓库 {wh}）")
        cb_lines = []
        for l in plan["lines"]:
            ol_id = line_map.get(l["productId"])
            if l["serial"]:
                for sn in l["serials"]:
                    cb_lines.append({"orderLineId": ol_id, "productId": l["productId"], "qty": 1,
                                     "batchNo": l["batch"], "serialNo": sn, "warehouseId": wh})
            else:
                cb_lines.append({"orderLineId": ol_id, "productId": l["productId"], "qty": l["qty"],
                                 "batchNo": l["batch"], "warehouseId": wh})
        payload = {
            "sourceOrderId": order_id,
            "dealerId": dealer_id,
            "direction": "FORWARD",
            "warehouseId": wh,
            "salesDate": "2026-08-28",
            "idempotencyKey": f"SEED-{uuid.uuid4()}",
            "lines": cb_lines,
        }
        st, parsed, raw = call("POST", "/api/v4/erp/outbound-callbacks", token=token, body=payload)
        print("出库回调:", st, (data_of(parsed) if st == 200 else raw[:400]))
        if st != 200:
            sys.exit(3)
        created_orders.append(order_id)

    step("[4] API 验证：/api/consignment/available")
    for dealer_id in (1, 2):
        st, parsed, raw = call("GET", f"/api/consignment/available?dealerId={dealer_id}", token=token)
        rows = data_of(parsed) or []
        print(f"\n经销商 {dealer_id} 可用寄售库存 {len(rows)} 行:")
        for r in rows:
            print(f"  stockId={r.get('stockId')} {r.get('productCode')} {r.get('productName')} "
                  f"批={r.get('batchNo')} 序列={r.get('serialNo')} 仓={r.get('warehouseName')} "
                  f"可用={r.get('availableQty')} 单价={r.get('stdUnitPrice')} 金额={r.get('availableAmount')}")

    step("[5] SQL 验证：台账 / 流水 / 资信")
    out, err = ssh_exec(VERIFY_SQL)
    print("consignment_stock:")
    print(out.strip())
    out2, _ = ssh_exec("SELECT change_type, COUNT(*), SUM(qty_change) FROM consignment_stock_movements GROUP BY change_type ORDER BY 1;")
    print("movements 汇总:\n" + out2.strip())
    out3, _ = ssh_exec("SELECT dealer_id, consignment_used, status FROM dealer_credit_profiles ORDER BY dealer_id;")
    print("dealer_credit_profiles:\n" + out3.strip())
    out4, _ = ssh_exec(f"SELECT id, code, order_type, status FROM orders WHERE remark LIKE '{SEED_TAG}%' ORDER BY id;")
    print("seed 补货单:\n" + out4.strip())

    step("造数完成")
    print("补货订单 IDs:", created_orders)


if __name__ == "__main__":
    main()
