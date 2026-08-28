import json
import sys
import urllib.request
import urllib.error

import paramiko

BASE = "http://43.128.145.141"
TAG = "E2E-INVOICE-v441"
DEALER = 1

fails = []


def call(method, path, token=None, body=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw = resp.read().decode("utf-8", "replace")
            st = resp.getcode()
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        st = e.code
    except Exception as e:
        return -1, None, str(e)
    try:
        parsed = json.loads(raw)
    except Exception:
        parsed = None
    return st, parsed, raw


def data_of(parsed):
    if not parsed or not isinstance(parsed, dict):
        return None
    return parsed.get("data", parsed)


def check(name, cond, extra=""):
    print(("PASS" if cond else "FAIL"), "-", name, extra)
    if not cond:
        fails.append(name)


def ssh(sql):
    cli = paramiko.SSHClient()
    cli.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    cli.connect("43.128.145.141", username="ubuntu", password="Welcomeyyx0616", timeout=20)
    cmd = "docker exec dms-test-postgres psql -U dms -d dms_test -t -A -F '|' -c \"%s\"" % sql.replace('"', '\\"')
    _, stdout, stderr = cli.exec_command(cmd)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    cli.close()
    return out, err


def stock_row(stock_id):
    out, _ = ssh(f"SELECT on_hand_qty, locked_qty FROM consignment_stock WHERE id={stock_id};")
    parts = out.strip().split("|")
    if len(parts) == 2 and parts[0].strip():
        return int(parts[0]), int(parts[1])
    return None


def movements(order_id):
    out, _ = ssh(f"SELECT change_type, qty_change FROM consignment_stock_movements WHERE ref_type='INVOICE_ORDER' AND ref_id={order_id} ORDER BY id;")
    return [l.strip() for l in out.strip().splitlines() if l.strip()]


def create_invoice(token, lines):
    body = {"orderType": "INVOICE", "dealerId": DEALER, "remark": TAG, "lines": lines}
    st, parsed, raw = call("POST", "/api/sales-orders", token=token, body=body)
    return st, data_of(parsed), raw


def order_lines(token, oid):
    st, parsed, raw = call("GET", f"/api/sales-orders/{oid}", token=token)
    d = data_of(parsed) or {}
    return {ln.get("productId"): ln for ln in d.get("lines", [])}


def first_task_id(token, oid):
    st, parsed, raw = call("GET", f"/api/approval/instances/by-business?businessType=INVOICE_ORDER&businessId={oid}", token=token)
    inst = data_of(parsed)
    if not inst:
        return None
    iid = inst.get("id")
    st, parsed, raw = call("GET", f"/api/approval/instances/{iid}", token=token)
    d = data_of(parsed) or {}
    tasks = d.get("tasks") or []
    for t in tasks:
        if t.get("status") == "PENDING":
            return t.get("id")
    return tasks[0].get("id") if tasks else None


print("=" * 66)
print("开票寄售库存拣选 全闭环 E2E (v4.4.1)")
print("=" * 66)

st, parsed, raw = call("POST", "/api/auth/login", body={"tenantCode": "default", "username": "admin", "password": "Sh123456"})
token = (data_of(parsed) or {}).get("accessToken")
check("登录", bool(token), raw[:120] if not token else "")
if not token:
    sys.exit(1)

st, parsed, raw = call("GET", f"/api/consignment/available?dealerId={DEALER}", token=token)
avail = data_of(parsed) or []
by_key = {}
for r in avail:
    by_key[(r["productId"], r.get("batchNo"), r.get("serialNo"))] = r
print(f"\n经销商{DEALER}可用库存 {len(avail)} 行:")
for r in avail:
    print(f"  stockId={r.get('stockId')} pid={r.get('productId')} 批={r.get('batchNo')} 序列={r.get('serialNo')} 可用={r.get('availableQty')}")

st_serial = by_key[(1, "B2608-A1", "SN-T2608-001")]
st_batch = by_key[(3, "B2608-A2", None)]
st_other_dealer = None
st2, p2, _ = call("GET", "/api/consignment/available?dealerId=2", token=token)
for r in (data_of(p2) or []):
    st_other_dealer = r
    break

print("\n" + "-" * 66)
print("场景1：开票拣选提交→预占 LOCK；驳回→释放 RELEASE")
print("-" * 66)
sid1 = st_serial["stockId"]
before1 = stock_row(sid1)
st, d, raw = create_invoice(token, [{
    "productId": 1, "qty": 1, "batchNo": st_serial["batchNo"],
    "serialNo": st_serial["serialNo"], "consignmentStockId": sid1}])
check("1.1 建开票单成功", st == 200 and d and "id" in d, raw[:200])
oid1 = d["id"] if d else None
st, d, raw = call("POST", f"/api/sales-orders/{oid1}/submit", token=token)
check("1.2 提交成功(触发预占)", st == 200, raw[:200])
after_lock1 = stock_row(sid1)
check("1.3 台账 locked+1 / on_hand 不变", before1 and after_lock1 and after_lock1[1] == before1[1] + 1 and after_lock1[0] == before1[0],
      f"before={before1} after={after_lock1}")
mvs = movements(oid1)
check("1.4 INVOICE_LOCK 流水", any("INVOICE_LOCK" in m for m in mvs), str(mvs))
task_id = first_task_id(token, oid1)
st, d, raw = call("POST", f"/api/approval/tasks/{task_id}/reject", token=token, body={"comment": "e2e reject"})
check("1.5 驳回成功", st == 200, raw[:200])
after_rel1 = stock_row(sid1)
check("1.6 驳回后 locked 释放回原值", after_rel1 == before1, f"before={before1} after={after_rel1}")
mvs = movements(oid1)
check("1.7 INVOICE_RELEASE 流水", any("INVOICE_RELEASE" in m for m in mvs), str(mvs))

print("\n" + "-" * 66)
print("场景2：开票数量超过可用量 → 提交被拒绝（业务异常，非500）")
print("-" * 66)
st, d, raw = create_invoice(token, [{
    "productId": 3, "qty": 999, "batchNo": st_batch["batchNo"], "consignmentStockId": st_batch["stockId"]}])
oid2 = d.get("id") if d else None
if st != 200 or not oid2:
    check("2.1 超额开票被业务拦截(建单或提交阶段)", "寄售库存不足" in raw or "可用" in raw, f"建单即拦截 status={st} {raw[:200]}")
else:
    st2s, d2, raw2 = call("POST", f"/api/sales-orders/{oid2}/submit", token=token)
    check("2.1 超额开票提交被业务拦截(400/业务码)", st2s in (400, 409) and ("寄售库存不足" in raw2 or "可用" in raw2),
          f"status={st2s} {raw2[:180]}")

print("\n" + "-" * 66)
print("场景3：stockId 防错——用经销商2的台账行给经销商1开票 → 拒绝")
print("-" * 66)
if st_other_dealer:
    st, d, raw = create_invoice(token, [{
        "productId": st_other_dealer["productId"], "qty": 1,
        "batchNo": st_other_dealer["batchNo"], "serialNo": st_other_dealer.get("serialNo"),
        "consignmentStockId": st_other_dealer["stockId"]}])
    check("3.1 建单(库存校验按维度可过/或拦截)", st in (200, 400), raw[:150])
    if st == 200 and d:
        oid3 = d["id"]
        st3, d3, raw3 = call("POST", f"/api/sales-orders/{oid3}/submit", token=token)
        check("3.2 提交时 stockId 归属校验拦截(不属于当前经销商)", st3 in (400, 409) and "不属于当前经销商" in raw3,
              f"status={st3} {raw3[:180]}")
else:
    check("3.x 经销商2台账行存在", False, "未取到")

print("\n" + "-" * 66)
print("场景4：正常开票全链路——stockId 精准锁定→审批通过→实扣 DEDUCT")
print("-" * 66)
sid4 = st_batch["stockId"]
before4 = stock_row(sid4)
st, d, raw = create_invoice(token, [{
    "productId": 3, "qty": 3, "batchNo": st_batch["batchNo"], "consignmentStockId": sid4}])
check("4.1 建开票单成功", st == 200 and d and "id" in d, raw[:200])
oid4 = d["id"] if d else None
lines4 = order_lines(token, oid4)
ln4 = lines4.get(3)
check("4.2 订单行回写 consignmentStockId", ln4 and ln4.get("consignmentStockId") == sid4,
      f"line={ln4.get('consignmentStockId') if ln4 else None} expect={sid4}")
st, d, raw = call("POST", f"/api/sales-orders/{oid4}/submit", token=token)
check("4.3 提交预占成功", st == 200, raw[:200])
mid4 = stock_row(sid4)
check("4.4 locked+3", mid4 and mid4[1] == before4[1] + 3 and mid4[0] == before4[0], f"before={before4} mid={mid4}")
task_id = first_task_id(token, oid4)
st, d, raw = call("POST", f"/api/approval/tasks/{task_id}/approve", token=token, body={"comment": "e2e approve"})
check("4.5 审批通过", st == 200, raw[:200])
final4 = stock_row(sid4)
check("4.6 实扣 on_hand-3 / locked-3", final4 and final4[0] == before4[0] - 3 and final4[1] == before4[1],
      f"before={before4} final={final4}")
mvs = movements(oid4)
check("4.7 INVOICE_LOCK + INVOICE_DEDUCT 流水",
      any("INVOICE_LOCK" in m for m in mvs) and any("INVOICE_DEDUCT" in m for m in mvs), str(mvs))

print("\n" + "-" * 66)
print("场景5：补货红冲完整链路——红字补货单(SOR)→提交→审批→RED回调→REPLENISH_OUT")
print("-" * 66)
import uuid as _uuid
st2o, p2o, _ = call("GET", "/api/consignment/available?dealerId=2", token=token)
avail2 = data_of(p2o) or []
sn_row = next((r for r in avail2 if r.get("serialNo") and (r.get("availableQty") or 0) >= 1), None)
if sn_row:
    sid5 = sn_row["stockId"]
    pid5 = sn_row["productId"]
    before5 = stock_row(sid5)
    red_body = {
        "orderType": "REPLENISHMENT", "isRed": True, "dealerId": 2,
        "remark": TAG + " red replenish",
        "lines": [{"productId": pid5, "qty": 1,
                   "batchNo": sn_row.get("batchNo"), "serialNo": sn_row.get("serialNo")}],
    }
    st, d5, raw = call("POST", "/api/sales-orders", token=token, body=red_body)
    d5 = data_of(d5)
    check("5.1 红字补货单创建成功(SOR)", st == 200 and d5 and "id" in d5 and str(d5.get("code", "")).startswith("SOR"),
          raw[:200])
    oid5 = d5["id"] if d5 else None
    if oid5:
        st, _, raw = call("POST", f"/api/sales-orders/{oid5}/submit", token=token)
        check("5.2 红字补货单提交成功", st == 200, raw[:200])
        st, _, raw = call("POST", f"/api/sales-orders/{oid5}/approve", token=token, body={"comment": "e2e red approve"})
        check("5.3 红字补货单审批通过(推送ERP)", st == 200, raw[:200])
        lines5 = order_lines(token, oid5)
        ln5 = lines5.get(pid5)
        check("5.4 重建后订单行可取(行ID/批号/序列号)", ln5 and ln5.get("id") and ln5.get("serialNo") == sn_row.get("serialNo"),
              f"line={ln5}")
        if ln5:
            cb_body = {
                "sourceOrderId": oid5, "dealerId": 2, "direction": "RED", "warehouseId": 2,
                "salesDate": "2026-08-28", "idempotencyKey": "E2E-RED-" + str(_uuid.uuid4()),
                "lines": [{"orderLineId": ln5["id"], "productId": pid5, "qty": 1,
                           "batchNo": sn_row.get("batchNo"), "serialNo": sn_row.get("serialNo"), "warehouseId": 2}],
            }
            st, dcb, raw = call("POST", "/api/v4/erp/outbound-callbacks", token=token, body=cb_body)
            dcb = data_of(dcb)
            check("5.5 红字出库回调成功(GIR)", st == 200 and dcb and str(dcb.get("code", "")).startswith("GIR"), raw[:200])
            after5 = stock_row(sid5)
            check("5.6 台账 on_hand-1（REPLENISH_OUT 实扣）",
                  before5 and after5 and after5[0] == before5[0] - 1 and after5[1] == before5[1],
                  f"before={before5} after={after5}")
            out_mv, _ = ssh(f"SELECT change_type, qty_change, ref_type, ref_id FROM consignment_stock_movements "
                            f"WHERE change_type='REPLENISH_OUT' AND ref_id={dcb.get('id') if dcb else -1} ORDER BY id;")
            check("5.7 REPLENISH_OUT 流水(ref=SALES_OUT/红字出库单)",
                  "REPLENISH_OUT" in out_mv and "SALES_OUT" in out_mv, out_mv.strip())
            out_st, _ = ssh(f"SELECT status, COALESCE(is_red,false) FROM orders WHERE id={oid5};")
            check("5.8 红字订单状态 COMPLETED 且 is_red=true", "COMPLETED" in out_st and "t" in out_st.split("|")[1], out_st.strip())
            out_gir, _ = ssh(f"SELECT code, is_red FROM sales_outs WHERE source_order_id={oid5} AND is_red=true ORDER BY id DESC LIMIT 1;")
            check("5.9 红字出库单 GIR 生成", "GIR" in out_gir and "t" in out_gir.split("|")[1], out_gir.strip())
else:
    check("5.x 经销商2序列号寄售库存行存在", False, "无可用序列号行")

print("\n" + "=" * 66)
print(f"E2E 结果：{len(fails)} 个失败")
for f in fails:
    print("  FAIL:", f)
print("=" * 66)
sys.exit(1 if fails else 0)
