#!/usr/bin/env python3
"""
全面验证DMS系统 - 重点测试导入导出功能
使用browser-use和playwright
"""

import asyncio
import sys
from browser_use import Browser, Agent
from langchain_anthropic import ChatAnthropic
import os
import time

# 配置
TEST_URL = "http://8.133.193.238:8083"
TENANT = "default"
USERNAME = "admin"
PASSWORD = "Sh123456"

# 主数据模块列表 - 对应菜单
MODULES = [
    {"name": "产品管理", "menu": "产品管理", "path": "/masterdata/products"},
    {"name": "产品分类", "menu": "产品分类", "path": "/masterdata/product-categories"},
    {"name": "经销商", "menu": "经销商", "path": "/masterdata/dealers"},
    {"name": "医院", "menu": "医院", "path": "/masterdata/hospitals"},
    {"name": "仓库", "menu": "仓库", "path": "/masterdata/warehouses"},
    {"name": "供应商", "menu": "供应商", "path": "/masterdata/suppliers"},
    {"name": "区域", "menu": "区域", "path": "/masterdata/regions"},
    {"name": "产品价格", "menu": "产品价格", "path": "/masterdata/product-prices"},
]


async def test_full_system():
    """完整系统测试"""
    print("=" * 80)
    print("  DMS全面测试 - 重点验证导入导出功能")
    print("=" * 80)
    
    # 初始化LLM
    llm = ChatAnthropic(model="claude-3-5-sonnet-20241022", timeout=60, temperature=0)
    
    # 初始化浏览器
    browser = Browser()
    page = await browser.get_new_page()
    
    results = {}
    
    try:
        # 1. 登录测试
        print("\n[1/6] 测试登录...")
        await page.goto(f"{TEST_URL}/login")
        await asyncio.sleep(3)
        
        # 输入登录信息
        await page.evaluate(f"""
        () => {{
            const setInput = (placeholder, value) => {{
                const inputs = document.querySelectorAll('input');
                for (const inp of inputs) {{
                    if (inp.placeholder === placeholder) {{
                        inp.value = value;
                        inp.dispatchEvent(new Event('input', {{ bubbles: true }}));
                        break;
                    }}
                }}
            }}
            setInput('租户', '{TENANT}');
            setInput('用户名', '{USERNAME}');
            setInput('密码', '{PASSWORD}');
        }}
        """)
        
        await asyncio.sleep(1)
        
        # 点击登录按钮
        login_button = None
        buttons = await page.query_selector_all('button')
        for btn in buttons:
            text = await btn.text_content()
            if "登录" in text:
                login_button = btn
                break
        
        if login_button:
            await login_button.click()
            await asyncio.sleep(5)
        
        # 检查是否登录成功
        current_url = page.url
        if "/dashboard" in current_url or current_url == TEST_URL + "/" or current_url == TEST_URL:
            results["login"] = True
            print("✅ 登录成功")
        else:
            results["login"] = False
            print("❌ 登录失败")
            return results
        
        # 2. 测试主数据列表 - 检查导入导出按钮
        print("\n[2/6] 测试主数据列表页...")
        modules_with_buttons = []
        
        for module in MODULES:
            try:
                print(f"  测试模块: {module['name']}")
                
                # 尝试导航
                try:
                    await page.goto(f"{TEST_URL}{module['path']}")
                except Exception as e:
                    print(f"    导航失败: {e}")
                    continue
                
                await asyncio.sleep(3)
                
                # 检查页面是否加载
                page_content = await page.content()
                
                # 查找导入导出按钮
                has_import = "导入" in page_content or "batch-import" in page_content
                has_export = "导出" in page_content or "export" in page_content.lower()
                
                # 查找按钮元素
                import_buttons = []
                export_buttons = []
                
                buttons = await page.query_selector_all('button')
                for btn in buttons:
                    text = await btn.text_content() or ""
                    if "导入" in text:
                        import_buttons.append(text)
                    if "导出" in text or "下载" in text or "模板" in text:
                        export_buttons.append(text)
                
                # 检查页面中是否有相关文字
                has_import_btn = len(import_buttons) > 0
                has_export_btn = len(export_buttons) > 0
                
                print(f"    导入按钮: {'✅' if has_import_btn else '❌'} {import_buttons}")
                print(f"    导出/模板按钮: {'✅' if has_export_btn else '❌'} {export_buttons}")
                
                modules_with_buttons.append({
                    "module": module["name"],
                    "has_import": has_import_btn,
                    "has_export": has_export_btn,
                    "import_buttons": import_buttons,
                    "export_buttons": export_buttons
                })
                
                await asyncio.sleep(1)
                
            except Exception as e:
                print(f"    测试失败: {e}")
                continue
        
        results["modules"] = modules_with_buttons
        
        # 3. 测试订单模块
        print("\n[3/6] 测试订单模块...")
        try:
            await page.goto(f"{TEST_URL}/orders/orders")
            await asyncio.sleep(3)
            
            page_content = await page.content()
            has_data = len(await page.query_selector_all('table')) > 0 or len(await page.query_selector_all('.el-table')) > 0
            
            results["orders_page"] = has_data
            print(f"  订单列表: {'✅ 有数据/表格' if has_data else '❌ 无表格'}")
        except Exception as e:
            results["orders_page"] = False
            print(f"  订单模块测试失败: {e}")
        
        # 4. 测试库存模块
        print("\n[4/6] 测试库存模块...")
        try:
            await page.goto(f"{TEST_URL}/inventory/inventory")
            await asyncio.sleep(3)
            
            page_content = await page.content()
            has_data = len(await page.query_selector_all('table')) > 0 or len(await page.query_selector_all('.el-table')) > 0
            
            results["inventory_page"] = has_data
            print(f"  库存列表: {'✅ 有数据/表格' if has_data else '❌ 无表格'}")
        except Exception as e:
            results["inventory_page"] = False
            print(f"  库存模块测试失败: {e}")
        
        # 5. 测试移动端H5
        print("\n[5/6] 测试移动端H5...")
        try:
            await page.goto(f"{TEST_URL}/mobile/login")
            await asyncio.sleep(3)
            
            page_content = await page.content()
            has_login_form = "租户" in page_content or "用户名" in page_content or "密码" in page_content
            
            results["mobile_login"] = has_login_form
            print(f"  移动端登录页: {'✅ 正常' if has_login_form else '❌ 异常'}")
        except Exception as e:
            results["mobile_login"] = False
            print(f"  移动端测试失败: {e}")
        
        # 6. 测试仪表盘
        print("\n[6/6] 测试仪表盘...")
        try:
            await page.goto(f"{TEST_URL}/")
            await asyncio.sleep(3)
            
            page_content = await page.content()
            has_dashboard = len(await page.query_selector_all('.el-card')) > 0 or len(await page.query_selector_all('.dashboard')) > 0 or "图表" in page_content
            
            results["dashboard"] = has_dashboard
            print(f"  仪表盘: {'✅ 正常' if has_dashboard else '❌ 异常'}")
        except Exception as e:
            results["dashboard"] = False
            print(f"  仪表盘测试失败: {e}")
        
        # 汇总结果
        print("\n" + "=" * 80)
        print("  测试结果汇总")
        print("=" * 80)
        
        print(f"\n登录: {'✅ 通过' if results.get('login') else '❌ 失败'}")
        print(f"仪表盘: {'✅ 通过' if results.get('dashboard') else '❌ 失败'}")
        print(f"订单页: {'✅ 通过' if results.get('orders_page') else '❌ 失败'}")
        print(f"库存页: {'✅ 通过' if results.get('inventory_page') else '❌ 失败'}")
        print(f"移动端: {'✅ 通过' if results.get('mobile_login') else '❌ 失败'}")
        
        print("\n主数据模块导入导出功能检查:")
        for mod in results.get("modules", []):
            status = "✅ 完整" if mod["has_import"] and mod["has_export"] else \
                     "⚠️ 部分" if (mod["has_import"] or mod["has_export"]) else "❌ 缺失"
            print(f"  {mod['module']}: {status}")
            if mod["import_buttons"]:
                print(f"    导入按钮: {mod['import_buttons']}")
            if mod["export_buttons"]:
                print(f"    导出/模板按钮: {mod['export_buttons']}")
        
        # 保存结果
        import json
        with open('full_verify_results.json', 'w', encoding='utf-8') as f:
            json.dump(results, f, ensure_ascii=False, indent=2)
        
        print("\n✅ 完整测试完成，结果已保存到 full_verify_results.json")
        
    except Exception as e:
        print(f"\n❌ 测试过程出错: {e}")
        import traceback
        traceback.print_exc()
    finally:
        await browser.close()
    
    return results


if __name__ == "__main__":
    asyncio.run(test_full_system())
