"""DMS 生产 v3.6.2 UI 巡检（Playwright，无 LLM）— 主 Agent 2026-07-26"""
import asyncio, os, json, sys, time
from datetime import datetime
from playwright.async_api import async_playwright

BASE = os.environ.get("DMS_BASE_URL", "http://8.133.193.238:8081")
TENANT = os.environ.get("DMS_TENANT", "default")
USER = os.environ.get("DMS_ADMIN_USER", "admin")
PWD = os.environ.get("DMS_ADMIN_PASSWORD", "Sh123456")

# 前端 modules.js 里的 32 个模块 key（PC 端）
PC_MODULES = [
    ("products","产品管理"),("categories","产品分类"),("product-lines","产品线"),
    ("product-package-levels","产品包装层级"),("product-bundles","产品组合"),
    ("dealers","经销商"),("hospitals","医院/终端"),("warehouses","仓库"),
    ("suppliers","供应商"),("product-prices","产品价格"),("regions","区域"),
    ("orders","销售订单"),("purchase-orders","采购订单"),("purchase-returns","采购退货"),
    ("sales-outs","销售出库"),("sales-returns","销售退货"),
    ("receipts","收货入库"),("inventory","库存"),("stock-moves","库存移动"),
    ("inventory-adjustments","库存调整"),("authorizations","授权"),
    ("contracts","合同"),("contract-apps","合同申请"),
    ("surgery-reports","手术报台"),("promotions","促销"),("positions","销售岗位"),
    ("users","用户"),("roles","角色"),("materials","物料"),
    ("report-sales-ranking","销售排行"),("report-product-top10","产品TOP10"),
    ("report-inventory-turnover","库存周转"),("report-surgery-stats","手术统计"),
    ("report-receivables","应收账款"),("report-order-trace","订单追溯"),
]

MOBILE_PAGES = ["/mobile/login","/mobile/dashboard","/mobile/orders","/mobile/inventory","/mobile/surgery","/mobile/scan","/mobile/profile"]

TS = datetime.now().strftime("%Y%m%d_%H%M%S")
os.makedirs("tools/browser-use/reports/screenshots", exist_ok=True)

async def login(page):
    await page.goto(BASE + "/login", wait_until="networkidle")
    await page.wait_for_timeout(1500)
    inputs = page.locator("input")
    vals = [TENANT, USER, PWD]
    for i in range(3):
        await inputs.nth(i).fill(vals[i])
    await page.locator("button").first.click()
    await page.wait_for_timeout(3500)
    return "login" not in page.url

async def check_module(page, key, name):
    url = f"{BASE}/m/{key}"
    entry = {"key":key,"name":name,"url":url,"status":"unknown","errors":[]}
    resp_log = []
    def on_resp(r):
        try:
            if "/api/" in r.url:
                resp_log.append({"url": r.url, "status": r.status})
        except: pass
    page.on("response", on_resp)
    try:
        await page.goto(url, wait_until="networkidle", timeout=15000)
        await page.wait_for_timeout(1500)
        page_text = (await page.content()).lower()
        # 判定
        has_table = len(await page.query_selector_all("table, .el-table")) > 0
        has_empty = ("暂无数据" in await page.content()) or ("no data" in page_text)
        has_error = ("系统内部错误" in await page.content()) or ("500" in page_text and "system" in page_text)
        api5xx = [r for r in resp_log if r["status"] >= 500]
        api4xx = [r for r in resp_log if 400 <= r["status"] < 500]
        entry["api_calls"] = resp_log
        entry["api_5xx"] = api5xx
        entry["api_4xx"] = api4xx
        if api5xx:
            entry["status"] = "bug500"
            entry["errors"].append(f"{len(api5xx)}个后端 500")
        elif api4xx and not has_table:
            entry["status"] = "auth_or_missing"
            entry["errors"].append(f"{len(api4xx)}个 4xx")
        elif has_table:
            entry["status"] = "ok"
        elif has_empty:
            entry["status"] = "empty_data"
        else:
            entry["status"] = "no_table"
        # 截图
        shot = f"tools/browser-use/reports/screenshots/{TS}_pc_{key}.png"
        await page.screenshot(path=shot, full_page=False)
        entry["screenshot"] = shot
    except Exception as e:
        entry["status"] = "exception"; entry["errors"].append(str(e))
    finally:
        page.remove_listener("response", on_resp)
    return entry

async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        ctx = await browser.new_context(viewport={"width":1440,"height":900}, locale="zh-CN")
        page = await ctx.new_page()
        print(f"[login] {BASE} ...")
        ok = await login(page)
        print(f"[login] {'ok' if ok else 'FAIL'} url={page.url}")
        if not ok:
            await browser.close(); sys.exit(1)
        results = []
        for key,name in PC_MODULES:
            r = await check_module(page, key, name)
            print(f"  [{r['status']:<10}] {name:<12} {url_summary(r)}")
            results.append(r)

        # 移动端
        mobile_ctx = await browser.new_context(viewport={"width":375,"height":812}, locale="zh-CN",
                                               user_agent="Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15")
        mpage = await mobile_ctx.new_page()
        mobile_results = []
        for path in MOBILE_PAGES:
            entry = {"path":path,"status":"unknown","errors":[]}
            resp_log = []
            def on_resp(r):
                try:
                    if "/api/" in r.url: resp_log.append({"url":r.url,"status":r.status})
                except: pass
            mpage.on("response", on_resp)
            try:
                await mpage.goto(BASE + path, wait_until="networkidle", timeout=15000)
                await mpage.wait_for_timeout(1500)
                content = await mpage.content()
                api5xx = [r for r in resp_log if r["status"] >= 500]
                if api5xx:
                    entry["status"] = "bug500"
                    entry["errors"].append(f"{len(api5xx)}个后端 500")
                elif "登录" in content and path.endswith("/login"):
                    entry["status"] = "ok_login_shown"
                elif len(content) > 500:
                    entry["status"] = "ok"
                else:
                    entry["status"] = "blank"
                shot = f"tools/browser-use/reports/screenshots/{TS}_mobile_{path.replace('/','_')}.png"
                await mpage.screenshot(path=shot, full_page=True)
                entry["screenshot"] = shot
                entry["api_5xx"] = api5xx
            except Exception as e:
                entry["status"] = "exception"; entry["errors"].append(str(e))
            finally:
                mpage.remove_listener("response", on_resp)
            print(f"  [mobile {entry['status']:<10}] {path}")
            mobile_results.append(entry)

        summary = {}
        for r in results:
            summary[r["status"]] = summary.get(r["status"],0)+1
        m_summary = {}
        for r in mobile_results:
            m_summary[r["status"]] = m_summary.get(r["status"],0)+1
        print("\n=== PC 汇总 ===", summary)
        print("=== 移动端汇总 ===", m_summary)

        out = f"tools/browser-use/reports/ui_smoke_{TS}.json"
        with open(out,"w",encoding="utf-8") as f:
            json.dump({"base":BASE,"timestamp":TS,"pc_summary":summary,"mobile_summary":m_summary,
                       "pc":results,"mobile":mobile_results}, f, ensure_ascii=False, indent=2)
        print(f"[saved] {out}")
        await browser.close()

def url_summary(r):
    n5 = len(r.get("api_5xx",[])); n4 = len(r.get("api_4xx",[]))
    return f"5xx={n5} 4xx={n4}"

if __name__ == "__main__":
    asyncio.run(main())


