import paramiko
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', port=22, username='root', password='Welcomeyyx0616', timeout=10, allow_agent=False, look_for_keys=False)
for cmd in [
    'ls /opt/dms/dms-test/admin-vue 2>&1',
    'docker exec dms-test-backend ls /app 2>&1',
    'docker exec dms-test-backend ls /app/BOOT-INF 2>&1 | head -5',
    'docker exec dms-test-backend sh -c "ls /tmp 2>&1 | head -5"',
    'docker exec dms-test-frontend ls /usr/share/nginx/html 2>&1',
    'docker exec dms-test-frontend sh -c "ls /usr/share/nginx/html/admin 2>&1 | head -5"',
    'docker exec dms-test-frontend sh -c "ls /etc/nginx/conf.d 2>&1"',
]:
    si, so, se = c.exec_command(cmd, timeout=10)
    out = so.read().decode('utf-8', errors='replace').rstrip()
    err = se.read().decode('utf-8', errors='replace').rstrip()
    print(f"--- {cmd} ---")
    print(out)
    if err: print('STDERR:', err)
c.close()
