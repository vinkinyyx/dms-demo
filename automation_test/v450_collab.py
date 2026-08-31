# -*- coding: utf-8 -*-
"""
v4.5.0 跨租户协同 - 自动化回归（API/DB 层 + 种子自清理）
覆盖缺口：
  G1 路径A 对码缺失阻断（40006、PO 回退 DRAFT、无台账/无厂家 SO）
  G2 普通供应商（无 manufacturer_tenant_id）不触发协同
  G3 幂等：重复 submit 不重复建 SO；同一张 GI 分批发货各生成一张收货单（V142 非唯一索引）
  G4 路径A 二次 partial-ship：两张收货单 + 复用同一张正式 PO 且数量不累加
  G5 序列号产品：batchNo/serialNo 透传 receipt_lines
  G6 厂家关闭进销存模块后 PurchaseOrderController 被拦（40006），经销商账号放行
  G7 DEALER_A2 链路（路径B 手工订单通道）：自动补建 dealer/默认仓、自动 PO 数量累计
  G8 vendor_order_code / customer_po_code 单号回显
  G9 confirm-full 后库存落到具体仓库（warehouse_id 非空），收货单 COMPLETED
  G10 V142 迁移断言：ux_ctdl_sales_out 不存在、ix_ctdl_sales_out 存在

运行：python v450_collab.py   （结果打印 PASS/FAIL，末尾输出汇总）
"""
import json, time, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
from v440_api_test import req, login, db, db_one, log, RESULTS

TAG = "T450" + time.strftime("%m%d%H%M%S")

MFR_A = "11111111-0000-0000-0000-000000000001"
MFR_B = "11111111-0000-0000-0000-000000000002"
A1 = "22222222-0000-0000-0000-000000000001"
A2 = "22222222-0000-0000-0000-000000000002"

MFR_A_WH = 26          # 厂家 MFR_A 仓库（库存所在仓）
A1_WH = 25             # 经销商 A1 自身仓库
A1_SELF_DEALER = 17    # A1 自身经销商主体
A1_PLATFORM_SUPPLIER = 9
A2_PLATFORM_SUPPLIER = 10
MFR_DEALER_A1 = 18     # MFR_A 侧客户主数据（绑定 A1）
MFR_DEALER_A2 = 52     # MFR_A 侧客户主数据（绑定 A2）

# 既有对码（A1）：厂家 25<->经销 26、厂家 27<->经销 28；（A2）：厂家 36<->38、41<->43、50<->52
A1_MAP_MFR_PID, A1_MAP_DLR_PID = 25, 26
A1_MAP2_MFR_PID, A1_MAP2_DLR_PID = 27, 28
BATCH_MFR_25 = "BATCH-COLLAB-001"   # 厂家 p25 批次，库存 95
BATCH_MFR_27 = "BATCH-B-9"          # 厂家 p27 批次，库存 42

STATE = {"ids": []}   # 记录本脚本创建的实体 id，用于清理


# ================= 工具 =================
def q1(sql):
    row, txt = db_one(sql)
    return row

def db_exec(sql):
    code, rows, txt = db(sql, fetch=False)
    return code, txt

def biz_code(st, res):
    if isinstance(res, dict):
        return res.get("code")
    return None

def create_po(token, supplier_id, wh_id, lines, remark):
    body = {"supplierId": supplier_id, "warehouseId": wh_id,
            "remark": remark + " " + TAG,
            "lines": [{"productId": p, "qty": q, "unitPrice": 100} for p, q in lines]}
    st, res = req("POST", "/purchase-orders", token, body)
    return st, res

def po_by_id(po_id):
    return q1(f"SELECT id, code, status, vendor_order_code FROM purchase_orders WHERE id={po_id}")

def latest_links(link_type=None, limit=20):
    cond = f"link_type='{link_type}'" if link_type else "1=1"
    code, rows, txt = db(
        f"SELECT id, link_type, COALESCE(po_id::text,''), COALESCE(sales_order_id::text,''), "
        f"COALESCE(sales_out_id::text,''), COALESCE(receipt_id::text,''), COALESCE(line_refs::text,'') "
        f"FROM cross_tenant_doc_links WHERE {cond} ORDER BY id DESC LIMIT {limit}")
    return rows if code == 0 else []

def gi_lines(gi_id):
    code, rows, txt = db(
        f"SELECT id, product_id, warehouse_id, expected_qty, COALESCE(shipped_qty,0) "
        f"FROM sales_out_lines WHERE sales_out_id={gi_id} AND COALESCE(expected_qty,0)>0 ORDER BY id")
    return rows if code == 0 else []

def gi_by_source_order(so_id):
    return q1(f"SELECT id, code, status FROM sales_outs WHERE source_order_id={so_id} "
              f"AND deleted_at IS NULL ORDER BY id DESC LIMIT 1")


# ================= 0. 残留清理（v4.5.0 手工测试遗留） =================
def cleanup_legacy():
    legacy = """
    DELETE FROM cross_tenant_doc_links WHERE id IN (1,2,3);
    DELETE FROM receipt_lines WHERE receipt_id IN (244,245);
    DELETE FROM receipts WHERE id IN (244,245);
    DELETE FROM purchase_order_lines WHERE po_id IN (241,242);
    DELETE FROM purchase_orders WHERE id IN (241,242);
    DELETE FROM sales_out_lines WHERE sales_out_id IN (126,127,128);
    DELETE FROM sales_outs WHERE id IN (126,127,128);
    DELETE FROM order_lines WHERE order_id = 294;
    DELETE FROM orders WHERE id = 294;
    DELETE FROM inventory WHERE warehouse_id IS NULL;
    """
    code, txt = db_exec(legacy)
    log("C0-清理手工测试残留(台账3/PO241-242/SO294/GI126-128/RC244-245/幽灵库存)", code == 0,
        {"code": code, "err": txt[:200] if code != 0 else "ok"})


# ================= 种子数据（TAG 标记） =================
def seed():
    # --- A1 普通供应商（无 manufacturer_tenant_id）---
    row = q1(f"SELECT id FROM suppliers WHERE tenant_id='{A1}' AND code='{TAG}-NORM'")
    if not row:
        code, txt = db_exec(
            f"INSERT INTO suppliers (tenant_id, code, name, status, level, created_at, updated_at) "
            f"VALUES ('{A1}', '{TAG}-NORM', '{TAG}普通供应商', 'active', 1, now(), now())")
    row = q1(f"SELECT id FROM suppliers WHERE tenant_id='{A1}' AND code='{TAG}-NORM'")
    STATE["norm_supplier"] = row[0]

    # --- A1 未对码经销商产品（路径A 对码缺失）---
    row = q1(f"SELECT id FROM products WHERE tenant_id='{A1}' AND code='{TAG}-NOMAP'")
    if not row:
        db_exec(
            f"INSERT INTO products (tenant_id, code, name_cn, unit, tax_rate, current_price, status, "
            f"is_serial_managed, created_at, updated_at, version) "
            f"VALUES ('{A1}', '{TAG}-NOMAP', '{TAG}未对码物料', '个', 0.13, 100, 'active', false, now(), now(), 0)")
    STATE["a1_nomap_pid"] = q1(f"SELECT id FROM products WHERE tenant_id='{A1}' AND code='{TAG}-NOMAP'")[0]

    # --- 序列号产品：厂家侧 + A1 侧 + 对码 + 厂家库存 + stock_serials ---
    row = q1(f"SELECT id FROM products WHERE tenant_id='{MFR_A}' AND code='{TAG}-MFR-SER'")
    if not row:
        db_exec(
            f"INSERT INTO products (tenant_id, code, name_cn, unit, tax_rate, current_price, status, "
            f"is_serial_managed, created_at, updated_at, version) "
            f"VALUES ('{MFR_A}', '{TAG}-MFR-SER', '{TAG}序列号厂家物料', '个', 0.13, 1000, 'active', true, now(), now(), 0)")
    mfr_ser = q1(f"SELECT id FROM products WHERE tenant_id='{MFR_A}' AND code='{TAG}-MFR-SER'")[0]
    row = q1(f"SELECT id FROM products WHERE tenant_id='{A1}' AND code='{TAG}-DLR-SER'")
    if not row:
        db_exec(
            f"INSERT INTO products (tenant_id, code, name_cn, unit, tax_rate, current_price, status, "
            f"is_serial_managed, created_at, updated_at, version) "
            f"VALUES ('{A1}', '{TAG}-DLR-SER', '{TAG}序列号经销物料', '个', 0.13, 1000, 'active', true, now(), now(), 0)")
    dlr_ser = q1(f"SELECT id FROM products WHERE tenant_id='{A1}' AND code='{TAG}-DLR-SER'")[0]
    row = q1(f"SELECT id FROM product_mappings WHERE manufacturer_tenant_id='{MFR_A}' AND dealer_tenant_id='{A1}' "
             f"AND manufacturer_product_id={mfr_ser} AND deleted_at IS NULL")
    if not row:
        db_exec(
            f"INSERT INTO product_mappings (manufacturer_tenant_id, dealer_tenant_id, manufacturer_product_id, "
            f"dealer_product_id, manufacturer_product_code, dealer_product_code, conversion_rate, status, "
            f"remark, created_at, updated_at, version) "
            f"VALUES ('{MFR_A}', '{A1}', {mfr_ser}, {dlr_ser}, '{TAG}-MFR-SER', '{TAG}-DLR-SER', 1, 'active', '{TAG}', now(), now(), 0)")
    # 厂家序列号库存（批次 + qty=1 库存行 + 1 条 QUALIFIED 在库序列号）
    ser_batch = f"{TAG}-BATCH-SER"
    ser_no = f"{TAG}-SN-0001"
    db_exec(
        f"INSERT INTO inventory (tenant_id, dealer_id, warehouse_id, product_id, batch_no, serial_no, "
        f"qty, in_source, stock_status, created_at, updated_at, version) "
        f"SELECT '{MFR_A}', {MFR_DEALER_A1}, {MFR_A_WH}, {mfr_ser}, '{ser_batch}', '{ser_no}', 1, 'PURCHASE_IN', 'IN_STOCK', now(), now(), 0 "
        f"WHERE NOT EXISTS (SELECT 1 FROM inventory WHERE tenant_id='{MFR_A}' AND product_id={mfr_ser} AND serial_no='{ser_no}')")
    db_exec(
        f"INSERT INTO stock_serials (tenant_id, warehouse_id, product_id, batch_no, serial_no, stock_status, "
        f"source_doc_type, received_at, updated_at) "
        f"SELECT '{MFR_A}', {MFR_A_WH}, {mfr_ser}, '{ser_batch}', '{ser_no}', 'QUALIFIED', 'PURCHASE_RECEIPT', now(), now() "
        f"WHERE NOT EXISTS (SELECT 1 FROM stock_serials WHERE tenant_id='{MFR_A}' AND serial_no='{ser_no}')")

    # --- G7 路径B 前置主数据（必须在 36/50 库存之前：inventory.dealer_id 外键依赖 dealers）---
    # MFR_A 侧 A2 客户主数据（tenant_dealer_bindings 引用 dealer_id=52，但 dealers 行缺失=悬空绑定，补建对齐）
    if not q1(f"SELECT id FROM dealers WHERE id={MFR_DEALER_A2}"):
        c_dealer, t_dealer = db_exec(
            f"INSERT INTO dealers (id, tenant_id, code, name, level, status, created_at, updated_at, version) "
            f"VALUES ({MFR_DEALER_A2}, '{MFR_A}', 'D-COLLAB-A2', 'A2CST', 'VIP', 'active', now(), now(), 0)")
        if c_dealer != 0:
            print(f"[seed][WARN] insert dealer {MFR_DEALER_A2} failed code={c_dealer}: {t_dealer[:300]}")
        STATE["mfr_dealer_a2_created"] = True
    if not q1(f"SELECT id FROM dealers WHERE id={MFR_DEALER_A2}"):
        print(f"[seed][ERROR] dealer {MFR_DEALER_A2} still missing after insert; G7 auth/inventory FK will fail")
    # 手工订单通道授权：MFR_A 给客户 52(A2) 一条通配 ORDER 授权
    # （手工 POST /orders 走 OrderService 授权校验；协同自动建单不经此校验，故仅 G7 需要；product_id/终端均 NULL=通配）
    if not q1(f"SELECT id FROM authorizations WHERE tenant_id='{MFR_A}' AND dealer_id={MFR_DEALER_A2} "
              f"AND auth_type='ORDER' AND remark='{TAG}'"):
        c_auth, t_auth = db_exec(
            f"INSERT INTO authorizations (tenant_id, dealer_id, auth_type, product_id, valid_from, valid_to, "
            f"status, source, remark, created_at, updated_at, version) "
            f"SELECT '{MFR_A}', {MFR_DEALER_A2}, 'ORDER', NULL, DATE '2026-01-01', DATE '2027-12-31', "
            f"'active', 'contract', '{TAG}', now(), now(), 0 "
            f"WHERE NOT EXISTS (SELECT 1 FROM authorizations WHERE tenant_id='{MFR_A}' "
            f"AND dealer_id={MFR_DEALER_A2} AND auth_type='ORDER' AND remark='{TAG}')")
        if c_auth != 0:
            print(f"[seed][WARN] insert authorization failed code={c_auth}: {t_auth[:300]}")
    if not q1(f"SELECT id FROM authorizations WHERE tenant_id='{MFR_A}' AND dealer_id={MFR_DEALER_A2} "
              f"AND auth_type='ORDER' AND remark='{TAG}'"):
        print("[seed][ERROR] ORDER wildcard authorization for dealer 52 missing; G7a 手工建单 will be 400")

    # --- A2 链路（路径B）：厂家 36/50 库存（对码 36<->38、50<->52 已存在）---
    c_i36, t_i36 = db_exec(
        f"INSERT INTO inventory (tenant_id, dealer_id, warehouse_id, product_id, batch_no, serial_no, "
        f"qty, in_source, stock_status, created_at, updated_at, version) "
        f"SELECT '{MFR_A}', {MFR_DEALER_A2}, {MFR_A_WH}, 36, '{TAG}-BATCH-A2-36', NULL, 20, 'PURCHASE_IN', 'IN_STOCK', now(), now(), 0 "
        f"WHERE NOT EXISTS (SELECT 1 FROM inventory WHERE tenant_id='{MFR_A}' AND product_id=36 AND batch_no='{TAG}-BATCH-A2-36')")
    c_i50, t_i50 = db_exec(
        f"INSERT INTO inventory (tenant_id, dealer_id, warehouse_id, product_id, batch_no, serial_no, "
        f"qty, in_source, stock_status, created_at, updated_at, version) "
        f"SELECT '{MFR_A}', {MFR_DEALER_A2}, {MFR_A_WH}, 50, '{TAG}-BATCH-A2-50', NULL, 10, 'PURCHASE_IN', 'IN_STOCK', now(), now(), 0 "
        f"WHERE NOT EXISTS (SELECT 1 FROM inventory WHERE tenant_id='{MFR_A}' AND product_id=50 AND batch_no='{TAG}-BATCH-A2-50')")
    for _pid, _cc, _tt in ((36, c_i36, t_i36), (50, c_i50, t_i50)):
        if _cc != 0:
            print(f"[seed][WARN] insert inventory product {_pid} failed code={_cc}: {_tt[:300]}")
    if not q1(f"SELECT id FROM inventory WHERE tenant_id='{MFR_A}' AND product_id=36 AND batch_no='{TAG}-BATCH-A2-36'"):
        print("[seed][ERROR] MFR inventory product 36 (A2 batch) missing; G7 出库将无库存")
    if not q1(f"SELECT id FROM inventory WHERE tenant_id='{MFR_A}' AND product_id=50 AND batch_no='{TAG}-BATCH-A2-50'"):
        print("[seed][ERROR] MFR inventory product 50 (A2 batch) missing; G7 出库将无库存")

    STATE.update({"mfr_ser": mfr_ser, "dlr_ser": dlr_ser, "ser_batch": ser_batch, "ser_no": ser_no})
    # 库存快照：协同三租户 seed 后的 inventory id->qty，清理时精准删除新增行/恢复扣减行
    snap = {}
    code, rows, _txt = db(
        "SELECT id || ':' || COALESCE(qty,0) FROM inventory WHERE tenant_id IN ("
        f"'{MFR_A}','{A1}','{A2}')")
    for r in rows:
        if r:
            inv_id, qty = r[0].split(":")
            snap[inv_id] = qty
    STATE["inv_snapshot"] = snap
    log("S0-种子数据就绪(普通供应商/未对码物料/序列号产品+库存/A2库存)", True,
        {"normSupplier": STATE["norm_supplier"], "mfrSer": mfr_ser, "dlrSer": dlr_ser,
         "invSnapshot": len(snap)})


# ================= G1 路径A 对码缺失阻断 =================
def case_g1(tok_a1):
    st, res = create_po(tok_a1, A1_PLATFORM_SUPPLIER, A1_WH,
                        [(int(STATE["a1_nomap_pid"]), 1)], f"{TAG} G1未对码采购")
    code = biz_code(st, res)
    po_id = res.get("data", {}).get("id") if isinstance(res, dict) else None
    log("G1a-未对码物料采购单创建(DRAFT)", st == 200 and po_id is not None,
        {"http": st, "poId": po_id})
    if not po_id:
        log("G1b-提交被阻断(40006)且PO回退DRAFT", False, {"err": "未取到 poId", "res": str(res)[:200]})
        log("G1c-无协同台账", False, {"err": "无 poId"})
        return
    STATE.setdefault("cleanup_pos", []).append(po_id)
    st2, res2 = req("POST", f"/purchase-orders/{po_id}/submit", tok_a1, {})
    code2 = biz_code(st2, res2)
    msg2 = str(res2.get("message") if isinstance(res2, dict) else res2)
    log("G1b-提交未对码采购被阻断(40006)", st2 != 200 and code2 == 40006,
        {"http": st2, "code": code2, "msg": msg2[:180]})
    row = po_by_id(po_id)
    log("G1c-PO 回退 DRAFT(不残留 PENDING)", row and row[2] == "DRAFT",
        {"po": row})
    links = [r for r in latest_links() if r[2] == str(po_id)]
    log("G1d-无协同台账/无厂家SO", len(links) == 0, {"links": len(links)})


# ================= G2 普通供应商不触发协同 =================
def case_g2(tok_a1):
    st, res = create_po(tok_a1, int(STATE["norm_supplier"]), A1_WH,
                        [(A1_MAP_DLR_PID, 2)], f"{TAG} G2普通供应商采购")
    po_id = res.get("data", {}).get("id") if st == 200 and isinstance(res, dict) else None
    log("G2a-普通供应商采购单创建", st == 200 and po_id is not None, {"http": st, "poId": po_id})
    if po_id:
        st2, res2 = req("POST", f"/purchase-orders/{po_id}/submit", tok_a1, {})
        code2 = biz_code(st2, res2)
        log("G2b-提交成功(不触发协同报错)", st2 == 200 and code2 in (None, 0), {"http": st2, "code": code2})
        links = [r for r in latest_links() if r[2] == str(po_id)]
        log("G2c-无 PO_TO_SALES_ORDER 台账", len(links) == 0, {"links": len(links)})
        row = po_by_id(po_id)
        log("G2d-vendor_order_code 为空(无厂家单号回写)", row and not row[3],
            {"vendorOrderCode": row[3] if row else None})
        STATE.setdefault("cleanup_pos", []).append(po_id)


# ================= G3+G4+G8 路径A 主链路：协同建 SO → 审批建 GI → 分两批 partial-ship =================
def case_path_a(tok_a1, tok_mfr):
    st, res = create_po(tok_a1, A1_PLATFORM_SUPPLIER, A1_WH,
                        [(A1_MAP_DLR_PID, 8), (A1_MAP2_DLR_PID, 5)], f"{TAG} G3G4路径A主链路")
    po_id = res.get("data", {}).get("id") if st == 200 and isinstance(res, dict) else None
    log("A0-路径A采购单创建", po_id is not None, {"http": st, "poId": po_id})
    STATE.setdefault("cleanup_pos", []).append(po_id)
    if not po_id:
        return

    n_before = len(latest_links("PO_TO_SALES_ORDER"))
    st, res = req("POST", f"/purchase-orders/{po_id}/submit", tok_a1, {})
    code = biz_code(st, res)
    log("G3a-路径A提交成功", st == 200 and code in (None, 0), {"http": st, "code": code})

    row = po_by_id(po_id)
    vendor_code = row[3] if row else None
    log("G8a-PO 回写 vendor_order_code", bool(vendor_code), {"vendorOrderCode": vendor_code})

    link = None
    for r in latest_links("PO_TO_SALES_ORDER"):
        if r[2] == str(po_id):
            link = r
            break
    log("G3b-生成 PO_TO_SALES_ORDER 台账", link is not None, {"link": link[:5] if link else None})
    so_id = int(link[3]) if link and link[3] else None

    # G8b：厂家侧 SO customer_po_code = PO 号
    if so_id:
        so_row = q1(f"SELECT code, status, customer_po_code FROM orders WHERE id={so_id}")
        log("G8b-厂家SO customer_po_code=PO号", so_row and so_row[2] == row[1],
            {"soCode": so_row[0] if so_row else None, "customerPoCode": so_row[2] if so_row else None,
             "poCode": row[1] if row else None})
        log("G3c-协同 SO 初始 DRAFT", so_row and so_row[1] == "DRAFT", {"status": so_row[1] if so_row else None})

    # G3d：重复 submit 不生效（状态机拦截，业务码 40009，HTTP 仍 200），且不重复建 SO/台账
    st2, res2 = req("POST", f"/purchase-orders/{po_id}/submit", tok_a1, {})
    code2 = biz_code(st2, res2)
    n_after = len(latest_links("PO_TO_SALES_ORDER"))
    log("G3d-重复submit被拦截且不重复建台账", code2 == 40009 and n_after == n_before + 1,
        {"http2": st2, "code2": code2, "linksBefore": n_before, "linksAfter": n_after})

    if not so_id:
        return

    # 厂家侧提交+审批 → 自动建 DRAFT GI
    st, _ = req("POST", f"/orders/{so_id}/submit", tok_mfr, {})
    st, _ = req("POST", f"/orders/{so_id}/approve", tok_mfr, {})
    gi = gi_by_source_order(so_id)
    log("G3e-审批后自动生成 DRAFT GI", gi is not None, {"gi": gi})
    if not gi:
        return
    gi_id = int(gi[0])
    STATE.setdefault("cleanup_gis", []).append(gi_id)
    STATE.setdefault("cleanup_sos", []).append(so_id)

    lines = gi_lines(gi_id)
    exp25 = next((l for l in lines if l[1] == str(A1_MAP_MFR_PID)), None)
    exp27 = next((l for l in lines if l[1] == str(A1_MAP2_MFR_PID)), None)
    log("G3f-GI 应发行含两个对码产品", exp25 is not None and exp27 is not None,
        {"lines": [l[:4] for l in lines]})
    if not exp25:
        return

    # 第一批 partial-ship：p25 发 3
    ship1 = {"lines": [{"expectedLineId": int(exp25[0]), "productId": A1_MAP_MFR_PID,
                        "warehouseId": MFR_A_WH, "qty": 3, "batchNo": BATCH_MFR_25, "unitPrice": 100}]}
    st, res = req("POST", f"/sales-outs/{gi_id}/partial-ship", tok_mfr, ship1)
    code = biz_code(st, res)
    log("G4a-第一批 partial-ship 成功", st == 200 and code in (None, 0), {"http": st, "code": code,
        "msg": str(res.get("message") if isinstance(res, dict) else "")[:150]})

    rc_links_1 = [r for r in latest_links("SALES_OUT_TO_RECEIPT") if r[4] == str(gi_id)]
    log("G4b-第一批生成 1 张收货单台账", len(rc_links_1) == 1, {"count": len(rc_links_1)})

    # 第二批 partial-ship：p25 再发 2 + p27 发 5
    ship2 = {"lines": [
        {"expectedLineId": int(exp25[0]), "productId": A1_MAP_MFR_PID,
         "warehouseId": MFR_A_WH, "qty": 2, "batchNo": BATCH_MFR_25, "unitPrice": 100},
        {"expectedLineId": int(exp27[0]), "productId": A1_MAP2_MFR_PID,
         "warehouseId": MFR_A_WH, "qty": 5, "batchNo": BATCH_MFR_27, "unitPrice": 100}]}
    st, res = req("POST", f"/sales-outs/{gi_id}/partial-ship", tok_mfr, ship2)
    code = biz_code(st, res)
    log("G4c-第二批 partial-ship 成功(缺陷1:二次发货不再整体跳过)", st == 200 and code in (None, 0),
        {"http": st, "code": code, "msg": str(res.get("message") if isinstance(res, dict) else "")[:150]})

    rc_links = [r for r in latest_links("SALES_OUT_TO_RECEIPT") if r[4] == str(gi_id)]
    log("G4d-两批共生成 2 张收货单(V142 非唯一索引)", len(rc_links) == 2, {"count": len(rc_links)})

    # G4e：两张收货单关联同一张正式 PO（路径A PO，不累加）
    po_ids = sorted({r[2] for r in rc_links})
    log("G4e-两张收货单复用同一张正式PO", len(po_ids) == 1 and str(po_id) in po_ids,
        {"poIds": po_ids, "pathAPo": po_id})
    # PO 行数量仍为订购量（8 / 5），未被发货累加
    pol = q1(f"SELECT string_agg(product_id||':'||qty, ',' ORDER BY seq) FROM purchase_order_lines WHERE po_id={po_id}")
    log("G4f-正式PO数量不累加(保持订购量 26:8/28:5)",
        pol and f"{A1_MAP_DLR_PID}:8" in pol[0] and f"{A1_MAP2_DLR_PID}:5" in pol[0],
        {"lines": pol[0] if pol else None})

    # G9：第一张收货单 confirm-full → 库存落到 A1 具体仓库
    rc1_id = int(rc_links_1[0][5]) if rc_links_1 else None
    STATE.setdefault("cleanup_rcs", []).extend([int(r[5]) for r in rc_links if r[5]])
    if rc1_id:
        st, res = req("POST", f"/receipts/{rc1_id}/confirm-full", tok_a1, {})
        code = biz_code(st, res)
        log("G9a-收货单 confirm-full 成功", st == 200 and code in (None, 0),
            {"http": st, "code": code, "msg": str(res.get("message") if isinstance(res, dict) else "")[:150]})
        rc = q1(f"SELECT status, dealer_id, warehouse_id FROM receipts WHERE id={rc1_id}")
        log("G9b-收货单 COMPLETED 且 dealer/warehouse 非空",
            rc and rc[0] == "COMPLETED" and rc[1] and rc[2],
            {"status": rc[0] if rc else None, "dealerId": rc[1] if rc else None,
             "warehouseId": rc[2] if rc else None})
        inv = q1(f"SELECT id, warehouse_id, dealer_id, qty, batch_no FROM inventory "
                 f"WHERE tenant_id='{A1}' AND product_id={A1_MAP_DLR_PID} "
                 f"AND batch_no='{BATCH_MFR_25}' AND warehouse_id IS NOT NULL ORDER BY id DESC LIMIT 1")
        log("G9c-库存落到具体仓库(warehouse_id 非空, 无幽灵库存)",
            inv and inv[1] is not None and float(inv[3]) >= 3,
            {"inv": inv})
        no_ghost = q1(f"SELECT count(*) FROM inventory WHERE tenant_id='{A1}' AND warehouse_id IS NULL")
        log("G9d-A1 无幽灵库存(warehouse_id IS NULL = 0)", no_ghost and no_ghost[0] == "0",
            {"ghostCount": no_ghost[0] if no_ghost else None})


# ================= G5 序列号透传（路径A） =================
def case_g5(tok_a1, tok_mfr):
    mfr_ser = int(STATE["mfr_ser"]); dlr_ser = int(STATE["dlr_ser"])
    ser_batch = STATE["ser_batch"]; ser_no = STATE["ser_no"]
    st, res = create_po(tok_a1, A1_PLATFORM_SUPPLIER, A1_WH,
                        [(dlr_ser, 1)], f"{TAG} G5序列号采购")
    po_id = res.get("data", {}).get("id") if st == 200 and isinstance(res, dict) else None
    log("G5a-序列号采购单创建", po_id is not None, {"http": st, "poId": po_id})
    STATE.setdefault("cleanup_pos", []).append(po_id)
    if not po_id:
        return
    st, _ = req("POST", f"/purchase-orders/{po_id}/submit", tok_a1, {})
    link = next((r for r in latest_links("PO_TO_SALES_ORDER") if r[2] == str(po_id)), None)
    so_id = int(link[3]) if link and link[3] else None
    log("G5b-序列号物料协同建 SO 成功", so_id is not None, {"soId": so_id})
    if not so_id:
        return
    STATE.setdefault("cleanup_sos", []).append(so_id)
    req("POST", f"/orders/{so_id}/submit", tok_mfr, {})
    req("POST", f"/orders/{so_id}/approve", tok_mfr, {})
    gi = gi_by_source_order(so_id)
    if not gi:
        log("G5c-自动生成 GI", False, {"err": "无 GI"})
        return
    gi_id = int(gi[0])
    STATE.setdefault("cleanup_gis", []).append(gi_id)
    exp = gi_lines(gi_id)
    if not exp:
        log("G5c-自动生成 GI", False, {"err": "GI 无应发行"})
        return
    ship = {"lines": [{"expectedLineId": int(exp[0][0]), "productId": mfr_ser,
                       "warehouseId": MFR_A_WH, "qty": 1, "batchNo": ser_batch,
                       "serialNo": ser_no, "unitPrice": 1000}]}
    st, res = req("POST", f"/sales-outs/{gi_id}/partial-ship", tok_mfr, ship)
    code = biz_code(st, res)
    log("G5c-序列号 partial-ship 成功", st == 200 and code in (None, 0),
        {"http": st, "code": code, "msg": str(res.get("message") if isinstance(res, dict) else "")[:180]})
    rc_link = next((r for r in latest_links("SALES_OUT_TO_RECEIPT") if r[4] == str(gi_id)), None)
    if not rc_link:
        log("G5d-收货行透传 batchNo/serialNo", False, {"err": "无收货台账"})
        return
    rc_id = int(rc_link[5])
    STATE.setdefault("cleanup_rcs", []).append(rc_id)
    rl = q1(f"SELECT batch_no, serial_no, expected_qty FROM receipt_lines WHERE receipt_id={rc_id}")
    log("G5d-收货行透传 batchNo/serialNo",
        rl and rl[0] == ser_batch and rl[1] == ser_no,
        {"batchNo": rl[0] if rl else None, "serialNo": rl[1] if rl else None})
    refs = rc_link[6]
    log("G5e-台账 line_refs 记录 outLineId/serialNo", ser_no in (refs or ""),
        {"lineRefs": (refs or "")[:200]})


# ================= G7 DEALER_A2 路径B 链路（手工订单通道，自动补建主体/仓库 + 自动 PO 累计） =================
def case_g7(tok_mfr, tok_a2):
    # 厂家侧手工建销售订单（dealer = MFR 侧 A2 客户主数据 52），产品 36/50
    body = {"dealerId": MFR_DEALER_A2, "remark": f"{TAG} G7 A2路径B",
            "lines": [{"productId": 36, "qty": 10, "unitPrice": 200, "taxRate": 0.13},
                      {"productId": 50, "qty": 6, "unitPrice": 300, "taxRate": 0.13}]}
    st, res = req("POST", "/orders", tok_mfr, body)
    so_id = None
    if st == 200 and isinstance(res, dict):
        data = res.get("data", {})
        so_id = (data.get("order") or {}).get("id") or data.get("id")
    log("G7a-厂家手工建销售订单(A2客户)", so_id is not None, {"http": st, "soId": so_id,
        "msg": str(res.get("message") if isinstance(res, dict) else "")[:150]})
    if not so_id:
        return
    STATE.setdefault("cleanup_sos", []).append(so_id)
    req("POST", f"/orders/{so_id}/submit", tok_mfr, {})
    req("POST", f"/orders/{so_id}/approve", tok_mfr, {})
    gi = gi_by_source_order(so_id)
    log("G7b-审批自动建 GI", gi is not None, {"gi": gi})
    if not gi:
        return
    gi_id = int(gi[0])
    STATE.setdefault("cleanup_gis", []).append(gi_id)
    lines = gi_lines(gi_id)
    exp36 = next((l for l in lines if l[1] == "36"), None)
    exp50 = next((l for l in lines if l[1] == "50"), None)
    if not exp36 or not exp50:
        log("G7c-第一批发货", False, {"err": "缺应发行", "lines": [l[:4] for l in lines]})
        return
    # 第一批：p36 发 4
    ship1 = {"lines": [{"expectedLineId": int(exp36[0]), "productId": 36,
                        "warehouseId": MFR_A_WH, "qty": 4, "batchNo": f"{TAG}-BATCH-A2-36",
                        "unitPrice": 200}]}
    st, res = req("POST", f"/sales-outs/{gi_id}/partial-ship", tok_mfr, ship1)
    code = biz_code(st, res)
    log("G7c-第一批 partial-ship(p36 x4) 成功", st == 200 and code in (None, 0),
        {"http": st, "code": code, "msg": str(res.get("message") if isinstance(res, dict) else "")[:180]})
    # 第二批：p36 再发 3 + p50 发 2
    ship2 = {"lines": [
        {"expectedLineId": int(exp36[0]), "productId": 36,
         "warehouseId": MFR_A_WH, "qty": 3, "batchNo": f"{TAG}-BATCH-A2-36", "unitPrice": 200},
        {"expectedLineId": int(exp50[0]), "productId": 50,
         "warehouseId": MFR_A_WH, "qty": 2, "batchNo": f"{TAG}-BATCH-A2-50", "unitPrice": 300}]}
    st, res = req("POST", f"/sales-outs/{gi_id}/partial-ship", tok_mfr, ship2)
    code = biz_code(st, res)
    log("G7d-第二批 partial-ship(p36 x3 + p50 x2) 成功", st == 200 and code in (None, 0),
        {"http": st, "code": code, "msg": str(res.get("message") if isinstance(res, dict) else "")[:180]})

    rc_links = [r for r in latest_links("SALES_OUT_TO_RECEIPT") if r[4] == str(gi_id)]
    log("G7e-两批生成 2 张 A2 收货单", len(rc_links) == 2, {"count": len(rc_links)})

    # 自动补建 A2 经销商主体 + 默认仓
    d2 = q1(f"SELECT id, code, name FROM dealers WHERE tenant_id='{A2}' AND code='DEALER-SELF'")
    w2 = q1(f"SELECT id, code, name FROM warehouses WHERE tenant_id='{A2}' AND code='COLLAB-DEFAULT-WH'")
    log("G7f-自动补建 A2 经销商主体(DEALER-SELF)", d2 is not None, {"dealer": d2})
    log("G7g-自动补建 A2 默认仓(COLLAB-DEFAULT-WH)", w2 is not None, {"warehouse": w2})

    # 路径B 自动 PO：两张收货单复用同一张自动 PO，数量累计 p38=7 / p52=2
    auto_po_ids = sorted({r[2] for r in rc_links})
    log("G7h-路径B复用同一张自动PO(缺陷2:不重复补PO)", len(auto_po_ids) == 1, {"poIds": auto_po_ids})
    if auto_po_ids:
        auto_po = int(next(iter(auto_po_ids)))
        STATE.setdefault("cleanup_pos", []).append(auto_po)
        pol = q1(f"SELECT string_agg(product_id||':'||qty, ',' ORDER BY seq) FROM purchase_order_lines WHERE po_id={auto_po}")
        log("G7i-自动PO数量按批累计(38:7/52:2)",
            pol and "38:7" in pol[0] and "52:2" in pol[0], {"lines": pol[0] if pol else None})
        po_row = q1(f"SELECT status, vendor_order_code FROM purchase_orders WHERE id={auto_po}")
        log("G7j-自动PO为 APPROVED 且回写厂家单号",
            po_row and po_row[0] == "APPROVED" and bool(po_row[1]),
            {"status": po_row[0] if po_row else None, "vendorCode": po_row[1] if po_row else None})

    STATE.setdefault("cleanup_rcs", []).extend([int(r[5]) for r in rc_links if r[5]])
    # G9 补充：A2 收货单 confirm-full 后库存落到自动补建的具体仓库
    if rc_links and w2:
        rc_id = int(rc_links[0][5])
        st, res = req("POST", f"/receipts/{rc_id}/confirm-full", tok_a2, {})
        code = biz_code(st, res)
        log("G7k-A2收货单 confirm-full 成功", st == 200 and code in (None, 0),
            {"http": st, "code": code, "msg": str(res.get("message") if isinstance(res, dict) else "")[:150]})
        inv = q1(f"SELECT warehouse_id, dealer_id, qty FROM inventory "
                 f"WHERE tenant_id='{A2}' AND product_id=38 AND warehouse_id IS NOT NULL ORDER BY id DESC LIMIT 1")
        log("G7l-A2库存落到自动补建仓库(非幽灵)", inv and inv[0] is not None, {"inv": inv})


# ================= G6 厂家关闭进销存模块后 API 拦截 =================
def case_g6(tok_mfr, tok_a1):
    saved = q1(f"SELECT modules_enabled::text FROM tenants WHERE id='{MFR_A}'")
    try:
        db_exec(f"UPDATE tenants SET modules_enabled=jsonb_set(COALESCE(modules_enabled,'{{}}'::jsonb), "
                f"'{{inventoryEnabled}}', 'false') WHERE id='{MFR_A}'")
        st, res = req("POST", "/purchase-orders", tok_mfr,
                      {"supplierId": 1, "warehouseId": MFR_A_WH, "lines": [{"productId": 25, "qty": 1}]})
        code = biz_code(st, res)
        msg = str(res.get("message") if isinstance(res, dict) else "")
        log("G6a-厂家关闭进销存后采购接口被拦(40006)", code == 40006 or "未启用" in msg or st == 403,
            {"http": st, "code": code, "msg": msg[:120]})
        # 经销商账号不受影响（isDealerUser 放行）
        st2, res2 = req("GET", "/warehouses", tok_a1)
        log("G6b-经销商账号进销存接口放行", st2 == 200, {"http": st2})
        # 重新打开后厂家恢复
        db_exec(f"UPDATE tenants SET modules_enabled=jsonb_set(COALESCE(modules_enabled,'{{}}'::jsonb), "
                f"'{{inventoryEnabled}}', 'true') WHERE id='{MFR_A}'")
        st3, res3 = req("GET", "/warehouses", tok_mfr)
        log("G6c-重新开启后厂家接口恢复", st3 == 200, {"http": st3})
    finally:
        # 兜底还原
        db_exec(f"UPDATE tenants SET modules_enabled='{saved[0]}'::jsonb WHERE id='{MFR_A}'")
        chk = q1(f"SELECT modules_enabled::text FROM tenants WHERE id='{MFR_A}'")
        log("G6d-功能开关已还原", chk and chk[0] == saved[0], {"restored": chk[0] if chk else None})


# ================= G10 V142 迁移断言 =================
def case_g10():
    ux = q1("SELECT count(*) FROM pg_indexes WHERE tablename='cross_tenant_doc_links' AND indexname='ux_ctdl_sales_out'")
    ix = q1("SELECT count(*) FROM pg_indexes WHERE tablename='cross_tenant_doc_links' AND indexname='ix_ctdl_sales_out'")
    v142 = q1("SELECT success FROM flyway_schema_history WHERE version='142'")
    log("G10a-V142 迁移已执行", v142 is not None and v142[0] in ("t", "true", "True"),
        {"v142": v142[0] if v142 else "MISSING"})
    log("G10b-旧唯一索引 ux_ctdl_sales_out 已删除", ux and ux[0] == "0", {"count": ux[0] if ux else None})
    log("G10c-新非唯一索引 ix_ctdl_sales_out 已创建", ix and ix[0] == "1", {"count": ix[0] if ix else None})


# ================= 清理本脚本种子与单据 =================
def cleanup_seed():
    ids = STATE
    pos = ",".join(str(i) for i in ids.get("cleanup_pos", [])) or "0"
    sos = ",".join(str(i) for i in ids.get("cleanup_sos", [])) or "0"
    gis = ",".join(str(i) for i in ids.get("cleanup_gis", [])) or "0"
    rcs = ",".join(str(i) for i in ids.get("cleanup_rcs", [])) or "0"

    # 1) 库存流水：厂家发货扣减(SALES_OUT=GI id) + 经销商收货入库(RECEIPT=收货单 id)
    db_exec(f"DELETE FROM inventory_transactions "
            f"WHERE tenant_id='{MFR_A}' AND ref_doc_type='SALES_OUT' AND ref_doc_id IN ({gis});")
    db_exec(f"DELETE FROM inventory_transactions "
            f"WHERE tenant_id IN ('{A1}','{A2}') AND ref_doc_type='RECEIPT' AND ref_doc_id IN ({rcs});")

    # 2) 库存：快照法——三租户内 seed 后新增的库存行直接删除；快照内行恢复为快照数量
    snap = STATE.get("inv_snapshot", {})
    code, rows, _t = db(
        f"SELECT id, COALESCE(qty,0) FROM inventory WHERE tenant_id IN ('{MFR_A}','{A1}','{A2}')")
    new_ids = []
    for r in rows:
        inv_id, qty = r[0], r[1]
        if inv_id in snap:
            if str(qty) != str(snap[inv_id]):
                db_exec(f"UPDATE inventory SET qty={snap[inv_id]}, updated_at=now() WHERE id={inv_id}")
        else:
            new_ids.append(inv_id)
    if new_ids:
        db_exec(f"DELETE FROM inventory WHERE id IN ({','.join(new_ids)});")

    # 3) 单据与台账（顺序：台账/行 -> 单据）
    sql = f"""
    DELETE FROM cross_tenant_doc_links WHERE po_id IN ({pos}) OR sales_order_id IN ({sos})
       OR sales_out_id IN ({gis}) OR receipt_id IN ({rcs});
    DELETE FROM receipt_lines WHERE receipt_id IN ({rcs});
    DELETE FROM receipts WHERE id IN ({rcs});
    DELETE FROM purchase_order_lines WHERE po_id IN ({pos});
    DELETE FROM purchase_orders WHERE id IN ({pos});
    DELETE FROM sales_out_lines WHERE sales_out_id IN ({gis});
    DELETE FROM sales_outs WHERE id IN ({gis});
    DELETE FROM order_lines WHERE order_id IN ({sos});
    DELETE FROM orders WHERE id IN ({sos});
    """
    code, txt = db_exec(sql)

    # 4) 种子主数据
    db_exec(f"DELETE FROM stock_serials WHERE tenant_id='{MFR_A}' AND serial_no='{ids.get('ser_no','')}';")
    db_exec(f"DELETE FROM product_mappings WHERE remark='{TAG}';")
    db_exec(f"DELETE FROM products WHERE code IN ('{TAG}-MFR-SER','{TAG}-DLR-SER','{TAG}-NOMAP');")
    db_exec(f"DELETE FROM suppliers WHERE code='{TAG}-NORM';")
    db_exec(f"DELETE FROM authorizations WHERE remark='{TAG}';")
    if ids.get("mfr_dealer_a2_created"):
        db_exec(f"DELETE FROM dealers WHERE id={MFR_DEALER_A2} AND code='D-COLLAB-A2';")
    # A2 自动补建的主体/仓库（收货单已删，库存已清，方可删除）
    db_exec(f"DELETE FROM warehouses WHERE tenant_id='{A2}' AND code='COLLAB-DEFAULT-WH';")
    db_exec(f"DELETE FROM dealers WHERE tenant_id='{A2}' AND code='DEALER-SELF';")
    log("C9-清理本脚本种子与单据(流水/库存快照恢复/单据/种子主数据)", code == 0,
        {"code": code, "restoredInv": len(snap), "deletedNewInv": len(new_ids),
         "err": txt[:300] if code != 0 else "ok"})


def main():
    print(f"========== v4.5.0 跨租户协同回归  TAG={TAG} ==========")
    # 健康
    from v440_api_test import req as _req, BASE
    st, res = _req("GET", "/actuator/health", base=BASE)
    log("P0-后端健康检查", st == 200 and res.get("status") == "UP", {"status": st})

    tok_mfr = login("mfr_a_admin", "Dms@123456", "MFR_A")
    tok_a1 = login("dealer_a1_admin", "Dms@123456", "DEALER_A1")
    tok_a2 = login("dealer_a2_admin", "Sh123456", "DEALER_A2")
    log("P0-三租户登录(MFR_A/A1/A2)", bool(tok_mfr and tok_a1 and tok_a2),
        {"mfr": bool(tok_mfr), "a1": bool(tok_a1), "a2": bool(tok_a2)})

    cleanup_legacy()
    case_g10()           # 迁移断言（部署后即应通过）
    seed()

    try:
        case_g1(tok_a1)
        case_g2(tok_a1)
        case_path_a(tok_a1, tok_mfr)   # G3/G4/G8/G9
        case_g5(tok_a1, tok_mfr)       # G5 序列号
        case_g7(tok_mfr, tok_a2)       # G7 A2 路径B
        case_g6(tok_mfr, tok_a1)       # G6 功能开关（最后做，避免开关状态影响其他用例）
    finally:
        cleanup_seed()

    total = len(RESULTS)
    passed = sum(1 for r in RESULTS if r["ok"])
    print("\n========== 汇总 ==========")
    print(f"TAG={TAG}  通过 {passed}/{total}")
    fails = [r for r in RESULTS if not r["ok"]]
    for r in fails:
        print("  FAIL " + r["case"] + " :: " + json.dumps(r["detail"], ensure_ascii=False)[:200])
    print("==========================")
    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())
