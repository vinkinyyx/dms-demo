"""
DMS 系统冒烟测试 - 使用 browser-use + LLM 自动执行
用法: python test_smoke.py
"""
import asyncio
import os
from dotenv import load_dotenv
from browser_use import Agent, Browser
from browser_use.agent.views import AgentHistoryList

load_dotenv()


async def run_smoke_test():
    base_url = os.getenv("DMS_TEST_URL", "http://8.133.193.238:8083")
    username = os.getenv("DMS_ADMIN_USER", "admin")
    password = os.getenv("DMS_ADMIN_PASSWORD", "Sh123456")
    tenant = os.getenv("DMS_TENANT", "default")

    browser = Browser(
        headless=False,
        enable_default_extensions=False,
        window_size={"width": 1920, "height": 1080},
    )

    task = f"""
    对 DMS 系统执行冒烟测试，步骤如下：

    1. 打开 {base_url}
    2. 验证登录页面正常显示
    3. 使用租户 "{tenant}"、账号 "{username}"、密码 "{password}" 登录
    4. 登录成功后，验证工作台页面正常加载
    5. 点击左侧菜单中的"产品管理"（如果有）
    6. 验证产品列表页面正常显示
    7. 点击"新增产品"按钮（如果有），验证弹窗能正常打开
    8. 返回列表页，验证操作日志功能可用
    9. 测试删除功能（有引用时应返回业务错误而非500）
    10. 退出登录，返回登录页

    注意事项：
    - 遇到弹窗先关闭
    - 页面加载慢时多等几秒
    - 每个操作后验证页面响应
    - 最后输出测试结果总结
    """

    agent = Agent(
        task=task,
        browser=browser,
        use_vision=True,
        max_actions_per_step=5,
        save_conversation_path="./smoke_test_result.json",
    )

    history: AgentHistoryList = await agent.run()

    print("\n" + "=" * 60)
    print("冒烟测试完成")
    print("=" * 60)
    print(f"总步骤数: {len(history.history)}")
    print(f"结果已保存到: smoke_test_result.json")

    await browser.close()


if __name__ == "__main__":
    asyncio.run(run_smoke_test())
