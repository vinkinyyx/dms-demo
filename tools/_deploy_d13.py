"""D13/v3.8.8 部署脚本 — 后端 jar (容器内) + 前端 dist + admin dist 上传到测试服务器 8.133.193.238"""
import paramiko, os, sys, time, subprocess
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
HOST = '8.133.193.238'
PORT = 22
USER = 'root'
PASS = 'Welcomeyyx0616'
BACKEND_JAR = r'D:\Workspace\TRAE\DMS\backend\target\dms-backend.jar'
ADMIN_DIST = r'D:\Workspace\TRAE\DMS\admin-vue\dist'
FRONTEND_DIST = r'D:\Workspace\TRAE\DMS\frontend-vue\dist'

def ssh():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, port=PORT, username=USER, password=PASS, timeout=15, allow_agent=False, look_for_keys=False)
    return c

def run(c, cmd, timeout=120):
    print(f'\n>>> {cmd[:300]}')
    si, so, se = c.exec_command(cmd, timeout=timeout)
    out = so.read().decode('utf-8', errors='replace').rstrip()
    err = se.read().decode('utf-8', errors='replace').rstrip()
    if out: print(out)
    if err: print('STDERR:', err[:800])
    return out, err

def zip_dir(local, out_zip):
    if os.path.exists(out_zip):
        os.remove(out_zip)
    subprocess.run(['powershell', '-NoProfile', '-Command',
        f"Compress-Archive -Path '{local}\\*' -DestinationPath '{out_zip}' -Force"],
        check=True)
    print(f'zipped: {out_zip} ({os.path.getsize(out_zip)/1024:.1f} KB)')

def main():
    c = ssh()
    sftp = c.open_sftp()
    tmp = os.environ.get('TEMP', '/tmp')
    admin_zip = os.path.join(tmp, 'admin-vue-v3.8.8.zip')
    frontend_zip = os.path.join(tmp, 'frontend-vue-v3.8.8.zip')
    # 1) 压缩
    zip_dir(ADMIN_DIST, admin_zip)
    zip_dir(FRONTEND_DIST, frontend_zip)
    # 2) 上传后端 jar 到 /opt/dms/dms-test/admin-vue/app.jar.new（宿主机）
    remote_jar = '/opt/dms/dms-test/admin-vue/app.jar.new'
    sftp.put(BACKEND_JAR, remote_jar)
    print('uploaded jar', os.path.getsize(BACKEND_JAR))
    # 3) 上传 admin zip
    remote_admin_zip = '/tmp/' + os.path.basename(admin_zip)
    sftp.put(admin_zip, remote_admin_zip)
    print('uploaded admin', os.path.getsize(admin_zip))
    # 4) 上传 frontend zip
    remote_frontend_zip = '/tmp/' + os.path.basename(frontend_zip)
    sftp.put(frontend_zip, remote_frontend_zip)
    print('uploaded frontend', os.path.getsize(frontend_zip))
    sftp.close()
    # 5) 后端操作：备份 /app/app.jar → 替换 → 重启
    run(c, 'docker exec dms-test-backend sh -c "cp /app/app.jar /app/app.jar.bak.$(date +%Y%m%d-%H%M%S)"')
    run(c, f'docker cp {remote_jar} dms-test-backend:/app/app.jar')
    run(c, 'docker restart dms-test-backend')
    # 6) admin dist 解压到宿主机再 docker cp
    run(c, f'rm -rf /opt/dms/dms-test/admin-dist && mkdir -p /opt/dms/dms-test/admin-dist && unzip -q -o {remote_admin_zip} -d /opt/dms/dms-test/admin-dist && ls /opt/dms/dms-test/admin-dist | head -5')
    run(c, 'docker exec dms-test-frontend sh -c "rm -rf /usr/share/nginx/html/admin/*"')
    run(c, 'docker cp /opt/dms/dms-test/admin-dist/. dms-test-frontend:/usr/share/nginx/html/admin/')
    # 7) frontend dist
    run(c, f'rm -rf /opt/dms/dms-test/frontend-dist/dist && mkdir -p /opt/dms/dms-test/frontend-dist/dist && unzip -q -o {remote_frontend_zip} -d /opt/dms/dms-test/frontend-dist/dist && ls /opt/dms/dms-test/frontend-dist/dist | head -5')
    run(c, 'docker exec dms-test-frontend sh -c "rm -rf /usr/share/nginx/html/assets /usr/share/nginx/html/index.html"')
    run(c, 'docker cp /opt/dms/dms-test/frontend-dist/dist/. dms-test-frontend:/usr/share/nginx/html/')
    # 8) 重启前端
    run(c, 'docker restart dms-test-frontend && sleep 3')
    run(c, 'docker ps --format "table {{.Names}}\t{{.Status}}" | grep -E "dms-test-(backend|frontend)"')
    # 9) 等后端 ready
    print('\n=== wait backend health ===')
    for i in range(30):
        out, _ = run(c, 'curl -s -m 2 http://localhost:8082/actuator/health 2>&1', timeout=5)
        if 'UP' in out:
            print('backend UP after', i, 'tries')
            break
        time.sleep(2)
    # 10) 校验 nginx proxy
    run(c, 'docker exec dms-test-frontend sh -c "grep -E proxy_pass /etc/nginx/conf.d/default.conf"')
    c.close()

if __name__ == '__main__':
    main()
