#!/usr/bin/env python3
"""Deploy DMS production environment.

与 scripts/deploy_test.py 差异：
- 部署目录 /opt/dms/prod/（不是 /opt/dms/test/）
- 容器名 dms-prod-*（不是 dms-test-*）
- compose 文件路径 /opt/dms/prod/docker-compose.yml
- 不重启 webgate（webgate 是独立统一网关，不在 prod compose 里）
- 重启 backend 时，依赖 postgres/redis/minio 都在 dms-prod 网络内、且当前 healthy
- 后端 health 通过 nginx 80 端口的 /actuator/health 探测（不直连 18080）
"""
import io, os, sys, tarfile, time
from pathlib import Path
import paramiko

HOST = os.environ.get("DMS_DEPLOY_HOST", "8.133.193.238")
USER = os.environ.get("DMS_DEPLOY_USER", "root")
PASSWORD = os.environ.get("DMS_DEPLOY_PASSWORD")
if not PASSWORD:
    raise SystemExit("Set DMS_DEPLOY_PASSWORD before deploying to production")

PROD_DIR = "/opt/dms/prod"
COMPOSE = f"{PROD_DIR}/docker-compose.yml"
BACKUP_DIR = "/opt/dms/backups"
CONTAINER = "dms-prod-backend"

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / "backend" / "target" / "dms-backend.jar"
FRONT = ROOT / "frontend-vue" / "dist"
ADMIN = ROOT / "admin-vue" / "dist"
for path in (JAR, FRONT / "index.html", ADMIN / "index.html"):
    if not path.exists():
        raise SystemExit(f"Missing build artifact: {path}")

# 生产 admin 以 /dms/admin/ 提供（不是 /admin/）
admin_index = (ADMIN / "index.html").read_text(encoding="utf-8")
if 'src="/dms/admin/' not in admin_index and 'href="/dms/admin/' not in admin_index:
    raise SystemExit(
        "admin 构建的资源基路径不是 /dms/admin/（生产要求）。"
        "请用 VITE_BASE=/dms/admin/ 重新构建 admin-vue（测试环境用 VITE_BASE=/admin/）。"
    )

print(f"=== DEPLOY PROD ===")
print(f"host={HOST} user={USER} prod_dir={PROD_DIR} compose={COMPOSE}")
print(f"jar={JAR.name} ({JAR.stat().st_size // 1048576} MB)")

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(HOST, 22, USER, PASSWORD, timeout=20, banner_timeout=30, auth_timeout=20, allow_agent=False, look_for_keys=False)

def run(cmd, timeout=300, check=True):
    print(f"\n$ {cmd}", flush=True)
    _, stdout, stderr = client.exec_command(
        f"export DMS_DEPLOY_PASSWORD={PASSWORD!r}; {cmd}", timeout=timeout)
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    code = stdout.channel.recv_exit_status()
    if out.strip(): print(out[-4000:], end="")
    if err.strip(): print("STDERR:", err[:2000])
    if check and code != 0:
        raise SystemExit(f"command failed {code}: {cmd}")
    return out

def tar_bytes(path: Path) -> bytes:
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w:gz") as tar:
        for item in path.rglob("*"):
            if item.is_file():
                tar.add(item, arcname=str(item.relative_to(path)).replace("\\", "/"))
    return buf.getvalue()

stamp = time.strftime("%Y%m%d-%H%M%S")
run(f"mkdir -p {BACKUP_DIR} {PROD_DIR}/backend {PROD_DIR}/frontend")
# 备份当前 prod 的 jar + frontend
run(f"cp -a {PROD_DIR}/backend/app.jar {BACKUP_DIR}/app-prod-{stamp}.jar", check=False)
run(f"cp -a {PROD_DIR}/frontend {BACKUP_DIR}/frontend-prod-{stamp}", timeout=180, check=False)

sftp = client.open_sftp()
try:
    print(f"uploading {JAR.name} ({JAR.stat().st_size // 1048576} MB)", flush=True)
    sftp.put(str(JAR), f"{PROD_DIR}/backend/app.jar")
    for name, data in (("dms-front-prod.tar.gz", tar_bytes(FRONT)),
                       ("dms-admin-prod.tar.gz", tar_bytes(ADMIN))):
        remote = f"/tmp/{name}"
        print(f"uploading {name} ({len(data) // 1024} KB)", flush=True)
        with sftp.file(remote, "wb") as handle:
            handle.set_pipelined(True)
            handle.write(data)
finally:
    sftp.close()

# 清空 frontend root（保留 admin 子目录），解压新的
run(f"find {PROD_DIR}/frontend -mindepth 1 -maxdepth 1 ! -name admin -exec rm -rf {{}} +", timeout=180)
run(f"tar -xzf /tmp/dms-front-prod.tar.gz -C {PROD_DIR}/frontend", timeout=180)
run(f"find {PROD_DIR}/frontend/admin -mindepth 1 -maxdepth 1 -exec rm -rf {{}} +", timeout=180)
run(f"tar -xzf /tmp/dms-admin-prod.tar.gz -C {PROD_DIR}/frontend/admin", timeout=180)
# 关键：把 frontend 改成容器外可写（compose 中 volumes 没挂 frontend，所以 root 写、容器内能读即可）
# 但 webgate 容器如果挂这个目录到它的 /usr/share/nginx/html 就需要权限一致
run(f"chown -R root:root {PROD_DIR}/frontend {PROD_DIR}/backend/app.jar")
run(f"rm -f /tmp/dms-front-prod.tar.gz /tmp/dms-admin-prod.tar.gz")

# 重启 backend
# 仅重启 backend 容器本身；不用 compose --force-recreate：旧版 docker-compose v1 会连带重建
# postgres/redis/minio 依赖容器，且与新 docker engine 存在 ContainerConfig 兼容问题。
run("docker restart dms-prod-backend", timeout=120)

# 轮询 health（走公网 80，模拟用户访问路径）
for attempt in range(60):
    time.sleep(5)
    code_line = run(
        "curl -s -o /tmp/dms-health -w '\\n%{http_code}' http://127.0.0.1/actuator/health || true",
        check=False)
    lines = [line.strip() for line in code_line.splitlines() if line.strip()]
    code = lines[-1] if lines else ""
    print(f"health attempt {attempt + 1}: {code}", flush=True)
    if code == "200":
        run("cat /tmp/dms-health")
        break
else:
    run(f"docker logs --tail 200 {CONTAINER}", check=False)
    raise SystemExit("backend health check failed")

# 列容器状态
run("docker ps --format '{{.Names}}\t{{.Status}}\t{{.Ports}}' | grep -E 'dms-prod|webgate'")
client.close()
print(f"\nDEPLOY COMPLETE: backup stamp {stamp}")
print(f"local backup: {BACKUP_DIR}/app-prod-{stamp}.jar + frontend-prod-{stamp}/")
