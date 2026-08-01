
#!/usr/bin/env python3
"""
验证DMS API - 使用Python requests库
"""

import requests
import json
import time
from datetime import datetime

BASE_URL = "http://8.133.193.238:8083"
API_BASE = "http://8.133.193.238:8082"

# 登录凭据
LOGIN_DATA = {
    "tenantCode": "default",
    "username": "admin",
    "password": "Sh123456"
}


def print_header(title):
    """打印标题"""
    print("\n" + "=" * 80)
    print(f"  {title}")
    print("=" * 80)


def print_result(name, status, details=""):
    """打印结果"""
    icon = "✅" if status else "❌"
    status_text = "通过" if status else "失败"
    print(f"{icon} {name}: {status_text}")
    if details:
        print(f"   {details}")


def verify_home_page():
    """验证首页"""
    print_header("1. 验证首页访问")
    try:
        response = requests.get(f"{BASE_URL}/", timeout=30)
        if response.status_code == 200:
            print_result("首页访问", True, f"状态码: {response.status_code}")
            return True
        else:
            print_result("首页访问", False, f"状态码: {response.status_code}")
            return False
    except Exception as e:
        print_result("首页访问", False, f"错误: {str(e)}")
        return False


def verify_login():
    """验证登录"""
    print_header("2. 验证登录功能")
    try:
        response = requests.post(
            f"{API_BASE}/api/auth/login",
            json=LOGIN_DATA,
            timeout=30
        )
        if response.status_code == 200:
            result = response.json()
            if result.get('code') == 0:
                token = result.get('data', {}).get('accessToken')
                print_result("登录功能", True, f"成功获取Token")
                return token
            else:
                print_result("登录功能", False, f"响应: {result}")
                return None
        else:
            print_result("登录功能", False, f"状态码: {response.status_code}")
            return None
    except Exception as e:
        print_result("登录功能", False, f"错误: {str(e)}")
        return None


def verify_products_list(token):
    """验证产品列表"""
    print_header("3. 验证产品列表API")
    try:
        headers = {"Authorization": f"Bearer {token}"}
        response = requests.get(
            f"{API_BASE}/api/products",
            headers=headers,
            params={"page": 1, "size": 10},
            timeout=30
        )
        if response.status_code == 200:
            result = response.json()
            if result.get('code') == 0:
                data = result.get('data', {})
                count = len(data.get('list', [])) if data else 0
                print_result("产品列表", True, f"获取到 {count} 条记录")
                return True
            else:
                print_result("产品列表", False, f"响应: {result}")
                return False
        else:
            print_result("产品列表", False, f"状态码: {response.status_code}")
            return False
    except Exception as e:
        print_result("产品列表", False, f"错误: {str(e)}")
        return False


def verify_export_template(token):
    """验证导出模板API"""
    print_header("4. 验证导出模板API")
    try:
        headers = {"Authorization": f"Bearer {token}"}
        response = requests.get(
            f"{API_BASE}/api/products/actions/export/template",
            headers=headers,
            timeout=30
        )
        if response.status_code == 200:
            content_type = response.headers.get('Content-Type', '')
            print_result("产品导出模板", True, f"Content-Type: {content_type}")
            return True
        else:
            print_result("产品导出模板", False, f"状态码: {response.status_code}")
            return False
    except Exception as e:
        print_result("产品导出模板", False, f"错误: {str(e)}")
        return False


def verify_order_trace(token):
    """验证订单追溯API"""
    print_header("5. 验证订单追溯API")
    try:
        headers = {"Authorization": f"Bearer {token}"}
        response = requests.get(
            f"{API_BASE}/api/reports/order-trace",
            headers=headers,
            params={"limit": 10},
            timeout=30
        )
        if response.status_code == 200:
            result = response.json()
            if result.get('code') == 0:
                data = result.get('data', [])
                count = len(data) if data else 0
                print_result("订单追溯", True, f"获取到 {count} 条记录")
                return True
            else:
                print_result("订单追溯", False, f"响应: {result}")
                return False
        else:
            print_result("订单追溯", False, f"状态码: {response.status_code}")
            return False
    except Exception as e:
        print_result("订单追溯", False, f"错误: {str(e)}")
        return False


def verify_mobile_login():
    """验证移动端登录页面"""
    print_header("6. 验证移动端登录页面")
    try:
        response = requests.get(f"{BASE_URL}/mobile/login", timeout=30)
        if response.status_code == 200:
            print_result("移动端登录", True, f"状态码: {response.status_code}")
            return True
        else:
            print_result("移动端登录", False, f"状态码: {response.status_code}")
            return False
    except Exception as e:
        print_result("移动端登录", False, f"错误: {str(e)}")
        return False


def main():
    """主函数"""
    print_header("DMS部署验证 - API测试")
    print(f"测试开始时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"前端地址: {BASE_URL}")
    print(f"后端地址: {API_BASE}")

    results = {
        "home": False,
        "login": False,
        "products": False,
        "export_template": False,
        "order_trace": False,
        "mobile": False
    }

    # 1. 首页验证
    results["home"] = verify_home_page()
    time.sleep(1)

    # 2. 登录验证
    token = verify_login()
    if token:
        results["login"] = True
        time.sleep(1)

        # 3. 产品列表验证
        results["products"] = verify_products_list(token)
        time.sleep(1)

        # 4. 导出模板验证
        results["export_template"] = verify_export_template(token)
        time.sleep(1)

        # 5. 订单追溯验证
        results["order_trace"] = verify_order_trace(token)
        time.sleep(1)
    else:
        results["login"] = False

    # 6. 移动端验证
    results["mobile"] = verify_mobile_login()

    # 汇总结果
    print_header("验证结果汇总")
    total = len(results)
    passed = sum(1 for v in results.values() if v)
    failed = total - passed

    print(f"\n总计: {total} 项测试")
    print(f"✅ 通过: {passed} 项")
    print(f"❌ 失败: {failed} 项")

    print("\n详细结果:")
    for key, value in results.items():
        status = "✅ 通过" if value else "❌ 失败"
        print(f"  {key}: {status}")

    # 保存结果
    with open('api-verify-results.json', 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print("\n验证结果已保存到 api-verify-results.json")

    print_header("验证完成")
    print(f"结束时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")

    return results


if __name__ == "__main__":
    main()

