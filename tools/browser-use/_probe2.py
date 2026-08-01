import asyncio
from playwright.async_api import async_playwright
BASE = "http://8.133.193.238:8081"
async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        page = await browser.new_page()
        await page.goto(BASE + "/login", wait_until="networkidle")
        await page.wait_for_timeout(1500)
        # 使用 index 定位
        inputs = page.locator("input")
        n = await inputs.count()
        print("input count=", n)
        # 依次填三项
        vals = ["default","admin","Sh123456"]
        for i in range(3):
            await inputs.nth(i).fill(vals[i])
        # 点登录
        btns = page.locator("button")
        print("btn count=", await btns.count(), "text0=", await btns.nth(0).text_content())
        await btns.nth(0).click()
        await page.wait_for_timeout(3500)
        print("final url=", page.url)
        # 打印页面 title 与 h1
        print("title=", await page.title())
        top = await page.evaluate("() => document.body.innerText.slice(0,300)")
        print("top:", top)
        await browser.close()
asyncio.run(main())
