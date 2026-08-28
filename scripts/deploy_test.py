#!/usr/bin/env python3
"""Deploy DMS test environment without hard-coded credentials.

Layout on server (matches nginx.conf):
  /opt/dms/test/frontend/index.html      -> landing page (root /)
  /opt/dms/test/frontend/dms/            -> business frontend (served at /dms/)
  /opt/dms/test/frontend/dms/admin/      -> platform admin (served at /dms/admin/)
"""
import io
import os
import tarfile
import time
from pathlib import Path

import paramiko

HOST = os.environ.get("DMS_DEPLOY_HOST", "43.128.145.141")
USER = os.environ.get("DMS_DEPLOY_USER", "ubuntu")
PASSWORD = os.environ.get("DMS_DEPLOY_PASSWORD")
if not PASSWORD:
    raise SystemExit("Set DMS_DEPLOY_PASSWORD before deploying")

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / "backend/target/dms-backend.jar"
FRONT = ROOT / "frontend-vue/dist"
ADMIN = ROOT / "admin-vue/dist"
LANDING = ROOT / "deploy/landing/index.html"
LANDING_ASSETS = ROOT / "DMS产品宣传手册/assets"
BROCHURE_PAGES = ROOT / "DMS产品宣传手册/pages"
for path in (JAR, FRONT / "index.html", ADMIN / "index.html"):
    if not path.exists():
        raise SystemExit(f"Missing build artifact: {path}")

front_index = (FRONT / "index.html").read_text(encoding="utf-8")
if "/dms/assets/" not in front_index:
    raise SystemExit(
        "frontend-vue 构建的资源基路径不是 /dms/。请用 VITE_BASE=/dms/ 重新构建。"
    )

admin_index = (ADMIN / "index.html").read_text(encoding="utf-8")
if "/dms/admin/assets/" not in admin_index:
    raise SystemExit(
        "admin-vue 构建的资源基路径不是 /dms/admin/。请用 VITE_BASE=/dms/admin/ 重新构建。"
    )

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(HOST, port=22, username=USER, password=PASSWORD, timeout=20, banner_timeout=30, auth_timeout=20, allow_agent=False, look_for_keys=False)
sudo = 'echo "$DMS_DEPLOY_PASSWORD" | sudo -S -p ""'

def run(cmd, timeout=300, check=True):
    print(f"\n$ {cmd}", flush=True)
    _, stdout, stderr = client.exec_command(f"export DMS_DEPLOY_PASSWORD={PASSWORD!r}; {cmd}", timeout=timeout)
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

def tar_brochure_bytes() -> bytes:
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w:gz") as tar:
        if BROCHURE_PAGES.is_dir():
            for item in BROCHURE_PAGES.rglob("*"):
                if item.is_file():
                    tar.add(item, arcname=item.name)
        if LANDING_ASSETS.is_dir():
            for item in LANDING_ASSETS.rglob("*"):
                if item.is_file():
                    tar.add(item, arcname="assets/" + item.name)
    return buf.getvalue()

stamp = time.strftime("%Y%m%d-%H%M%S")
run(f"{sudo} mkdir -p /opt/dms/backups /opt/dms/test/backend /opt/dms/test/frontend/dms/admin")
run(f"{sudo} cp -a /opt/dms/test/backend/app.jar /opt/dms/backups/app-{stamp}.jar", check=False)
run(f"{sudo} cp -a /opt/dms/test/frontend /opt/dms/backups/frontend-{stamp}", timeout=180, check=False)

sftp = client.open_sftp()
try:
    run(f"{sudo} chown {USER}:{USER} /opt/dms/test/backend/app.jar", check=False)
    print(f"uploading {JAR.name} ({JAR.stat().st_size // 1048576} MB)", flush=True)
    sftp.put(str(JAR), "/opt/dms/test/backend/app.jar")
    for name, data in (("dms-front.tar.gz", tar_bytes(FRONT)),
                       ("dms-admin.tar.gz", tar_bytes(ADMIN)),
                       ("dms-brochure.tar.gz", tar_brochure_bytes())):
        remote = f"/home/ubuntu/{name}"
        print(f"uploading {name} ({len(data) // 1024} KB)", flush=True)
        with sftp.file(remote, "wb") as handle:
            handle.set_pipelined(True)
            handle.write(data)
    if LANDING.exists():
        print("uploading landing page", flush=True)
        sftp.put(str(LANDING), "/home/ubuntu/dms-landing.html")
    if LANDING_ASSETS.is_dir():
        print(f"uploading landing assets ({len(list(LANDING_ASSETS.iterdir()))} files)", flush=True)
        with sftp.file("/home/ubuntu/dms-landing-assets.tar.gz", "wb") as handle:
            handle.set_pipelined(True)
            handle.write(tar_bytes(LANDING_ASSETS))
finally:
    sftp.close()

run(f"{sudo} mkdir -p /opt/dms/test/frontend/dms", timeout=60)
run(f"{sudo} find /opt/dms/test/frontend/dms -mindepth 1 -maxdepth 1 ! -name admin -exec rm -rf {{}} +", timeout=180)
run(f"{sudo} tar -xzf /home/ubuntu/dms-front.tar.gz -C /opt/dms/test/frontend/dms", timeout=180)
run(f"{sudo} mkdir -p /opt/dms/test/frontend/dms/admin", timeout=60)
run(f"{sudo} find /opt/dms/test/frontend/dms/admin -mindepth 1 -maxdepth 1 -exec rm -rf {{}} +", timeout=180)
run(f"{sudo} tar -xzf /home/ubuntu/dms-admin.tar.gz -C /opt/dms/test/frontend/dms/admin", timeout=180)
if LANDING.exists():
    run(f"{sudo} cp /home/ubuntu/dms-landing.html /opt/dms/test/frontend/index.html", timeout=60)
    run("rm -f /home/ubuntu/dms-landing.html")
if LANDING_ASSETS.is_dir():
    run(f"{sudo} rm -rf /opt/dms/test/frontend/assets", timeout=120)
    run(f"{sudo} mkdir -p /opt/dms/test/frontend/assets", timeout=60)
    run(f"{sudo} tar -xzf /home/ubuntu/dms-landing-assets.tar.gz -C /opt/dms/test/frontend/assets", timeout=180)
    run("rm -f /home/ubuntu/dms-landing-assets.tar.gz")
if BROCHURE_PAGES.is_dir():
    run(f"{sudo} rm -rf /opt/dms/test/frontend/brochure", timeout=120)
    run(f"{sudo} mkdir -p /opt/dms/test/frontend/brochure", timeout=60)
    run(f"{sudo} tar -xzf /home/ubuntu/dms-brochure.tar.gz -C /opt/dms/test/frontend/brochure", timeout=180)
run("rm -f /home/ubuntu/dms-brochure.tar.gz")
run(f"{sudo} chown -R root:root /opt/dms/test/frontend /opt/dms/test/backend/app.jar")
run(f"{sudo} docker compose -f /opt/dms/test/docker-compose.yml up -d --force-recreate backend-test nginx-test", timeout=240)

for attempt in range(36):
    time.sleep(5)
    out = run('curl -s -o /tmp/dms-health -w "\\n%{http_code}" http://127.0.0.1/actuator/health || true', check=False)
    lines = [line.strip() for line in out.splitlines() if line.strip()]
    code = lines[-1] if lines else ""
    print(f"health attempt {attempt + 1}: {code}", flush=True)
    if code == "200":
        run("cat /tmp/dms-health")
        break
else:
    run(f"{sudo} docker logs --tail 120 dms-test-backend", check=False)
    raise SystemExit("backend health check failed")

run('rm -f /home/ubuntu/dms-front.tar.gz /home/ubuntu/dms-admin.tar.gz')
run("curl -s -o /dev/null -w 'landing %{http_code}\\n' http://127.0.0.1/")
run("curl -s -o /dev/null -w 'landing-asset %{http_code}\\n' http://127.0.0.1/assets/01-pc-login-new.png")
run("curl -s -o /dev/null -w 'dms %{http_code}\\n' http://127.0.0.1/dms/")
run("curl -s -o /dev/null -w 'dms-admin %{http_code}\\n' http://127.0.0.1/dms/admin/")
run("curl -s -o /dev/null -w 'brochure %{http_code}\\n' http://127.0.0.1/brochure/")
run(f"{sudo} docker ps --format '{{.Names}}\t{{.Status}}\t{{.Ports}}'")
client.close()
print(f"DEPLOY COMPLETE: backup stamp {stamp}")
