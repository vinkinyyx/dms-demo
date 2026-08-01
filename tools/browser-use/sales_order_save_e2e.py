import asyncio
from pathlib import Path
from playwright.async_api import async_playwright

JS = """
async () => {
  const token = localStorage.getItem('dms_access_token')
  const payload = {
    orderType: 'NORMAL',
    dealerId: 1,
    warehouseId: 1,
    expectedDate: '2026-08-15',
    remark: 'browser-e2e-save',
    lines: [{ productId: 1, qty: 2, unitPrice: 10, taxRate: 0.13 }]
  }
  const resp = await fetch('/api/sales-orders', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
    body: JSON.stringify(payload)
  })
  const text = await resp.text()
  let data = null
  try { data = text ? JSON.parse(text) : null } catch (e) {}
  return { status: resp.status, ok: resp.ok, text: text.slice(0, 500), code: data && data.code, id: data && data.data && data.data.id, message: data && data.message }
}
"""

async def main():
    outdir = Path('tools/browser-use/reports')
    outdir.mkdir(parents=True, exist_ok=True)
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        page = await browser.new_page(viewport={'width': 1600, 'height': 1100})
        await page.goto('http://localhost:5173/login', wait_until='networkidle')
        await page.locator('.login-form input').nth(0).fill('default')
        await page.locator('.login-form input').nth(1).fill('admin')
        await page.locator('.login-form input').nth(2).fill('Sh123456')
        await page.locator('.btn-login').click()
        await page.wait_for_url('**/home', timeout=15000)
        await page.goto('http://localhost:5173/m/orders', wait_until='networkidle')
        result = await page.evaluate(JS)
        await page.screenshot(path=str(outdir/'sales_order_browser_save.png'), full_page=True)
        print(result)
        assert result['status'] == 200 and result['code'] == 0 and result['id']
        print('BROWSER_SAVE_E2E_OK')
        await browser.close()

asyncio.run(main())