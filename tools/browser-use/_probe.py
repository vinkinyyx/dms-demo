import asyncio
from playwright.async_api import async_playwright
BASE = "http://8.133.193.238:8081"
async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        page = await browser.new_page()
        await page.goto(BASE + "/login", wait_until="networkidle")
        await page.wait_for_timeout(1500)
        inputs = await page.evaluate("() => Array.from(document.querySelectorAll('input')).map(i => ({ph:i.placeholder||'', type:i.type, name:i.name||'', id:i.id||''}))")
        print("inputs:", inputs)
        btns = await page.evaluate("() => Array.from(document.querySelectorAll('button')).map(b => b.textContent.trim())")
        print("buttons:", btns)
        html = await page.content()
        with open("_login.html","w",encoding="utf-8") as f: f.write(html)
        print("saved html len=", len(html), "  final url=", page.url)
        await browser.close()
asyncio.run(main())
