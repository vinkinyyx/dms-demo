import paramiko, sys, time
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect("8.133.193.238", port=22, username="root", password="Welcomeyyx0616", timeout=15, allow_agent=False, look_for_keys=False)

def run(cmd, timeout=180):
    print(f"\n>>> {cmd[:120]}")
    si, so, se = client.exec_command(cmd, timeout=timeout)
    out = so.read().decode("utf-8", errors="replace")
    err = se.read().decode("utf-8", errors="replace")
    if out: print(out.rstrip())
    if err: print("STDERR:", err[:600])
    return out

# 1) 上传 zip 到生产端
from scp import SCPClient
local_zip = r"C:\Users\vinki\AppData\Local\Temp\dms-frontend-v3.9.0.zip"
remote_zip = "/opt/dms/frontend-vue/dms-frontend-v3.9.0.zip"
with SCPClient(client.get_transport()) as scp:
    scp.put(local_zip, remote_zip)
run(f"ls -la {remote_zip}")

# 2) 写 Dockerfile.dist
run("cat > /opt/dms/frontend-vue/Dockerfile.dist << 'EOF'\nFROM nginx:1.25-alpine\nCOPY dist /usr/share/nginx/html\nCOPY nginx-vue.conf /etc/nginx/nginx.conf\nEXPOSE 80\nCMD [\"nginx\", \"-g\", \"daemon off;\"]\nEOF")
run("cat /opt/dms/frontend-vue/Dockerfile.dist")

# 3) 准备 dist 目录
stamp = time.strftime("%Y%m%d-%H%M%S")
run("mkdir -p /opt/dms/frontend-vue/dist && rm -rf /opt/dms/frontend-vue/dist/* /opt/dms/frontend-vue/dist/.[!.]* 2>/dev/null")
run("cd /opt/dms/frontend-vue && unzip -q dms-frontend-v3.9.0.zip -d dist && ls dist | head")

# 4) 备份旧镜像
run("docker tag dms-frontend-vue:latest dms-frontend-vue:backup-" + stamp + " 2>&1 || true")
run("docker images | grep dms-frontend-vue")

# 5) 删旧镜像（避免缓存）
run("docker rmi dms-frontend-vue:latest 2>&1 || echo 'remove failed, will try force'")
run("docker builder prune -af 2>&1 | tail -3")

# 6) 构建新镜像
run("cd /opt/dms/frontend-vue && docker build -f Dockerfile.dist -t dms-frontend-vue:latest . 2>&1 | tail -20", timeout=300)

# 7) 验证镜像里的 dist 是新版
run("docker run --rm dms-frontend-vue:latest ls /usr/share/nginx/html/assets | grep -E 'M(Home|Orders|Order|Profile|Surgery|Dashboard)' | sort")

# 8) 删旧容器，跑新容器 (沿用原端口 8081)
run("docker rm -f dms-frontend-vue 2>&1 || true")
run("docker run -d --name dms-frontend-vue --restart unless-stopped -p 8081:80 --network dms-net dms-frontend-vue:latest")
time.sleep(3)
run("docker ps --filter name=dms-frontend-vue --format 'table {{.Names}}\\t{{.Image}}\\t{{.Status}}\\t{{.Ports}}'")

client.close()