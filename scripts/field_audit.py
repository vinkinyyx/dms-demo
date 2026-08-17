"""
字段一致性审计脚本 (v3.8.4+)

目的：防止“前端表单填写了字段，但后端列表/详情不返回，导致编辑回填为空”这类问题。

原理：
  1. 解析 frontend-vue/src/config/modules.js，抽取每个模块的 form 字段 key。
  2. 对每个可编辑模块，调用后端 列表接口 与 详情接口。
  3. 对比 form 字段是否出现在列表行或详情中；若两者都缺失，报告为风险字段。

用法：
  python scripts/field_audit.py
  python scripts/field_audit.py --base http://localhost:8080

退出码：发现真实问题返回 1，否则返回 0（可纳入 CI）。
"""
import argparse, json, re, sys, urllib.request, urllib.error

DEFAULT_BASE = "http://43.128.145.141"
IGNORE_FIELDS = {"password"}          # 密码出于安全不返回，忽略
DETAIL_ONLY = {"lines", "attrs"}      # 列表可能省略，详情回填即可
SKIP_KEYS = {"materials"}             # 未启用/无后端的遗留模块
# ?????????????? Jackson non_null ???????????????
NON_NULL_FIELDS = {
    "authorizations": {"categoryIds", "terminalIds", "remark"},
    "surgery-reports": {"remark"},
    "warehouses": {"hospitalId"},
    "users": {"dealerId"},
}


def http(method, url, token=None, body=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8"))
        except Exception:
            return e.code, None
    except Exception as e:
        return -1, {"error": str(e)}


def parse_modules(path):
    src = open(path, encoding="utf-8").read()
    mods = []
    pat = re.compile(
        r"key:\s*'([^']+)',\s*title:[^,]+,\s*api:\s*'([^']+)'(.*?)(?=\n\s*key:|\nconst |\n]\s*;|\Z)",
        re.S)
    for m in pat.finditer(src):
        key, api, rest = m.group(1), m.group(2), m.group(3)
        detailable = "detailable" in rest
        readonly = "readonly" in rest or "noCreate" in rest
        no_edit = "noEdit" in rest
        formkeys = re.findall(r"\{\s*key:\s*'([^']+)'", rest)
        mods.append(dict(key=key, api=api, detailable=detailable,
                         readonly=readonly, no_edit=no_edit, formkeys=formkeys))
    return mods


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default=DEFAULT_BASE)
    ap.add_argument("--user", default="admin")
    ap.add_argument("--password", default="Sh123456")
    ap.add_argument("--tenant", default="default")
    args = ap.parse_args()

    st, res = http("POST", args.base + "/api/auth/login",
                   body={"tenantCode": args.tenant, "username": args.user, "password": args.password})
    if st != 200 or not res or not res.get("data"):
        print("登录失败:", st, res)
        return 2
    token = res["data"]["accessToken"]

    mods = parse_modules("frontend-vue/src/config/modules.js")
    problems = 0
    checked = 0
    for mod in mods:
        if not mod["formkeys"] or mod["readonly"] or mod["no_edit"] or mod["key"] in SKIP_KEYS:
            continue
        st, lr = http("GET", args.base + mod["api"] + "?page=1&size=50", token)
        if st != 200 or not lr or not lr.get("data"):
            print(f"[SKIP] {mod['key']} list {st}")
            continue
        data = lr["data"]
        lst = data.get("list") if isinstance(data, dict) else data
        if not lst:
            print(f"[EMPTY] {mod['key']} 无数据，跳过")
            continue
        list_keys = set()
        for r in lst:
            list_keys.update(r.keys())
        row = lst[0]
        rid = row.get("id")
        miss = [k for k in mod["formkeys"]
                if k not in list_keys
                and k not in IGNORE_FIELDS
                and not k.endswith("Name")]
        detail = {}
        if mod["detailable"] and rid:
            st2, dr = http("GET", f"{args.base}{mod['api']}/{rid}", token)
            if st2 == 200 and dr and dr.get("data"):
                detail = dr["data"] or {}
            else:
                print(f"[WARN] {mod['key']} detail 接口异常: HTTP {st2}")
        real_miss = []
        allowed_null = NON_NULL_FIELDS.get(mod["key"], set())
        for k in miss:
            if k in detail:
                continue
            if k in DETAIL_ONLY and detail:
                continue
            if k in allowed_null:
                continue
            real_miss.append(k)
        if real_miss:
            problems += 1
            print(f"[RISK] {mod['key']} ({mod['api']}) 表单字段在列表/详情均缺失: {real_miss}")
        checked += 1

    print(f"\n检查 {checked} 个模块，发现 {problems} 处字段回填风险。")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
