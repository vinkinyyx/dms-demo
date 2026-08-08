import paramiko, sys
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect("8.133.193.238", port=22, username="root", password="Welcomeyyx0616", timeout=15, allow_agent=False, look_for_keys=False)
def run(cmd):
    si, so, se = client.exec_command(cmd)
    out = so.read().decode("utf-8", errors="replace")
    err = se.read().decode("utf-8", errors="replace").rstrip()
    return out, err

# 1) 验证容器内 nginx 代理目标是不是 dms-backend:8080
out, _ = run("docker exec dms-frontend-vue cat /etc/nginx/nginx.conf | grep -A 1 'proxy_pass\\|upstream' | head -20")
print("nginx proxy conf:")
print(out)

# 2) 验证从容器内能解析 dms-backend
out, _ = run("docker exec dms-frontend-vue getent hosts dms-backend 2>&1")
print("DNS resolve dms-backend:", out.strip())

# 3) HTTP 主页
out, _ = run("curl -s -o /dev/null -w 'home=%{http_code}\\n' http://localhost:8081/")
print(out.strip())

# 4) 登录
out, _ = run("""curl -s -X POST -H 'Content-Type: application/json' -d '{"tenantCode":"default","username":"admin","password":"Sh123456"}' http://localhost:8081/api/auth/login -o /dev/null -w 'login=%{http_code}\\n'""")
print(out.strip())

# 5) 关键 chunk
out, _ = run("curl -s -o /dev/null -w 'MHome=%{http_code}\\n' http://localhost:8081/assets/MHome-3U9nxINY.js")
print(out.strip())

# 6) 跑 E2E 接口矩阵
import json
out, _ = run("""curl -s -X POST -H 'Content-Type: application/json' -d '{"tenantCode":"default","username":"admin","password":"Sh123456"}' http://localhost:8081/api/auth/login""")
data = json.loads(out)
token = data["data"]["accessToken"]
print(f"token len = {len(token)}")

eps = [
    "/api/dashboard/kpi?period=today",
    "/api/dashboard/kpi?period=month",
    "/api/orders?page=1&size=5",
    "/api/surgery-reports?page=1&size=5",
    "/api/dashboard/sales-trend",
    "/api/dashboard/top-dealers?period=month",
    "/api/lookups/dealers?limit=5",
    "/api/lookups/hospitals?limit=5",
    "/api/lookups/warehouses?limit=5",
]
for ep in eps:
    out, _ = run(f"curl -s -o /dev/null -w '%{{http_code}}' -H 'Authorization: Bearer {token}' 'http://localhost:8081{ep}'")
    print(f"  [{out}] {ep}")

client.close()