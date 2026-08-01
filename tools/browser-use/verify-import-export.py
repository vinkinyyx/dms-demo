#!/usr/bin/env python3
"""
验证导入导出功能 - 使用 browser-use
"""
import asyncio
import sys
import os
import time

# 添加项目根目录到路径
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', '..'))

from browser_use import Browser, Agent
from langchain_anthropic import ChatAnthropic

# 配置
TEST_URL = "http://8.133.193.238:8083"
TENANT = "default"
USERNAME = "admin"
PASSWORD = "Sh123456"

# 测试的模块列表
MODULES_TO_TEST = [
    {"name": "产品管理", "path": "/masterdata/products"},
    {"name": "产品分类", "path": "/masterdata/product-categories"},
    {"name": "经销商", "path": "/masterdata/dealers"},
    {"name": "医院/终端", "path": "/masterdata/hospitals"},
    {"name": "仓库管理", "path": "/masterdata/warehouses"},
    {"name": "供应商管理", "path": "/masterdata/suppliers"},
    {"name": "区域管理", "path": "/masterdata/regions"},
    {"name": "产品价格", "path": "/masterdata/product-prices"},
    {"name": "产品线管理", "path": "/masterdata/product-lines"},
    {"name": "产品包装层级", "path": "/masterdata/product-package-levels"},
    {"name": "产品组合", "path": "/masterdata/product-bundles"},
]

async def main():
    print("=" * 80)
    print("  DMS 功能验证 - 重点测试导入导出")
    print("=" * 80)
    
    # 初始化 LLM
    llm = ChatAnthropic(model="claude-3-5-sonnet-20241022", timeout=60, temperature=0)
    
    # 初始化浏览器
    browser = Browser()
    page = await browser.get_new_page()
    
    results = {
        "login": False,
        "dashboard": False,
        "modules": []
    }
    
    try:
        # === 1. 登录 ===
        print("\n" + "=" * 60)
        print("1. 登录测试")
        print("=" * 60)
        
        await page.goto(f"{TEST_URL}/login")
        await asyncio.sleep(3)
        
        # 输入登录信息 - 使用 evaluate 确保 Vue v-model 更新
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
        buttons = await page.query_selector_all('button')
        login_button = None
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
        
        # === 2. 仪表盘 ===
        print("\n" + "=" * 60)
        print("2. 仪表盘测试")
        print("=" * 60)
        
        await asyncio.sleep(2)
        page_content = await page.content()
        
        has_chart = "chart" in page_content.lower() or "echarts" in page_content.lower()
        has_card = len(await page.query_selector_all('.el-card')) > 0
        
        if has_chart or has_card:
            results["dashboard"] = True
            print("✅ 仪表盘正常")
        else:
            results["dashboard"] = False
            print("❌ 仪表盘可能有问题")
        
        # === 3. 逐个测试模块 ===
        print("\n" + "=" * 60)
        print("3. 模块测试 - 导入导出按钮检查")
        print("=" * 60)
        
        for module in MODULES_TO_TEST:
            print(f"\n  测试模块: {module['name']}")
            print(f"  路径: {module['path']}")
            
            try:
                # 导航到模块页面
                await page.goto(f"{TEST_URL}{module['path']}")
                await asyncio.sleep(4)
                
                # 获取页面内容
                page_content = await page.content()
                
                # 查找按钮
                buttons = await page.query_selector_all('button')
                import_button_found = False
                export_button_found = False
                
                import_button_texts = []
                export_button_texts = []
                
                for btn in buttons:
                    text = (await btn.text_content() or "").strip()
                    if "导入" in text:
                        import_button_found = True
                        import_button_texts.append(text)
                    if "导出" in text or "模板" in text or "下载" in text:
                        export_button_found = True
                        export_button_texts.append(text)
                
                # 检查表格
                has_table = len(await page.query_selector_all('table')) > 0 or len(await page.query_selector_all('.el-table')) > 0
                
                module_result = {
                    "name": module["name"],
                    "path": module["path"],
                    "has_import": import_button_found,
                    "has_export": export_button_found,
                    "has_table": has_table,
                    "import_buttons": import_button_texts,
                    "export_buttons": export_button_texts
                }
                
                results["modules"].append(module_result)
                
                # 打印结果
                status = "✅ 完整" if import_button_found and export_button_found else \
                        "⚠️ 部分" if (import_button_found or export_button_found) else "❌ 缺失"
                print(f"  状态: {status}")
                print(f"  - 导入按钮: {'✅' if import_button_found else '❌'} {import_button_texts}")
                print(f"  - 导出按钮: {'✅' if export_button_found else '❌'} {export_button_texts}")
                print(f"  - 数据表格: {'✅' if has_table else '❌'}")
                
            except Exception as e:
                print(f"  ❌ 测试失败: {e}")
                import traceback
                traceback.print_exc()
        
        # === 4. 移动端测试 ===
        print("\n" + "=" * 60)
        print("4. 移动端测试")
        print("=" * 60)
        
        try:
            await page.goto(f"{TEST_URL}/mobile/login")
            await asyncio.sleep(3)
            
            mobile_page_content = await page.content()
            has_mobile_ui = "mobile" in mobile_page_content.lower() or len(await page.query_selector_all('input')) >= 2
            
            print(f"  移动端登录页: {'✅ 正常' if has_mobile_ui else '❌ 异常'}")
            results["mobile_login"] = has_mobile_ui
            
        except Exception as e:
            print(f"  ❌ 移动端测试失败: {e}")
            results["mobile_login"] = False
        
        # === 5. 订单追溯 ===
        print("\n" + "=" * 60)
        print("5. 订单追溯测试")
        print("=" * 60)
        
        try:
            await page.goto(f"{TEST_URL}/report/order-trace")
            await asyncio.sleep(4)
            
            trace_page_content = await page.content()
            has_trace_table = len(await page.query_selector_all('table')) > 0 or len(await page.query_selector_all('.el-table')) > 0
            
            print(f"  订单追溯页面: {'✅ 正常' if has_trace_table else '❌ 异常'}")
            results["order_trace"] = has_trace_table
            
        except Exception as e:
            print(f"  ❌ 订单追溯测试失败: {e}")
            results["order_trace"] = False
        
        # === 6. 汇总结果 ===
        print("\n" + "=" * 80)
        print("  测试结果汇总")
        print("=" * 80)
        
        print(f"\n登录: {'✅ 通过' if results['login'] else '❌ 失败'}")
        print(f"仪表盘: {'✅ 通过' if results['dashboard'] else '❌ 失败'}")
        print(f"移动端: {'✅ 通过' if results.get('mobile_login') else '❌ 失败'}")
        print(f"订单追溯: {'✅ 通过' if results.get('order_trace') else '❌ 失败'}")
        
        print("\n模块导入导出功能:")
        complete_count = sum(1 for m in results["modules"] if m["has_import"] and m["has_export"])
        partial_count = sum(1 for m in results["modules"] if (m["has_import"] or m["has_export"]) and not (m["has_import"] and m["has_export"]))
        missing_count = len(results["modules"]) - complete_count - partial_count
        
        print(f"  ✅ 完整: {complete_count}")
        print(f"  ⚠️ 部分: {partial_count}")
        print(f"  ❌ 缺失: {missing_count}")
        
        for m in results["modules"]:
            status = "✅" if m["has_import"] and m["has_export"] else \
                    "⚠️" if (m["has_import"] or m["has_export"]) else "❌"
            print(f"  {status} {m['name']}")
        
        # 保存结果
        import json
        with open(os.path.join(os.path.dirname(__file__), "import-export-results.json"), "w", encoding="utf-8") as f:
            json.dump(results, f, ensure_ascii=False, indent=2)
        
        print("\n" + "=" * 80)
        print("  测试完成!")
        print("=" * 80)
        
    except Exception as e:
        print(f"\n❌ 测试过程出错: {e}")
        import traceback
        traceback.print_exc()
    finally:
        await browser.close()
    
    return results

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n用户中断")
        sys.exit(1)
    except Exception as e:
        print(f"\n测试失败: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
