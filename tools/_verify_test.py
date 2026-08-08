import paramiko, sys, time, json
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect("8.133.193.238", port=22, username="root", password="Welcomeyyx0616", timeout=15, allow_agent=False, look_for_keys=False)

def run(cmd, timeout=30):
    print(f"\n>>> {cmd[:120]}")
    si, so, se = client.exec_command(cmd, timeout=timeout)
    out = so.read().decode("utf-8", errors="replace")
    err = se.read().decode("utf-8", errors="replace").rstrip()
    if err: print("STDERR:", err[:300])
    return out

# 1) 8083 页面可达
print("\n=== 1) HTTP 8083 主页 ===")
out = run("curl -s -o /dev/null -w 'http_code=%{http_code} time=%{time_total}s size=%{size_download}\\n' http://localhost:8083/")
print(out)

# 2) 8083 SPA index.html 内容包含移动端路由（title）
out = run("curl -s http://localhost:8083/ | head -25")
print(out)

# 3) 检查 MProfile 路由被 Vite 切了 chunk
out = run("curl -s http://localhost:8083/assets/MProfile-B7h6jOsD.js | head -c 400")
print("\nMProfile chunk:", out[:300])

# 4) 容器内 dist 内容确认
out = run("docker exec dms-test-frontend ls /usr/share/nginx/html/assets | grep -E 'M(Home|Orders|Order|Profile|Surgery|Dashboard|Layout)' | wc -l")
print("mobile chunks in container:", out.strip())

# 5) 通过前端 8083 调后端登录
print("\n=== 2) 登录接口 (通过 nginx 8083) ===")
out = run("curl -s -X POST -H 'Content-Type: application/json' -d '{\"tenantCode\":\"default\",\"username\":\"admin\",\"password\":\"Sh123456\"}' http://localhost:8083/api/auth/login")
print(out[:500])

client.close()