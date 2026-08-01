
#!/usr/bin/env python3
"""
验证DMS部署结果 - 使用browser-automation
"""

import asyncio
import sys
import os
from browser_use import Agent
from browser_use.browser.context import BrowserContextConfig
from dotenv import load_dotenv
import json

# 加载环境变量
load_dotenv()

# 配置参数
BASE_URL = "http://8.133.193.238:8083"
ADMIN_URL = f"{BASE_URL}/admin"
USERNAME = "admin"
PASSWORD = "Sh123456"


async def verify_deployment():
    """验证部署结果"""
    print("=" * 80)
    print("开始验证DMS部署结果...")
    print("=" * 80)
    print(f"测试环境: {BASE_URL}")
    print(f"后台管理: {ADMIN_URL}")
    print(f"用户名: {USERNAME}")
    print("=" * 80)
    
    results = {}
    
    try:
        # 1. 验证首页访问
        print("\n[1/6] 验证首页访问...")
        agent_home = Agent(
            task=f"访问 {BASE_URL}，检查页面是否正常加载，截图保存为 home-page.png",
            browser_config=BrowserContextConfig(headless=False),
            use_vision=True
        )
        history_home = await agent_home.run()
        results['home'] = 'success' if history_home else 'failed'
        print(f"首页验证: {results['home']}")
        
        await asyncio.sleep(2)
        
        # 2. 验证登录功能
        print("\n[2/6] 验证登录功能...")
        agent_login = Agent(
            task=f"""
            访问 {BASE_URL}，找到登录表单，填写以下信息：
            - 租户: default
            - 用户名: {USERNAME}
            - 密码: {PASSWORD}
            
            点击登录按钮，检查是否登录成功，进入工作台页面。
            截图保存为 login-success.png
            """,
            browser_config=BrowserContextConfig(headless=False),
            use_vision=True
        )
        history_login = await agent_login.run()
        results['login'] = 'success' if history_login else 'failed'
        print(f"登录验证: {results['login']}")
        
        await asyncio.sleep(3)
        
        # 3. 验证产品管理模块
        print("\n[3/6] 验证产品管理模块...")
        agent_products = Agent(
            task=f"""
            在已登录的页面，找到产品管理菜单并点击进入。
            检查产品列表是否正常显示数据。
            截图保存为 products-list.png
            """,
            browser_config=BrowserContextConfig(headless=False),
            use_vision=True
        )
        history_products = await agent_products.run()
        results['products'] = 'success' if history_products else 'failed'
        print(f"产品管理验证: {results['products']}")
        
        await asyncio.sleep(2)
        
        # 4. 验证导入导出按钮
        print("\n[4/6] 验证导入导出按钮...")
        agent_import_export = Agent(
            task=f"""
            在产品管理页面，查找导入和导出按钮。
            检查这些按钮是否存在并可见。
            点击导出按钮，看是否能正常下载文件。
            截图保存为 import-export.png
            """,
            browser_config=BrowserContextConfig(headless=False),
            use_vision=True
        )
        history_import_export = await agent_import_export.run()
        results['import_export'] = 'success' if history_import_export else 'failed'
        print(f"导入导出验证: {results['import_export']}")
        
        await asyncio.sleep(2)
        
        # 5. 验证订单追溯报表
        print("\n[5/6] 验证订单追溯报表...")
        agent_order_trace = Agent(
            task=f"""
            找到订单追溯报表菜单并点击进入。
            检查报表页面是否正常显示数据。
            截图保存为 order-trace.png
            """,
            browser_config=BrowserContextConfig(headless=False),
            use_vision=True
        )
        history_order_trace = await agent_order_trace.run()
        results['order_trace'] = 'success' if history_order_trace else 'failed'
        print(f"订单追溯验证: {results['order_trace']}")
        
        await asyncio.sleep(2)
        
        # 6. 验证移动端登录
        print("\n[6/6] 验证移动端登录...")
        agent_mobile = Agent(
            task=f"""
            访问 {BASE_URL}/mobile/login
            填写以下信息：
            - 租户: default
            - 用户名: {USERNAME}
            - 密码: {PASSWORD}
            
            点击登录按钮，检查是否能正常登录。
            截图保存为 mobile-login.png
            """,
            browser_config=BrowserContextConfig(headless=False),
            use_vision=True
        )
        history_mobile = await agent_mobile.run()
        results['mobile'] = 'success' if history_mobile else 'failed'
        print(f"移动端验证: {results['mobile']}")
        
    except Exception as e:
        print(f"验证过程中出现错误: {e}")
        results['error'] = str(e)
    
    # 输出验证结果
    print("\n" + "=" * 80)
    print("验证结果汇总")
    print("=" * 80)
    for key, value in results.items():
        status = "✅ 成功" if value == "success" else "❌ 失败"
        print(f"{key}: {status}")
    
    total_tests = len([k for k in results.keys() if k != 'error'])
    passed_tests = len([k for k, v in results.items() if v == 'success'])
    print(f"\n总计: {total_tests} 项测试, 通过: {passed_tests} 项")
    
    # 保存结果到文件
    with open('deployment-verify-results.json', 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print("\n验证结果已保存到 deployment-verify-results.json")
    
    return results


if __name__ == "__main__":
    try:
        asyncio.run(verify_deployment())
    except KeyboardInterrupt:
        print("\n\n用户中断了验证过程")
        sys.exit(0)
    except Exception as e:
        print(f"\n\n验证过程中发生错误: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

