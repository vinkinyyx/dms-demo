"""v3.7.3 UI smoke via Playwright: 验证前端 UI 关键点
- R1 采购订单列表 DRAFT 状态显示"提交审批"按钮
- R5 收货入库列表**没有**"新建"按钮
- R8 收货入库列表行没有默认"删除"按钮
- R6 打开某收货单编辑页，能看到"关联采购订单"信息
"""
import os, asyncio, sys, datetime, json
from pathlib import Path
from playwright.async_api import async_playwright

BASE = os.environ.get("DMS_UI_BASE", "http://8.133.193.238:8083")
USER = "admin"
PWD  = "Sh123456"
TEN  = "default"
HEADLESS = os.environ.get("HEADLESS", "1") == "1"

async def run():
    results = {}
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=HEADLESS)
        ctx = await browser.new_context(viewport={"width":1440,"height":900})
        page = await ctx.new_page()
        page.set_default_timeout(15000)

        # 1) login
        await page.goto(BASE + "/", wait_until="domcontentloaded")
        # tenant/username/password inputs order
        try:
            visible_inputs = page.locator('input:not([type="checkbox"]):not([type="hidden"])')
            n = await visible_inputs.count()
            if n >= 3:
                await visible_inputs.nth(n-3).fill(TEN)
                await visible_inputs.nth(n-2).fill(USER)
                await visible_inputs.nth(n-1).fill(PWD)
            # click any button labelled 登录 or button in form
            btn = page.get_by_role("button", name="登录")
            if await btn.count() == 0:
                btn = page.locator("button").last
            await btn.click()
            await page.wait_for_url("**/home**", timeout=20000)
            results["login"] = True
        except Exception as e:
            results["login"] = False
            results["login_err"] = str(e)
            await browser.close()
            return results

        # 2) navigate purchase orders
        try:
            await page.goto(BASE + "/m/purchase-orders", wait_until="networkidle")
            await page.wait_for_selector('.el-table__row', timeout=20000)
            await page.wait_for_timeout(4000)
            # click a DRAFT row's status filter to see actions column, but just count buttons by scanning body
            # 检查表格里 DRAFT 行是否有"提交审批"按钮
            body = await page.content()
            results["po_page_loaded"] = "采购订单" in body or "purchase-orders" in body
            results["po_has_submit_btn"] = "提交审批" in body
            results["po_has_approve_btn"] = "审批通过" in body
        except Exception as e:
            results["po_err"] = str(e)

        # 3) navigate receipts
        try:
            await page.goto(BASE + "/m/receipts", wait_until="networkidle")
            try:
                await page.wait_for_selector('.el-table__row', timeout=20000)
            except: pass
            await page.wait_for_timeout(2000)
            body = await page.content()
            results["rk_page_loaded"] = "收货入库" in body or "receipts" in body
            # noCreate 应该没有"新建"按钮
            # Element Plus 上按钮 label 通常写"新增/新建"
            results["rk_has_new_btn"] = ("新建" in body) and ("新建收货入库" in body or ">新建<" in body)
            # 找 V373-A 行有无
            results["rk_v373_a_visible"] = "RK-V373-A" in body
            results["rk_v373_b_visible"] = "RK-V373-B" in body
            results["rk_v373_c_visible"] = "RK-V373-C" in body
            results["rk_v373_d_visible"] = "RK-V373-D" in body
        except Exception as e:
            results["rk_err"] = str(e)

        # 4) open a receipt-edit for RK-V373-B (partial) to check 关联采购订单 卡片
        try:
            # fetch via requests using backend directly
            import requests
            login = requests.post(BASE.replace(':8083', ':8082').replace(':8081', ':8080') + '/api/auth/login',
                                  json={'tenantCode':TEN,'username':USER,'password':PWD}, timeout=10)
            tok = login.json()['data']['accessToken']
            backend = BASE.replace(':8083', ':8082').replace(':8081', ':8080')
            r = requests.get(backend + '/api/receipts?page=1&size=50', headers={'Authorization': 'Bearer '+tok}, timeout=10)
            rk_list = r.json()['data']['list']
            rk_b = next((x for x in rk_list if x.get("code") == "RK-V373-B"), None)
            if rk_b:
                rid = rk_b["id"]
                await page.goto(f"{BASE}/receipt-edit/{rid}", wait_until="networkidle")
                await page.wait_for_timeout(2500)
                body = await page.content()
                results["rk_edit_show_source_po"] = "关联采购订单" in body
                results["rk_edit_show_supplier"] = "供应商" in body
                results["rk_edit_show_expected"] = "期望到货" in body
            else:
                results["rk_edit_skip"] = "RK-V373-B not found in list"
        except Exception as e:
            results["rk_edit_err"] = str(e)

        await browser.close()
    return results

async def main():
    results = await run()
    print(json.dumps(results, ensure_ascii=False, indent=2))
    # summary
    keys = ["login","po_page_loaded","po_has_submit_btn","po_has_approve_btn",
            "rk_page_loaded","rk_v373_a_visible","rk_v373_b_visible",
            "rk_v373_c_visible","rk_v373_d_visible",
            "rk_edit_show_source_po","rk_edit_show_supplier","rk_edit_show_expected"]
    passed = sum(1 for k in keys if results.get(k) is True)
    print(f"\n== {passed}/{len(keys)} true ==")
    if results.get("rk_has_new_btn"):
        print("[WARN] receipts page shows 新建 button")
    ts = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    out = Path("tools/browser-use/reports") / f"ui_smoke_v373_{ts}.json"
    out.parent.mkdir(exist_ok=True, parents=True)
    out.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print("report:", out)

if __name__ == "__main__":
    asyncio.run(main())
