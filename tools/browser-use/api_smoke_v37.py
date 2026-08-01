"""
DMS v3.7 全量 API 冒烟 - 生产 v3.6.2 环境
主 Agent 于 2026-07-26 生成。默认跑生产（唯一在线环境）。
每个 API 探测：状态码 + 是否 200/404/500 + 内容摘要，最终产出报告。
"""
import os, sys, json, time, io
from datetime import datetime
try:
    import requests
except ImportError:
    print("[ERR] pip install requests", file=sys.stderr); sys.exit(2)

BASE = os.environ.get("DMS_BASE_URL", "http://8.133.193.238:8081")
BACKEND = os.environ.get("DMS_BACKEND_URL", "http://8.133.193.238:8080")
TENANT = os.environ.get("DMS_TENANT", "default")
USER = os.environ.get("DMS_ADMIN_USER", "admin")
PWD = os.environ.get("DMS_ADMIN_PASSWORD", "Sh123456")

TS = datetime.now().strftime("%Y%m%d_%H%M%S")

# 47 项接口来自 docs/09_测试报告/browser-use自动化测试报告_v3.7.1_20260725.md 的对齐表 + 补充 v3.7 新增 14 项
APIS = [
    # (module, method, path, params, priority, story)
    ("产品管理","GET","/api/products",{"page":1,"size":5},"","US-A01"),
    ("产品分类","GET","/api/product-categories",{"page":1,"size":5},"","US-A01"),
    ("产品线","GET","/api/product-lines",{"page":1,"size":5},"","US-A01"),
    ("产品包装层级","GET","/api/product-package-levels",{"page":1,"size":5},"","US-A01"),
    ("产品组合","GET","/api/product-bundles",{"page":1,"size":5},"","US-B04"),
    ("经销商","GET","/api/dealers",{"page":1,"size":5},"","US-A04"),
    ("医院/终端","GET","/api/hospitals",{"page":1,"size":5},"","US-A02"),
    ("仓库","GET","/api/warehouses",{"page":1,"size":5},"","-"),
    ("供应商","GET","/api/suppliers",{"page":1,"size":5},"","-"),
    ("产品价格","GET","/api/product-prices",{"page":1,"size":5},"","US-B01"),
    ("区域","GET","/api/regions",{"page":1,"size":5},"","US-A03"),
    ("销售订单","GET","/api/orders",{"page":1,"size":5},"","US-B01"),
    ("采购订单","GET","/api/purchase-orders",{"page":1,"size":5},"","-"),
    ("收货入库","GET","/api/receipts",{"page":1,"size":5},"","-"),
    ("销售出库","GET","/api/sales-outs",{"page":1,"size":5},"","-"),
    ("库存查询","GET","/api/inventory",{"page":1,"size":5},"","-"),
    ("库存移动","GET","/api/stock-moves",{"page":1,"size":5},"","-"),
    ("库存调整","GET","/api/inventory-adjustments",{"page":1,"size":5},"","-"),
    ("授权管理","GET","/api/authorizations",{"page":1,"size":5},"","US-A04"),
    ("合同","GET","/api/contracts",{"page":1,"size":5},"","-"),
    ("手术报台","GET","/api/surgery-reports",{"page":1,"size":5},"","US-D01"),
    ("促销规则","GET","/api/promotions",{"page":1,"size":5},"","-"),
    ("销售岗位","GET","/api/sales-positions",{"page":1,"size":5},"","-"),
    ("数据看板","GET","/api/dashboard/overview",{},"","-"),
    ("销售排行","GET","/api/reports/sales-ranking",{},"","-"),
    ("产品TOP10","GET","/api/reports/product-top10",{},"","-"),
    ("库存周转","GET","/api/reports/inventory-turnover",{},"","-"),
    ("手术统计","GET","/api/reports/surgery-stats",{},"","-"),
    ("应收账款","GET","/api/reports/receivables",{},"","-"),
    ("操作日志","GET","/api/operation-log/list/product/1",{},"","-"),
    ("数据字典","GET","/api/dicts/types",{},"","-"),

    # v3.7.1 报告标记的 3 个 500 Bug
    ("合同申请-Bug","GET","/api/contract-applications",{"page":1,"size":5},"","-"),
    ("订单追溯报表-Bug","GET","/api/reports/order-trace",{"page":1,"size":5},"","-"),
    ("菜单配置-Bug","GET","/api/menu-configs",{},"","-"),

    # v3.7 新增 14 项，测试报告标记未实现
    ("质量投诉","GET","/api/quality-complaints",{"page":1,"size":5},"P1","US-C04"),
    ("CAPA","GET","/api/capa",{"page":1,"size":5},"P1","US-C04"),
    ("返利池","GET","/api/rebate-pools",{"page":1,"size":5},"P1","US-B02"),
    ("特殊价审批","GET","/api/special-prices",{"page":1,"size":5},"P1","US-B03"),
    ("维修订单","GET","/api/repair-orders",{"page":1,"size":5},"P1","US-B05"),
    ("组套定义","GET","/api/product-bundle-definitions",{"page":1,"size":5},"P1","US-B04"),
    ("区域月度快照","GET","/api/region-snapshots",{"page":1,"size":5},"P2","US-A03"),
    ("医院产品线关系","GET","/api/hospital-product-lines",{"page":1,"size":5},"P2","US-A02"),
    ("集成监控","GET","/api/integration/monitor",{},"P0","US-D02"),
    ("预警中心","GET","/api/alerts",{"page":1,"size":5},"P2","US-D03"),
    ("系统使用报表","GET","/api/reports/system-usage",{},"P2","US-D03"),
    ("常购模板","GET","/api/order-templates",{"page":1,"size":5},"P2","US-B06"),
    ("发票管理","GET","/api/invoices",{"page":1,"size":5},"P0","US-D01"),
    ("产品流向追溯","GET","/api/traceability",{"page":1,"size":5},"P1","US-C03"),
]

def login():
    for base in (BASE,):
        try:
            r = requests.post(base + "/api/auth/login", json={
                "tenantCode": TENANT, "username": USER, "password": PWD,
            }, timeout=10)
            if r.status_code == 200:
                data = r.json()
                d=(data.get("data") or {}); token = d.get("accessToken") or d.get("token")
                if token:
                    print(f"[login] via {base} ok, token len={len(token)}")
                    return token, base
        except Exception as e:
            print(f"[login] via {base} exception: {e}")
    return None, None

def classify(module, status, snippet, story):
    """分类：ok / bug500 / missing / partial"""
    if status == 200:
        return "ok"
    if status in (500, 502, 503):
        return "bug500"
    if status in (404, 401, 403):
        return "missing"
    return "unknown"

def main():
    print(f"=== DMS v3.7 API 冒烟  {TS} ===")
    print(f"BASE={BASE}  BACKEND={BACKEND}  TENANT={TENANT}  USER={USER}")
    token, base_url = login()
    if not token:
        print("[FATAL] 登录失败，退出")
        sys.exit(1)
    headers = {"Authorization": f"Bearer {token}"}

    results = []
    for module, method, path, params, prio, story in APIS:
        url = base_url + path
        t0 = time.time()
        try:
            r = requests.request(method, url, params=params, headers=headers, timeout=12)
            elapsed = round((time.time()-t0)*1000)
            body = r.text[:300]
            code_json = None
            try:
                j = r.json()
                code_json = j.get("code")
                total = None
                d = j.get("data")
                if isinstance(d, dict):
                    total = d.get("total") or d.get("totalElements") or (len(d.get("records", [])) if "records" in d else None)
                snippet = f"code={code_json} total={total}"
            except Exception:
                snippet = body[:200]
            cat = classify(module, r.status_code, snippet, story)
            results.append({
                "module": module, "method": method, "path": path, "params": params,
                "story": story, "priority": prio,
                "status": r.status_code, "http_ms": elapsed,
                "biz_code": code_json, "snippet": snippet, "class": cat,
            })
            print(f"  [{r.status_code:>3}] {module:<18} {path:<48} {snippet}")
        except Exception as e:
            results.append({
                "module": module, "method": method, "path": path, "params": params,
                "story": story, "priority": prio,
                "status": 0, "http_ms": round((time.time()-t0)*1000),
                "biz_code": None, "snippet": f"EXC:{e}", "class": "error",
            })
            print(f"  [EXC] {module}: {e}")

    # 汇总
    summary = {"ok":0, "bug500":0, "missing":0, "error":0, "unknown":0}
    for r in results:
        summary[r["class"]] = summary.get(r["class"], 0) + 1
    print("\n=== 汇总 ===")
    print(summary)

    out_json = f"tools/browser-use/reports/api_smoke_{TS}.json"
    with open(out_json, "w", encoding="utf-8") as f:
        json.dump({"base":base_url,"timestamp":TS,"summary":summary,"results":results}, f, ensure_ascii=False, indent=2)
    print(f"[saved] {out_json}")

if __name__ == "__main__":
    main()

