import json, os, glob
# 找到最新两份
api = sorted(glob.glob("tools/browser-use/reports/api_smoke_*.json"))[-1]
ui = sorted(glob.glob("tools/browser-use/reports/ui_smoke_*.json"))[-1]
print("api:", api)
print("ui:", ui)
a = json.load(open(api, encoding="utf-8"))
u = json.load(open(ui, encoding="utf-8"))
# 汇总 500 API 详情
bugs = [r for r in a["results"] if r["class"] == "bug500"]
ok = [r for r in a["results"] if r["class"] == "ok"]
print(f"\nAPI: total {len(a['results'])}, ok {len(ok)}, bug500 {len(bugs)}")
for r in bugs:
    print(f"  500 [{r['priority']:>2}] {r['module']:<18} {r['path']}  story={r['story']}")

# UI 汇总
print("\nUI PC:", u["pc_summary"])
print("UI Mobile:", u["mobile_summary"])
for r in u["pc"]:
    if r["status"] != "ok":
        print(f"  [{r['status']:<10}] {r['name']:<12} 5xx={len(r.get('api_5xx',[]))}")
        for c in r.get("api_5xx",[]):
            print(f"      500 {c['url']}")
