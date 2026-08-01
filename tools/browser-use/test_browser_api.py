"""
纯浏览器自动化测试 - 不依赖 LLM，直接用 browser-use 的 Browser API
用于验证浏览器功能和基础页面操作
用法: python test_browser_api.py
"""
import asyncio
import time
import base64
from browser_use import Browser


async def test_browser():
    print("=" * 60)
    print("Browser API 基础测试")
    print("=" * 60)

    browser = Browser(
        headless=False,
        enable_default_extensions=False,
        window_size={"width": 1280, "height": 800},
    )

    try:
        print("\n[1/5] 启动浏览器...")
        await browser.start()
        print("    ✅ 浏览器启动成功")

        print("\n[2/5] 打开新页面...")
        page = await browser.new_page()
        print("    ✅ 新页面创建成功")

        print("\n[3/5] 访问 example.com...")
        await page.goto("https://example.com")
        await asyncio.sleep(3)
        title = await page.get_title()
        url = await page.get_url()
        print(f"    ✅ 页面加载成功")
        print(f"       标题: {title}")
        print(f"       URL: {url}")

        print("\n[4/5] 执行 JS 脚本...")
        h1_text = await page.evaluate("() => document.querySelector('h1').textContent")
        p_text = await page.evaluate("() => document.querySelector('p').textContent.substring(0, 50)")
        print(f"    ✅ JS 执行成功")
        print(f"       h1: {h1_text}")
        print(f"       p (前50字): {p_text}...")

        print("\n[5/5] 截图...")
        screenshot_b64 = await page.screenshot()
        screenshot_bytes = base64.b64decode(screenshot_b64)
        print(f"    ✅ 截图成功，大小: {len(screenshot_bytes)} 字节")
        with open("test_screenshot.png", "wb") as f:
            f.write(screenshot_bytes)
        print("       已保存到 test_screenshot.png")

        print("\n" + "=" * 60)
        print("所有测试通过! ✅")
        print("=" * 60)
        print("\n提示：browser-use 主要配合 LLM 使用 AI Agent 模式，")
        print("      纯 Browser API 功能相对基础，建议结合 Agent 使用。")

    except Exception as e:
        print(f"\n❌ 测试失败: {e}")
        import traceback
        traceback.print_exc()
    finally:
        await browser.close()
        print("\n浏览器已关闭")


if __name__ == "__main__":
    asyncio.run(test_browser())
