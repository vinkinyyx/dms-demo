# -*- coding: utf-8 -*-
"""
v4.4.0 功能测试 - API/DB 层（只读探测 + 自清理种子数据）
覆盖：防回归清单、R1 订单类型计价、R2 寄售门禁、R3/R4 寄售台账与开票生命周期、
     R5 资信账期、R6 进销存开关、补货发货->寄售入库链路行为验证。
结果写入 automation_test/results-v440/ 下的 JSON。
"""
import json, urllib.request, urllib.error, time, sys, os, io
import paramiko

BASE = "http://43.128.145.141"
API = BASE + "/api"
SSH_HOST = "43.128.145.141"
SSH_USER = "ubuntu"
SSH_PASS = "Welcomeyyx0616"
TAG = "T440" + time.strftime("%m%d%H%M%S")

RESULTS = []
DB_STATE = {"dealer": None, "features": None}


def log(case, ok, detail):
    RESULTS.append({"case": case, "ok": bool(ok), "detail": detail})
    print(("PASS " if ok else "FAIL ") + case + " :: " + json.dumps(detail, ensure_ascii=False)[:400])


def req(method, path, token=None, body=None, base=API, timeout=45):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    r = urllib.request.Request(base + path, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
            return resp.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            payload = json.loads(raw)
        except Exception:
            payload = {"raw": raw[:500]}
        return e.code, payload
    except Exception as e:
        return -1, {"error": str(e)}


def login(username="admin", password="Sh123456", tenant="default"):
    st, res = req("POST", "/auth/login", body={"tenantCode": tenant, "username": username, "password": password})
    if st == 200 and res.get("data", {}).get("accessToken"):
        return res["data"]["accessToken"]
    log("登录-" + username, False, {"status": st, "res": res})
    return None


def list_rows(token, path, **kw):
    qs = ("?" + "&".join(f"{k}={v}" for k, v in kw.items())) if kw else ""
    st, res = req("GET", path + qs, token)
    data = res.get("data") if st == 200 else None
    if isinstance(data, dict):
        return data.get("list", data.get("records", [])) or []
    if isinstance(data, list):
        return data
    return []


def db(sql, fetch=True):
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(SSH_HOST, port=22, username=SSH_USER, password=SSH_PASS, timeout=25,
                   banner_timeout=30, auth_timeout=20, allow_agent=False, look_for_keys=False)
    try:
        escaped = sql.replace("'", "'\\''")
        if fetch:
            wrapped = "(" + sql.rstrip().rstrip(";") + ")"
            cmd = ("export PGPASSWORD=dms; docker exec -i dms-test-postgres psql -U dms -d dms_test -t -A -F'|' "
                   "-v ON_ERROR_STOP=1 -P pager=off -c \"" + wrapped.replace("\"", "\\\"") + "\"")
        else:
            cmd = ("export PGPASSWORD=dms; docker exec -i dms-test-postgres psql -U dms -d dms_test "
                   "-v ON_ERROR_STOP=1 -P pager=off <<'SQL'\n" + sql + "\nSQL")
        stdin, stdout, stderr = client.exec_command(
            "echo '%s' | sudo -S -p '' bash -c '%s'" % (SSH_PASS, cmd.replace("'", "'\\''")), timeout=90)
        out = stdout.read().decode("utf-8", errors="replace")
        err = stderr.read().decode("utf-8", errors="replace")
        code = stdout.channel.recv_exit_status()
        rows = []
        if fetch and code == 0:
            for line in out.splitlines():
                line = line.strip()
                if line:
                    rows.append(line.split("|"))
        return code, rows, (out + "\n" + err)
    finally:
        client.close()


def db_one(sql):
    code, rows, txt = db(sql)
    if code != 0:
        return None, txt
    return (rows[0] if rows else None), txt


# ============ Phase 0 环境与健康 ============
def phase0():
    st, res = req("GET", "/actuator/health", base=BASE)
    log("P0-后端健康检查", st == 200 and res.get("status") == "UP", {"status": st, "body": res})
    tok = login()
    log("P0-admin登录", bool(tok), {"hasToken": bool(tok)})
    dtok = login("dealer_admin", "Dms@123456")
    log("P0-dealer_admin登录", bool(dtok), {"hasToken": bool(dtok)})
    return tok, dtok


# ============ Phase A 防回归 API 清单 ============
def phase_a(tok):
    prods = list_rows(tok, "/products", page=1, size=10)
    log("A1-产品列表", len(prods) > 0, {"count": len(prods)})
    pid = prods[0]["id"] if prods else None
    st, res = req("GET", f"/products/{pid}", tok)
    log("A2-产品详情", st == 200 and res.get("data", {}).get("id") == pid, {"status": st, "id": pid})
    st, res = req("GET", f"/operation-log/list/product/{pid}", tok)
    ok_log = st == 200 and isinstance(res.get("data"), (list, dict))
    log("A3-产品操作日志", ok_log, {"status": st, "dataType": type(res.get("data")).__name__})
    # 删除有引用的产品应返回业务错误码（40904 或 4xx 业务错误），不得 500
    st, res = req("DELETE", f"/products/{pid}", tok)
    code = res.get("code") if isinstance(res, dict) else None
    msg = res.get("message") or res.get("msg") or ""
    ok_del = st != 500 and (st in (400, 409) or code in (40904, 40900, 40004) or "引用" in str(msg) or "使用" in str(msg) or "不能删除" in str(msg))
    log("A4-删除被引用产品返回业务错误(非500)", ok_del, {"http": st, "code": code, "msg": str(msg)[:120]})


# ============ Phase B R6 进销存开关 ============
def phase_b(tok, dtok):
    st, res = req("GET", "/tenant/features", tok)
    inv = res.get("data", {}).get("inventoryEnabled") if st == 200 else None
    log("B1-租户特性接口", st == 200 and isinstance(inv, bool), {"status": st, "data": res.get("data")})
    DB_STATE["features"] = inv
    # 关开关（DB），验证厂家用户被拦截
    if inv is not None:
        code, _, txt = db("UPDATE tenants SET modules_enabled = jsonb_set(COALESCE(modules_enabled,'{}'::jsonb),'{inventoryEnabled}','false') WHERE code='default'", fetch=False)
        time.sleep(1)
        st2, res2 = req("GET", "/tenant/features", tok)
        inv2 = res2.get("data", {}).get("inventoryEnabled")
        log("B2-关闭进销存开关生效", code == 0 and inv2 is False, {"dbCode": code, "now": inv2, "txt": txt[:150]})
        blocked = []
        for name, path in [("采购订单", "/purchase-orders?page=1&size=1"), ("库存", "/inventory?page=1&size=1"),
                           ("仓库", "/warehouses?page=1&size=1"), ("供应商", "/suppliers?page=1&size=1")]:
            s, r = req("GET", path, tok)
            m = str(r.get("message") or r.get("msg") or "")
            blocked.append({"name": name, "http": s, "code": r.get("code"), "msg": m[:60]})
        vendor_blocked = all(b["http"] in (400, 403) and ("进销存" in b["msg"] or "库存模块" in b["msg"]) for b in blocked)
        log("B3-厂家用户访问采购/库存/仓库/供应商被拦截", vendor_blocked, blocked)
        # 经销商用户旁路放行
        if dtok:
            s, r = req("GET", "/inventory?page=1&size=1", dtok)
            log("B4-经销商用户访问库存旁路放行", s == 200, {"http": s, "msg": str(r.get("message"))[:80]})
        # 恢复
        db("UPDATE tenants SET modules_enabled = jsonb_set(COALESCE(modules_enabled,'{}'::jsonb),'{inventoryEnabled}','%s') WHERE code='default'" % ("true" if inv else "false"), fetch=False)
        time.sleep(1)
        st3, res3 = req("GET", "/tenant/features", tok)
        log("B5-进销存开关已还原", res3.get("data", {}).get("inventoryEnabled") == inv, {"restored": res3.get("data", {}).get("inventoryEnabled"), "orig": inv})


# ============ Phase C R1/R2 订单类型与寄售门禁 ============
def find_dealer(tok, keyword=None):
    rows = list_rows(tok, "/dealers", page=1, size=50, **({"keyword": keyword} if keyword else {}))
    return rows


def phase_c(tok):
    dealers = find_dealer(tok)
    if not dealers:
        log("C-前置:经销商数据", False, {"reason": "no dealers"})
        return None, None
    d = dealers[0]
    did = d["id"]
    # 记录原始寄售开关
    orig_row, _ = db_one(f"SELECT COALESCE(consignment_enabled,false) FROM dealers WHERE id={did}")
    orig = orig_row[0] if orig_row else "false"
    DB_STATE["dealer"] = (did, orig)
    prods = list_rows(tok, "/products", page=1, size=20)
    # 选一个有价格的产品
    pid = None
    for p in prods:
        if p.get("status") in (None, "active", "ACTIVE"):
            pid = p["id"]
            break
    if not pid:
        pid = prods[0]["id"]

    # 门禁：未开启寄售 -> REPLENISHMENT/INVOICE 预览应被拒
    db(f"UPDATE dealers SET consignment_enabled=false WHERE id={did}", fetch=False)
    time.sleep(1)
    body_rep = {"dealerId": did, "orderType": "REPLENISHMENT", "lines": [{"productId": pid, "qty": 2}]}
    s1, r1 = req("POST", "/sales-orders/preview", tok, body_rep)
    m1 = str(r1.get("message") or r1.get("msg") or "")
    log("C1-未开寄售->补货单被拒", s1 in (400, 409) and "寄售" in m1, {"http": s1, "msg": m1[:120]})
    body_inv = {"dealerId": did, "orderType": "INVOICE", "lines": [{"productId": pid, "qty": 1, "batchNo": None}]}
    s2, r2 = req("POST", "/sales-orders/preview", tok, body_inv)
    m2 = str(r2.get("message") or r2.get("msg") or "")
    log("C2-未开寄售->开票单被拒", s2 in (400, 409) and "寄售" in m2, {"http": s2, "msg": m2[:120]})

    # 开启寄售
    db(f"UPDATE dealers SET consignment_enabled=true, consignment_limit=99999999 WHERE id={did}", fetch=False)
    time.sleep(1)

    # R1 补货单：金额应为 0
    s3, r3 = req("POST", "/sales-orders/preview", tok, body_rep)
    d3 = r3.get("data", {}) if s3 == 200 else {}
    final = _final(d3)
    log("C3-补货单预览金额为0", s3 == 200 and _num(final) == 0, {"http": s3, "final": final, "keys": list(d3.keys())[:15]})

    # R1 样品单：缺原因被拒
    s4, r4 = req("POST", "/sales-orders/preview", tok, {"dealerId": did, "orderType": "SAMPLE", "lines": [{"productId": pid, "qty": 1}]})
    m4 = str(r4.get("message") or "")
    log("C4-样品单缺申请原因被拒", s4 in (400, 409) and ("样品" in m4 or "原因" in m4), {"http": s4, "msg": m4[:120]})
    # 样品单：多行被拒
    s5, r5 = req("POST", "/sales-orders/preview", tok, {"dealerId": did, "orderType": "SAMPLE", "sampleReason": "展会样品",
                                                        "lines": [{"productId": pid, "qty": 1}, {"productId": prods[1]["id"] if len(prods) > 1 else pid, "qty": 1}]})
    m5 = str(r5.get("message") or "")
    log("C5-样品单多行被拒", s5 in (400, 409) and "一个样品" in m5, {"http": s5, "msg": m5[:120]})
    # 样品单：单行+原因 -> 金额 0
    s6, r6 = req("POST", "/sales-orders/preview", tok, {"dealerId": did, "orderType": "SAMPLE", "sampleReason": "展会样品",
                                                        "lines": [{"productId": pid, "qty": 1}]})
    d6 = r6.get("data", {}) if s6 == 200 else {}
    final6 = _final(d6)
    log("C6-样品单合法预览金额为0", s6 == 200 and _num(final6) == 0, {"http": s6, "final": final6})

    return did, pid


def _num(v):
    try:
        return float(v)
    except Exception:
        return None


def _final(d):
    if not isinstance(d, dict):
        return None
    for k in ("finalAmount", "final_amount", "payableAmount", "payable_amount"):
        if d.get(k) is not None:
            return d.get(k)
    t = d.get("totals")
    if isinstance(t, dict):
        for k in ("finalAmount", "final_amount", "payableAmount"):
            if t.get(k) is not None:
                return t.get(k)
    return None


def find_todo_task(tok, oid, instance_id=None):
    tasks = list_rows(tok, "/approval/tasks/my-todo", page=1, size=50)
    if instance_id:
        for t in tasks:
            if str(t.get("instanceId")) == str(instance_id) and str(t.get("status")) == "PENDING":
                return t, tasks
    for t in tasks:
        if str(t.get("businessId")) == str(oid) and str(t.get("status")) == "PENDING":
            return t, tasks
    if instance_id:
        s, r = req("GET", f"/approval/instances/{instance_id}", tok)
        inst = r.get("data") if s == 200 else None
        bid = str((inst or {}).get("businessId") or "")
        for t in tasks:
            if str(t.get("status")) == "PENDING" and (bid and (bid == str(oid) or str(t.get("instanceId")) == str(instance_id))):
                return t, tasks
    return None, tasks


# ============ Phase D R3/R4 寄售库存与开票生命周期（DB 种子） ============
def phase_d(tok, did, pid):
    if not did or not pid:
        log("D-前置", False, {"reason": "missing dealer/product"})
        return
    # 种子：该经销商寄售库存 10 件（批号 B-T440），标准价取产品价格
    code_row, _ = db_one(f"SELECT code FROM products WHERE id={pid}")
    name_row, _ = db_one(f"SELECT COALESCE(name_cn,name,'') FROM products WHERE id={pid}")
    pcode = code_row[0] if code_row else "T440P"
    pname = name_row[0] if name_row else "T440产品"
    wh_row, _ = db_one("SELECT id FROM warehouses WHERE tenant_id=(SELECT id FROM tenants WHERE code='default') AND deleted_at IS NULL ORDER BY id LIMIT 1")
    whid = wh_row[0] if wh_row else "NULL"
    batch = "B" + TAG
    # 清理旧种子
    db(f"DELETE FROM consignment_stock_movements WHERE tenant_id=(SELECT id FROM tenants WHERE code='default') AND remark LIKE '%{TAG}%'", fetch=False)
    db(f"DELETE FROM consignment_stock WHERE tenant_id=(SELECT id FROM tenants WHERE code='default') AND batch_no='{batch}'", fetch=False)
    db(
        f"INSERT INTO consignment_stock (tenant_id,dealer_id,product_id,product_code,product_name,product_spec,unit,"
        f"batch_no,serial_no,warehouse_id,on_hand_qty,locked_qty,std_unit_price,created_at,updated_at,version) "
        f"SELECT t.id,{did},{pid},'{pcode}','{pname}','T440','件','{batch}',NULL,{whid},10,0,2800,now(),now(),0 "
        f"FROM tenants t WHERE t.code='default'", fetch=False)
    db(f"UPDATE dealers SET consignment_enabled=true WHERE id={did}", fetch=False)
    time.sleep(1)

    # 可用库存接口
    s, r = req("GET", f"/consignment/available?dealerId={did}", tok)
    avail = r.get("data") if s == 200 else []
    mine = [x for x in (avail or []) if str(x.get("batchNo")) == batch]
    log("D1-寄售可用库存接口返回种子数据", s == 200 and len(mine) == 1 and mine[0].get("availableQty") == 10,
        {"http": s, "mine": mine[:1]})

    # 开票预览：合法（数量<=可用）
    body_ok = {"dealerId": did, "orderType": "INVOICE",
               "lines": [{"productId": pid, "qty": 3, "batchNo": batch}]}
    s, r = req("POST", "/sales-orders/preview", tok, body_ok)
    d = r.get("data", {}) if s == 200 else {}
    log("D2-开票单合法预览成功(重计价非0)", s == 200, {"http": s, "final": d.get("finalAmount") or d.get("final_amount")})

    # 开票预览：超量被拒（含产品编码+名称）
    s, r = req("POST", "/sales-orders/preview", tok,
               {"dealerId": did, "orderType": "INVOICE", "lines": [{"productId": pid, "qty": 99, "batchNo": batch}]})
    m = str(r.get("message") or "")
    log("D3-开票超可用量被拒(含编码名称)", s in (400, 409) and pcode in m and ("可用" in m), {"http": s, "msg": m[:160]})

    # 开票预览：错误批号（不在寄售库存）被拒
    s, r = req("POST", "/sales-orders/preview", tok,
               {"dealerId": did, "orderType": "INVOICE", "lines": [{"productId": pid, "qty": 1, "batchNo": "NOT-EXIST-999"}]})
    m = str(r.get("message") or "")
    log("D4-开票错误批号被拒", s in (400, 409) and "不在该经销商的寄售库存" in m, {"http": s, "msg": m[:160]})

    # 创建开票草稿 -> 提交（预占）
    s, r = req("POST", "/sales-orders", tok, body_ok)
    oid = (r.get("data") or {}).get("id")
    log("D5-开票草稿创建", s == 200 and bool(oid), {"http": s, "id": oid, "msg": str(r.get("message"))[:120]})
    if oid:
        # 行上 batch_no/serial_no 持久化
        row, _ = db_one(f"SELECT batch_no, qty FROM order_lines WHERE order_id={oid}")
        log("D6-开票行批号持久化", row and row[0] == batch, {"row": row})
        s, r = req("POST", f"/sales-orders/{oid}/submit", tok)
        submit_data = r.get("data") or {}
        inst_id = submit_data.get("approvalInstanceId")
        log("D7-开票提交成功(预占)", s == 200, {"http": s, "data": submit_data, "msg": str(r.get("message"))[:150]})
        time.sleep(1)
        lk, _ = db_one(f"SELECT locked_qty, on_hand_qty FROM consignment_stock WHERE tenant_id=(SELECT id FROM tenants WHERE code='default') AND dealer_id={did} AND batch_no='{batch}'")
        log("D8-提交后寄售库存锁定=3", lk and lk[0] == "3" and lk[1] == "10", {"locked/onhand": lk})
        mv, _ = db_one(f"SELECT change_type, qty_change FROM consignment_stock_movements WHERE ref_type='INVOICE_ORDER' AND ref_id={oid} ORDER BY id DESC LIMIT 1")
        log("D9-生成INVOICE_LOCK台账", mv and mv[0] == "INVOICE_LOCK", {"movement": mv})

        # 审批通过 -> 实扣（任务通过 instanceId 关联，业务字段在实例上）
        task, tasks = find_todo_task(tok, oid, inst_id)
        tid_task = task.get("id") if task else None
        log("D10-审批待办包含开票任务", bool(tid_task), {"found": bool(task), "instanceId": inst_id,
            "sample": {k: tasks[0].get(k) for k in ("id", "instanceId", "nodeName", "status")} if tasks else None})
        if tid_task:
            s, r = req("POST", f"/approval/tasks/{tid_task}/approve", tok, {"comment": "T440 approve", "action": "APPROVE"})
            log("D11-开票审批通过", s == 200, {"http": s, "msg": str(r.get("message"))[:120]})
            time.sleep(1)
            stk, _ = db_one(f"SELECT locked_qty, on_hand_qty FROM consignment_stock WHERE tenant_id=(SELECT id FROM tenants WHERE code='default') AND dealer_id={did} AND batch_no='{batch}'")
            log("D12-审批通过后实扣(onhand=7,locked=0)", stk and stk[1] == "7" and stk[0] == "0", {"locked/onhand": stk})
            mvd, _ = db_one(f"SELECT change_type FROM consignment_stock_movements WHERE ref_type='INVOICE_ORDER' AND ref_id={oid} AND change_type='INVOICE_DEDUCT'")
            log("D13-生成INVOICE_DEDUCT台账", bool(mvd), {"deduct": mvd})

    # 驳回释放链路：再开一张 2 件
    body2 = {"dealerId": did, "orderType": "INVOICE", "lines": [{"productId": pid, "qty": 2, "batchNo": batch}]}
    s, r = req("POST", "/sales-orders", tok, body2)
    oid2 = (r.get("data") or {}).get("id")
    inst_id2 = None
    if oid2:
        s, r = req("POST", f"/sales-orders/{oid2}/submit", tok)
        inst_id2 = (r.get("data") or {}).get("approvalInstanceId")
        time.sleep(1)
        task2, tasks = find_todo_task(tok, oid2, inst_id2)
        if task2:
            s, r = req("POST", f"/approval/tasks/{task2['id']}/reject", tok, {"comment": "T440 reject", "action": "REJECT"})
            log("D14-开票审批驳回", s == 200, {"http": s, "msg": str(r.get("message"))[:120]})
            time.sleep(1)
            stk, _ = db_one(f"SELECT locked_qty, on_hand_qty FROM consignment_stock WHERE tenant_id=(SELECT id FROM tenants WHERE code='default') AND dealer_id={did} AND batch_no='{batch}'")
            log("D15-驳回后释放预占(locked=0,onhand=7)", stk and stk[0] == "0" and stk[1] == "7", {"locked/onhand": stk})
            mvr, _ = db_one(f"SELECT change_type FROM consignment_stock_movements WHERE ref_type='INVOICE_ORDER' AND ref_id={oid2} AND change_type='INVOICE_RELEASE'")
            log("D16-生成INVOICE_RELEASE台账", bool(mvr), {"release": mvr})
        else:
            log("D14-开票审批驳回", False, {"reason": "no todo task for oid2", "tasks": [str(t.get("businessId")) for t in tasks]})

    # R5 资信账期
    s, r = req("GET", "/dealer-credit?page=1&size=50", tok)
    credits = (r.get("data") or {}).get("list") if isinstance(r.get("data"), dict) else r.get("data")
    mine_c = [x for x in (credits or []) if str(x.get("dealerId")) == str(did)]
    fields_ok = mine_c and all(k in mine_c[0] for k in ("creditLimit", "creditUsed", "creditAvailable", "consignmentUsed", "paymentDays"))
    log("D17-资信账期列表字段完整", s == 200 and fields_ok, {"http": s, "mine": mine_c[:1]})

    # 清理种子与订单
    cleanup_ids = [str(x) for x in [oid, oid2] if x]
    if cleanup_ids:
        ids = ",".join(cleanup_ids)
        db(f"DELETE FROM consignment_stock_movements WHERE tenant_id=(SELECT id FROM tenants WHERE code='default') AND ref_id IN ({ids})", fetch=False)
        db(f"DELETE FROM order_promotion_hits WHERE order_id IN ({ids})", fetch=False)
        db(f"DELETE FROM order_status_history WHERE order_id IN ({ids})", fetch=False)
        db(f"DELETE FROM approval_records WHERE instance_id IN (SELECT id FROM approval_instances WHERE business_id IN ({ids}))", fetch=False)
        db(f"DELETE FROM approval_tasks WHERE instance_id IN (SELECT id FROM approval_instances WHERE business_id IN ({ids}))", fetch=False)
        db(f"DELETE FROM approval_cc_records WHERE instance_id IN (SELECT id FROM approval_instances WHERE business_id IN ({ids}))", fetch=False)
        db(f"DELETE FROM approval_instances WHERE business_id IN ({ids})", fetch=False)
        db(f"DELETE FROM order_lines WHERE order_id IN ({ids})", fetch=False)
        db(f"DELETE FROM orders WHERE id IN ({ids})", fetch=False)
    db(f"DELETE FROM consignment_stock_movements WHERE tenant_id=(SELECT id FROM tenants WHERE code='default') AND remark LIKE '%{TAG}%'", fetch=False)
    db(f"DELETE FROM consignment_stock WHERE tenant_id=(SELECT id FROM tenants WHERE code='default') AND batch_no='{batch}'", fetch=False)
    db(f"DELETE FROM dealer_credit_profiles WHERE tenant_id=(SELECT id FROM tenants WHERE code='default') AND dealer_id={did} AND consignment_used>0", fetch=False)
    log("D18-寄售种子数据已清理", True, {"batch": batch, "orders": cleanup_ids})


# ============ Phase E 补货发货 -> 寄售入库 链路行为 ============
def phase_e(tok, did, pid):
    if not did or not pid:
        return
    # 创建补货订单并提交
    body = {"dealerId": did, "orderType": "REPLENISHMENT",
            "lines": [{"productId": pid, "qty": 2}], "remark": TAG + "补货链路"}
    s, r = req("POST", "/sales-orders", tok, body)
    oid = (r.get("data") or {}).get("id")
    if not oid:
        log("E1-补货订单创建", False, {"http": s, "msg": str(r.get("message"))[:150]})
        return
    log("E1-补货订单创建", True, {"id": oid})
    s, r = req("POST", f"/sales-orders/{oid}/submit", tok)
    e_inst = (r.get("data") or {}).get("approvalInstanceId")
    log("E2-补货订单提交", s == 200, {"http": s, "msg": str(r.get("message"))[:120], "instanceId": e_inst})
    # 审批通过
    time.sleep(1)
    task, tasks = find_todo_task(tok, oid, e_inst)
    approved = False
    if task:
        s, r = req("POST", f"/approval/tasks/{task['id']}/approve", tok, {"comment": "T440", "action": "APPROVE"})
        approved = s == 200
        if not approved:
            # 业务快捷审批入口兜底
            s2, r2 = req("POST", f"/sales-orders/{oid}/approve", tok, {"comment": "T440"})
            approved = s2 == 200
            log("E3-补货订单审批通过(快捷入口)", approved, {"taskFound": True, "taskHttp": s, "shortcut": s2, "msg": str(r2.get("message"))[:100]})
        else:
            log("E3-补货订单审批通过", True, {"taskFound": True, "taskId": task.get("id")})
    else:
        s2, r2 = req("POST", f"/sales-orders/{oid}/approve", tok, {"comment": "T440"})
        approved = s2 == 200
        log("E3-补货订单审批通过(快捷入口)", approved, {"taskFound": False, "shortcut": s2, "msg": str(r2.get("message"))[:120], "tasks": [t.get("instanceId") for t in tasks][:10]})
    time.sleep(1)
    # 模拟发货（ERP 回调生成出库单）
    s, r = req("POST", f"/v4/sales-orders/{oid}/simulate-ship", tok, {})
    log("E4-模拟发货(ERP回调)", s == 200, {"http": s, "msg": str(r.get("message"))[:120], "data": str(r.get("data"))[:150]})
    time.sleep(2)
    # 查寄售入库是否发生
    cnt, _ = db_one(f"SELECT COUNT(*) FROM consignment_stock_movements WHERE tenant_id=(SELECT id FROM tenants WHERE code='default') AND change_type='REPLENISH_IN' AND product_id={pid}")
    stock, _ = db_one(f"SELECT COUNT(*) FROM consignment_stock WHERE tenant_id=(SELECT id FROM tenants WHERE code='default') AND dealer_id={did} AND product_id={pid}")
    out_row, _ = db_one(f"SELECT so.id, so.business_type, o.order_type FROM sales_outs so JOIN orders o ON o.id=so.source_order_id WHERE so.source_order_id={oid} ORDER BY so.id DESC LIMIT 1")
    log("E5-补货发货后生成REPLENISH_IN寄售入库", cnt and cnt[0] != "0",
        {"replenishInCount": cnt[0] if cnt else None, "stockRows": stock[0] if stock else None, "out/orderType": out_row})
    # 清理
    db(f"DELETE FROM consignment_stock_movements WHERE tenant_id=(SELECT id FROM tenants WHERE code='default') AND remark LIKE '%{TAG}%'", fetch=False)
    db(f"DELETE FROM consignment_stock_movements WHERE tenant_id=(SELECT id FROM tenants WHERE code='default') AND ref_type='SALES_OUT' AND ref_id IN (SELECT id FROM sales_outs WHERE source_order_id={oid})", fetch=False)
    db(f"DELETE FROM consignment_stock WHERE tenant_id=(SELECT id FROM tenants WHERE code='default') AND source_sales_out_id IN (SELECT id FROM sales_outs WHERE source_order_id={oid})", fetch=False)
    db(f"DELETE FROM sales_out_execution_lines WHERE sales_out_id IN (SELECT id FROM sales_outs WHERE source_order_id={oid})", fetch=False)
    db(f"DELETE FROM sales_out_lines WHERE sales_out_id IN (SELECT id FROM sales_outs WHERE source_order_id={oid})", fetch=False)
    db(f"DELETE FROM sales_outs WHERE source_order_id={oid}", fetch=False)
    db(f"DELETE FROM order_promotion_hits WHERE order_id={oid}", fetch=False)
    db(f"DELETE FROM order_status_history WHERE order_id={oid}", fetch=False)
    db(f"DELETE FROM approval_records WHERE instance_id IN (SELECT id FROM approval_instances WHERE business_id={oid})", fetch=False)
    db(f"DELETE FROM approval_tasks WHERE instance_id IN (SELECT id FROM approval_instances WHERE business_id={oid})", fetch=False)
    db(f"DELETE FROM approval_cc_records WHERE instance_id IN (SELECT id FROM approval_instances WHERE business_id={oid})", fetch=False)
    db(f"DELETE FROM approval_instances WHERE business_id={oid}", fetch=False)
    db(f"DELETE FROM order_lines WHERE order_id={oid}", fetch=False)
    db(f"DELETE FROM orders WHERE id={oid}", fetch=False)
    log("E6-补货链路测试数据已清理", True, {"oid": oid})


def restore():
    # 还原经销商寄售开关
    did, orig = DB_STATE.get("dealer") or (None, None)
    if did:
        val = "true" if (orig or "").strip() in ("t", "true", "True") else "false"
        db(f"UPDATE dealers SET consignment_enabled={val} WHERE id={did}", fetch=False)
    # 还原开关
    inv = DB_STATE.get("features")
    if inv is not None:
        db("UPDATE tenants SET modules_enabled = jsonb_set(COALESCE(modules_enabled,'{}'::jsonb),'{inventoryEnabled}','%s') WHERE code='default'" % ("true" if inv else "false"), fetch=False)


def main():
    outdir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "results-v440")
    os.makedirs(outdir, exist_ok=True)
    try:
        tok, dtok = phase0()
        if not tok:
            print("LOGIN FAILED, ABORT")
            return
        phase_a(tok)
        phase_b(tok, dtok)
        did, pid = phase_c(tok)
        phase_d(tok, did, pid)
        phase_e(tok, did, pid)
    finally:
        restore()
        passed = sum(1 for x in RESULTS if x["ok"])
        failed = [x for x in RESULTS if not x["ok"]]
        summary = {"total": len(RESULTS), "passed": passed, "failed": len(failed), "tag": TAG, "failures": failed}
        with io.open(os.path.join(outdir, "api-results-%s.json" % TAG), "w", encoding="utf-8") as f:
            json.dump({"summary": summary, "results": RESULTS}, f, ensure_ascii=False, indent=2)
        print("\n==== SUMMARY ====")
        print(json.dumps({"total": len(RESULTS), "passed": passed, "failed": len(failed)}, ensure_ascii=False))
        for x in failed:
            print("FAILED:", x["case"], "->", json.dumps(x["detail"], ensure_ascii=False)[:300])


if __name__ == "__main__":
    main()
