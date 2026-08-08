import paramiko, sys, time
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect("8.133.193.238", port=22, username="root", password="Welcomeyyx0616", timeout=15, allow_agent=False, look_for_keys=False)

def run(cmd, timeout=60):
    print(f"\n>>> {cmd}")
    si, so, se = client.exec_command(cmd, timeout=timeout)
    out = so.read().decode("utf-8", errors="replace")
    print(out.rstrip())
    err = se.read().decode("utf-8", errors="replace").rstrip()
    if err: print("STDERR:", err[:400])
    return out

# 1) 备份旧 dist
stamp = time.strftime("%Y%m%d-%H%M%S")
run(f"cd /opt/dms/dms-test/frontend-dist && cp -r dist dist.bak.{stamp} && ls -d dist*")

# 2) 清空 dist 内容，保留目录
run("rm -rf /opt/dms/dms-test/frontend-dist/dist/* /opt/dms/dms-test/frontend-dist/dist/.[!.]* 2>/dev/null; ls -la /opt/dms/dms-test/frontend-dist/dist")

# 3) 解压新 dist
run("cd /opt/dms/dms-test/frontend-dist && unzip -q dms-frontend-v3.9.0.zip -d dist && ls dist | head -10")

# 4) 检查关键文件
run("grep -c 'MOrderDetail\\|MSurgeryReports' /opt/dms/dms-test/frontend-dist/dist/assets/index-*.js 2>/dev/null || true")
run("ls /opt/dms/dms-test/frontend-dist/dist/assets | grep -E 'M(Home|Orders|Order|Profile|Surgery|Dashboard|Layout)' | head -20")

# 5) docker cp 进容器
run("docker cp /opt/dms/dms-test/frontend-dist/dist/. dms-test-frontend:/usr/share/nginx/html/")

# 6) restart
run("docker restart dms-test-frontend && sleep 3 && docker ps --filter name=dms-test-frontend --format '{{.Names}} {{.Status}}'")

client.close()