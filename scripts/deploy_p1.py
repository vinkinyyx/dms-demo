import paramiko, time, sys, os
from pathlib import Path

HOST='43.128.145.141'; USER='ubuntu'; PWD='Welcomeyyx0616'
ROOT=Path('.')
JAR=ROOT/'backend/target/dms-backend.jar'
FRONT=ROOT/'frontend-vue/dist'; ADMIN=ROOT/'admin-vue/dist'
assert JAR.exists(), JAR
assert (FRONT/'index.html').exists(), FRONT
assert (ADMIN/'index.html').exists(), ADMIN

c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(HOST,22,USER,PWD,timeout=20,banner_timeout=30,auth_timeout=20,allow_agent=False,look_for_keys=False)
SUDO='echo Welcomeyyx0616 | sudo -S -p ""'

def run(cmd, t=300, check=True):
    print(f"\n$ {cmd}", flush=True)
    _,o,e=c.exec_command(cmd,timeout=t)
    out=o.read().decode(errors='replace'); err=e.read().decode(errors='replace'); code=o.channel.recv_exit_status()
    print(out, end='')
    if err.strip(): print("STDERR:", err[:2000])
    if check and code!=0: raise SystemExit(f'command failed {code}: {cmd}')
    return out,err,code

stamp=time.strftime('%Y%m%d-%H%M%S')
# 1. backups
run(f'{SUDO} mkdir -p /opt/dms/backups')
run(f'{SUDO} cp -a /opt/dms/test/backend/app.jar /opt/dms/backups/app-{stamp}.jar')
run(f'{SUDO} cp -a /opt/dms/test/docker-compose.yml /opt/dms/backups/docker-compose-{stamp}.yml')
run(f'{SUDO} cp -a /opt/dms/test/frontend /opt/dms/backups/frontend-{stamp}', t=120)

# 2. update compose: port 80:80 and APP_BASE_URL to port 80
run(f"{SUDO} sed -i 's|\"8083:80\"|\"80:80\"|g' /opt/dms/test/docker-compose.yml")
run(f"{SUDO} sed -i 's|APP_BASE_URL: http://43.128.145.141:8083|APP_BASE_URL: http://43.128.145.141|g' /opt/dms/test/docker-compose.yml")
run(f"{SUDO} grep -n '80:80\\|APP_BASE_URL' /opt/dms/test/docker-compose.yml")

# 3. upload backend jar
print("\n==> uploading backend jar (%d MB)" % (JAR.stat().st_size//1048576), flush=True)
sftp=c.open_sftp()
c.exec_command(f'{SUDO} chown ubuntu:ubuntu /opt/dms/test/backend/app.jar')[1].channel.recv_exit_status()
sftp.put(str(JAR), '/opt/dms/test/backend/app.jar')
sftp.close()
print("jar uploaded", flush=True)

# 4. upload frontend + admin as tar.gz
import tarfile, io
def tar_bytes(path):
    buf=io.BytesIO()
    with tarfile.open(fileobj=buf, mode='w:gz') as t:
        for p in path.rglob('*'):
            if p.is_file(): t.add(p, arcname=str(p.relative_to(path)).replace('\\','/'))
    return buf.getvalue()
front_tar=tar_bytes(FRONT); admin_tar=tar_bytes(ADMIN)
sftp=c.open_sftp()
for name,data in [('dms-front-p1.tar.gz',front_tar),('dms-admin-p1.tar.gz',admin_tar)]:
    with sftp.file('/home/ubuntu/'+name,'wb') as f:
        f.set_pipelined(True); f.write(data)
    print('uploaded',name,len(data), flush=True)
sftp.close()
run(f'{SUDO} find /opt/dms/test/frontend -mindepth 1 -maxdepth 1 ! -path "*/admin" -exec rm -rf {{}} +')
run(f'{SUDO} tar -xzf /home/ubuntu/dms-front-p1.tar.gz -C /opt/dms/test/frontend')
run(f'{SUDO} mkdir -p /opt/dms/test/frontend/admin')
run(f'{SUDO} find /opt/dms/test/frontend/admin -mindepth 1 -maxdepth 1 -exec rm -rf {{}} +')
run(f'{SUDO} tar -xzf /home/ubuntu/dms-admin-p1.tar.gz -C /opt/dms/test/frontend/admin')
run(f'{SUDO} chown -R ubuntu:ubuntu /opt/dms/test/frontend')

# 5. recreate backend (picks new jar + APP_BASE_URL), nginx (port change)
run(f'{SUDO} docker compose -f /opt/dms/test/docker-compose.yml up -d backend-test nginx-test', t=180)

# 6. wait for health
print("\n==> waiting for backend health...", flush=True)
ok=False
for i in range(30):
    time.sleep(5)
    out,_,_=run('curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1/api/actuator/health || true', check=False)
    code=out.strip().splitlines()[-1].strip()
    print(f"  attempt {i+1}: {code}", flush=True)
    if code=='200': ok=True; break
if not ok:
    print("WARN: health not 200 yet, checking logs")
    run(f'{SUDO} docker logs --tail 40 dms-test-backend', check=False)

run(f'{SUDO} docker ps --format "{{{{.Names}}}}\t{{{{.Status}}}}\t{{{{.Ports}}}}"')
run('rm -f /home/ubuntu/dms-front-p1.tar.gz /home/ubuntu/dms-admin-p1.tar.gz')
c.close()
print("\nDEPLOY P1 COMPLETE backups at /opt/dms/backups/"+stamp)