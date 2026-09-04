#!/usr/bin/env python3
"""Targeted v4.6.3 deploy: backend jar + business frontend only. No nginx/admin/brochure changes."""
import io, os, tarfile, time
from pathlib import Path
import paramiko

HOST = os.environ.get("DMS_DEPLOY_HOST", "43.128.145.141")
USER = os.environ.get("DMS_DEPLOY_USER", "ubuntu")
PASSWORD = os.environ["DMS_DEPLOY_PASSWORD"]
ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / "backend/target/dms-backend.jar"
FRONT = ROOT / "frontend-vue/dist"
assert JAR.exists(), JAR
assert (FRONT / "index.html").exists()
assert "/dms/assets/" in (FRONT / "index.html").read_text(encoding="utf-8"), "frontend must be built with VITE_BASE=/dms/"

cli = paramiko.SSHClient(); cli.set_missing_host_key_policy(paramiko.AutoAddPolicy())
cli.connect(HOST, 22, USER, PASSWORD, timeout=20, banner_timeout=30, auth_timeout=20,
            allow_agent=False, look_for_keys=False)
sudo = 'echo "$DMS_DEPLOY_PASSWORD" | sudo -S -p ""'

def run(cmd, timeout=300, check=True):
    print(f"\n$ {cmd}", flush=True)
    _, out, err = cli.exec_command(f"export DMS_DEPLOY_PASSWORD={PASSWORD!r}; {cmd}", timeout=timeout)
    o = out.read().decode("utf-8","replace"); e = err.read().decode("utf-8","replace")
    code = out.channel.recv_exit_status()
    if o.strip(): print(o[-3000:], end="")
    if e.strip(): print("STDERR:", e[:1500])
    if check and code != 0: raise SystemExit(f"failed {code}: {cmd}")
    return o

def tar_bytes(path):
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w:gz") as t:
        for it in path.rglob("*"):
            if it.is_file(): t.add(it, arcname=str(it.relative_to(path)).replace("\\","/"))
    return buf.getvalue()

stamp = time.strftime("%Y%m%d-%H%M%S")
run(f"{sudo} mkdir -p /opt/dms/backups /opt/dms/test/backend /opt/dms/test/frontend/dms")
run(f"{sudo} cp -a /opt/dms/test/backend/app.jar /opt/dms/backups/app-{stamp}.jar", check=False)
run(f"{sudo} cp -a /opt/dms/test/frontend/dms /opt/dms/backups/dms-front-{stamp}", timeout=180, check=False)

sftp = cli.open_sftp()
try:
    run(f"{sudo} chown {USER}:{USER} /opt/dms/test/backend/app.jar", check=False)
    print(f"uploading app.jar ({JAR.stat().st_size//1048576} MB)", flush=True)
    sftp.put(str(JAR), "/opt/dms/test/backend/app.jar")
    data = tar_bytes(FRONT)
    with sftp.file("/home/ubuntu/dms-front.tar.gz","wb") as h:
        h.set_pipelined(True); h.write(data)
    print(f"uploaded dms-front.tar.gz ({len(data)//1024} KB)", flush=True)
finally:
    sftp.close()

run(f"{sudo} mkdir -p /opt/dms/test/frontend/dms", timeout=60)
run(f"{sudo} find /opt/dms/test/frontend/dms -mindepth 1 -maxdepth 1 ! -name admin -exec rm -rf {{}} +", timeout=180)
run(f"{sudo} tar -xzf /home/ubuntu/dms-front.tar.gz -C /opt/dms/test/frontend/dms", timeout=180)
run(f"{sudo} chown -R root:root /opt/dms/test/frontend /opt/dms/test/backend/app.jar")
run("rm -f /home/ubuntu/dms-front.tar.gz")
# restart only backend (jar changed). nginx serves bind-mounted static files; no nginx change.
run(f"{sudo} docker compose -f /opt/dms/test/docker-compose.yml up -d --force-recreate backend-test", timeout=240)

for attempt in range(40):
    time.sleep(5)
    o = run('curl -s -o /tmp/h -w "%{http_code}" http://127.0.0.1/actuator/health || true', check=False)
    lines=[l.strip() for l in o.splitlines() if l.strip()]
    code = lines[-1] if lines else ""
    print(f"health {attempt+1}: {code}", flush=True)
    if code == "200":
        run("cat /tmp/h"); break
else:
    run(f"{sudo} docker logs --tail 150 dms-test-backend 2>/dev/null || {sudo} docker logs --tail 150 backend-test", check=False)
    raise SystemExit("backend health failed")

for u in ["/", "/dms/", "/dms/admin/", "/dms/mobile/login"]:
    run(f"curl -s -o /dev/null -w '{u} %{{http_code}}\\n' http://127.0.0.1{u}")
run(f"{sudo} docker ps --format '{{{{.Names}}}}\t{{{{.Status}}}}'")
cli.close()
print(f"DEPLOY COMPLETE stamp={stamp}")
