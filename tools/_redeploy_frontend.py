import paramiko, os, sys, time, subprocess
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', port=22, username='root', password='Welcomeyyx0616', timeout=15, allow_agent=False, look_for_keys=False)
sftp = c.open_sftp()
tmp = os.environ.get('TEMP', '/tmp')
frontend_zip = os.path.join(tmp, 'frontend-vue-v3.8.8.zip')
admin_zip = os.path.join(tmp, 'admin-vue-v3.8.8.zip')
remote_frontend_zip = '/tmp/' + os.path.basename(frontend_zip)
remote_admin_zip = '/tmp/' + os.path.basename(admin_zip)
sftp.put(frontend_zip, remote_frontend_zip)
sftp.put(admin_zip, remote_admin_zip)
sftp.close()
for cmd in [
    f'rm -rf /opt/dms/dms-test/frontend-dist/dist && mkdir -p /opt/dms/dms-test/frontend-dist/dist && unzip -q -o {remote_frontend_zip} -d /opt/dms/dms-test/frontend-dist/dist',
    f'rm -rf /opt/dms/dms-test/admin-dist && mkdir -p /opt/dms/dms-test/admin-dist && unzip -q -o {remote_admin_zip} -d /opt/dms/dms-test/admin-dist',
    'docker exec dms-test-frontend sh -c "rm -rf /usr/share/nginx/html/assets /usr/share/nginx/html/index.html /usr/share/nginx/html/admin/*"',
    'docker cp /opt/dms/dms-test/frontend-dist/dist/. dms-test-frontend:/usr/share/nginx/html/',
    'docker cp /opt/dms/dms-test/admin-dist/. dms-test-frontend:/usr/share/nginx/html/admin/',
    'docker restart dms-test-frontend && sleep 3',
    'docker exec dms-test-frontend sh -c "ls /usr/share/nginx/html/assets" | head -5',
]:
    si, so, se = c.exec_command(cmd, timeout=60)
    out = so.read().decode('utf-8', errors='replace').rstrip()
    err = se.read().decode('utf-8', errors='replace').rstrip()
    print(f'>>> {cmd[:120]}')
    print(out)
    if err: print('STDERR:', err)
c.close()
