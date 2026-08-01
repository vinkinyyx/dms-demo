#!/usr/bin/env python3
"""
简单测试 - 验证导入导出按钮
"""
import asyncio
import sys
import os
import time
import json

# 添加项目根目录
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', '..'))

from playwright.async_api import async_playwright

# 配置
TEST_URL = "http://8.133.193.238:8083"
TENANT = "default"
USERNAME = "admin"
PASSWORD = "Sh123456"

MODULES = [
    {"name": "产品管理", "path": "/masterdata/products"},
    {"name": "产品分类", "path": "/masterdata/product-categories"},
    {"name": "经销商", "path": "/masterdata/dealers"},
    {"name": "医院/终端", "path": "/masterdata/hospitals"},
    {"name": "仓库管理", "path": "/masterdata/warehouses"},
    {"name": "供应商管理", "path": "/masterdata/suppliers"},
]

async def main():
    print("=" * 80)
    print("  DMS 简单测试")
    print("=" * 80)
    
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=False)
        page = await browser.new_page()
        
        results = {
            "login": False,
            "dashboard": False,
            "modules": []
        }
        
        try:
            # 登录
            print("\n[1] 登录...")
            await page.goto(f"{TEST_URL}/login")
            await asyncio.sleep(3)
            
            # 输入
            await page.evaluate(f"""
            () => {{
                const inputs = document.querySelectorAll('input');
                const setInput = (ph, val) => {{
                    for (const i of inputs) {{
                        if (i.placeholder === ph) {{
                            i.value = val;
                            i.dispatchEvent(new Event('input', {{bubbles:true}}));
                            break;
                        }}
                    }}
                }};
                setInput('租户', '{TENANT}');
                setInput('用户名', '{USERNAME}');
                setInput('密码', '{PASSWORD}');
            }}
            """)
            
            await asyncio.sleep(1)
            
            # 点击登录按钮
            buttons = await page.query_selector_all('button')
            login_btn = None
            for b in buttons:
                txt = await b.text_content()
                if txt and "登录" in txt:
                    login_btn = b
                    break
            
            if login_btn:
                await login_btn.click()
                await asyncio.sleep(5)
            
            current_url = page.url
            if "/dashboard" in current_url or current_url == TEST_URL + "/" or current_url == TEST_URL:
                results["login"] = True
                print("✅ 登录成功")
            else:
                results["login"] = False
                print(f"❌ 登录失败，URL: {current_url}")
                return results
            
            # 仪表盘
            print("\n[2] 仪表盘...")
            await asyncio.sleep(2)
            content = await page.content()
            has_chart = "chart" in content.lower() or "echarts" in content.lower()
            has_card = len(await page.query_selector_all('.el-card')) > 0
            results["dashboard"] = has_chart or has_card
            print(f"✅ 仪表盘: {'正常' if results['dashboard'] else '异常'}")
            
            # 测试模块
            print("\n[3] 模块测试...")
            for mod in MODULES:
                print(f"\n  测试: {mod['name']}")
                
                try:
                    await page.goto(f"{TEST_URL}{mod['path']}")
                    await asyncio.sleep(4)
                    
                    buttons = await page.query_selector_all('button')
                    import_found = False
                    export_found = False
                    import_texts = []
                    export_texts = []
                    
                    for b in buttons:
                        txt = (await b.text_content() or "").strip()
                        if "导入" in txt:
                            import_found = True
                            import_texts.append(txt)
                        if "导出" in txt or "模板" in txt or "下载" in txt:
                            export_found = True
                            export_texts.append(txt)
                    
                    has_table = len(await page.query_selector_all('table')) > 0 or len(await page.query_selector_all('.el-table')) > 0
                    
                    mod_result = {
                        "name": mod["name"],
                        "has_import": import_found,
                        "has_export": export_found,
                        "has_table": has_table,
                        "import_buttons": import_texts,
                        "export_buttons": export_texts
                    }
                    results["modules"].append(mod_result)
                    
                    status = "✅ 完整" if import_found and export_found else \
                            "⚠️ 部分" if (import_found or export_found) else "❌ 缺失"
                    print(f"  状态: {status}")
                    print(f"  - 导入: {'✅' if import_found else '❌'} {import_texts}")
                    print(f"  - 导出: {'✅' if export_found else '❌'} {export_texts}")
                    print(f"  - 表格: {'✅' if has_table else '❌'}")
                    
                except Exception as e:
                    print(f"  ❌ 测试失败: {e}")
            
            # 总结
            print("\n" + "=" * 80)
            print("  结果汇总")
            print("=" * 80)
            
            print(f"\n登录: {'✅' if results['login'] else '❌'}")
            print(f"仪表盘: {'✅' if results['dashboard'] else '❌'}")
            
            print("\n模块:")
            complete = sum(1 for m in results['modules'] if m['has_import'] and m['has_export'])
            partial = sum(1 for m in results['modules'] if (m['has_import'] or m['has_export']) and not (m['has_import'] and m['has_export']))
            missing = len(results['modules']) - complete - partial
            print(f"✅ 完整: {complete}")
            print(f"⚠️ 部分: {partial}")
            print(f"❌ 缺失: {missing}")
            
            for m in results['modules']:
                st = "✅" if m['has_import'] and m['has_export'] else \
                     "⚠️" if (m['has_import'] or m['has_export']) else "❌"
                print(f"{st} {m['name']}")
            
            # 保存
            output_file = os.path.join(os.path.dirname(__file__), "simple-results.json")
            with open(output_file, "w", encoding="utf-8") as f:
                json.dump(results, f, ensure_ascii=False, indent=2)
            
            print("\n✅ 测试完成，结果已保存")
            
        except Exception as e:
            print(f"\n❌ 测试出错: {e}")
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
    except Exception as e:
        print(f"\n测试失败: {e}")
        import traceback
        traceback.print_exc()
