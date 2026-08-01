"""
DMS Bug 修复后浏览器回归验证 v3 - 正确触发 Vue v-model
通过 dispatch events 并点击登录按钮
"""
import asyncio
import base64
from browser_use import Browser


async def save_screenshot(page, path):
    img_b64 = await page.screenshot()
    img_bytes = base64.b64decode(img_b64)
    with open(path, "wb") as f:
        f.write(img_bytes)
    return len(img_bytes)


async def do_login(page, prefix="/", tenant="default", username="admin", password="Sh123456"):
    """登录流程：goto → 等待表单 → 输入 → 点击登录按钮"""
    await page.goto(f"http://8.133.193.238:8083{prefix}login")
    await asyncio.sleep(3)

    # 触发原生的 input 事件，让 Vue v-model 监听到变更
    await page.evaluate(f"""
() => {{
    const setInput = (placeholder, value) => {{
        const inputs = document.querySelectorAll('input');
        for (const inp of inputs) {{
            if (inp.placeholder === placeholder) {{
                const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                nativeSetter.call(inp, value);
                inp.dispatchEvent(new Event('input', {{ bubbles: true }}));
                inp.dispatchEvent(new Event('change', {{ bubbles: true }}));
                inp.dispatchEvent(new Event('blur', {{ bubbles: true }}));
                return true;
            }}
        }}
        return false;
    }};
    setInput('租户代码', '{tenant}');
    setInput('账号', '{username}');
    setInput('密码', '{password}');
}}
""")
    await asyncio.sleep(1)

    # 点击登录按钮
    await page.evaluate("""
() => {
    const btns = document.querySelectorAll('button');
    for (const b of btns) {
        if (b.textContent.includes('登 录') || b.textContent.includes('登录')) {
            b.click();
            return true;
        }
    }
    return false;
}
""")
    await asyncio.sleep(5)


async def run():
    browser = Browser(
        headless=True,
        enable_default_extensions=False,
        window_size={"width": 1280, "height": 800},
    )

    try:
        await browser.start()
        page = await browser.new_page()

        print("=== Bug-001: PC 端列表回归验证 ===")
        await do_login(page)

        # 验证登录成功
        url = await page.get_url()
        print(f"登录后 URL: {url}")

        # 产品管理
        await page.goto("http://8.133.193.238:8083/m/products")
        await asyncio.sleep(6)
        products_text = await page.evaluate("() => document.body.innerText")
        products_count = products_text.count("PROD-")
        await save_screenshot(page, "verify-bug001-products.png")
        print(f"产品管理列表: 显示 {products_count} 条 PROD- 数据")

        # 销售订单
        await page.goto("http://8.133.193.238:8083/m/orders")
        await asyncio.sleep(6)
        orders_text = await page.evaluate("() => document.body.innerText")
        orders_count = orders_text.count("SO-") + orders_text.count("RSO-") + orders_text.count("TEST-")
        await save_screenshot(page, "verify-bug001-orders.png")
        print(f"销售订单列表: 显示 {orders_count} 条订单号数据")

        # 经销商
        await page.goto("http://8.133.193.238:8083/m/dealers")
        await asyncio.sleep(6)
        dealers_text = await page.evaluate("() => document.body.innerText")
        await save_screenshot(page, "verify-bug001-dealers.png")
        print(f"经销商列表预览: {dealers_text[:80].strip().replace(chr(10), ' / ')}")

        # ===== Bug-002: 移动端仪表盘 =====
        print("\n=== Bug-002: 移动端仪表盘回归验证 ===")
        await do_login(page, prefix="/mobile/")

        await page.goto("http://8.133.193.238:8083/mobile/home")
        await asyncio.sleep(4)
        home_text = await page.evaluate("() => document.body.innerText")
        await save_screenshot(page, "verify-bug002-mobile-home.png")
        print(f"移动端首页预览: {home_text[:160].strip().replace(chr(10), ' / ')}")

        await page.goto("http://8.133.193.238:8083/mobile/dashboard")
        await asyncio.sleep(6)
        dash_text = await page.evaluate("() => document.body.innerText")
        has_error = "系统内部错误" in dash_text
        has_chart = "销量" in dash_text or "订单" in dash_text or "金额" in dash_text or "销售" in dash_text
        await save_screenshot(page, "verify-bug002-mobile-dashboard.png")
        print(f"移动端仪表盘: 有错误={has_error}, 图表关键字={has_chart}")
        print(f"仪表盘预览: {dash_text[:300].strip().replace(chr(10), ' / ')}")

        # ===== Bug-004: 订单追溯 =====
        print("\n=== Bug-004: 订单追溯前端验证 ===")
        await page.goto("http://8.133.193.238:8083/m/report-order-trace")
        await asyncio.sleep(6)
        trace_text = await page.evaluate("() => document.body.innerText")
        await save_screenshot(page, "verify-bug004-order-trace.png")
        print(f"订单追溯报表预览: {trace_text[:400].strip().replace(chr(10), ' / ')}")

        # ===== 汇总 =====
        print("\n" + "=" * 60)
        print("Bug 修复回归结论")
        print("=" * 60)
        print(f"Bug-001 (PC端产品): {'PASS' if products_count > 0 else 'FAIL'} - {products_count}条")
        print(f"Bug-001 (PC端订单): {'PASS' if orders_count > 0 else 'FAIL'} - {orders_count}条")
        print(f"Bug-002 (移动端仪表盘): {'PASS' if not has_error else 'FAIL'}")
        print(f"Bug-004 (订单追溯): {'PASS' if 'TEST-' in trace_text or 'SO-' in trace_text else 'FAIL'}")

    except Exception as e:
        print(f"\nERROR: {e}")
        import traceback
        traceback.print_exc()
    finally:
        try:
            await browser.close()
        except Exception:
            pass


if __name__ == "__main__":
    asyncio.run(run())
