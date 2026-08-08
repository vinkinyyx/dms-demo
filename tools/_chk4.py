import paramiko
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', port=22, username='root', password='Welcomeyyx0616', timeout=10, allow_agent=False, look_for_keys=False)
for cmd in [
    # frontend container contents
    'docker exec dms-test-frontend sh -c "ls -la /usr/share/nginx/html"',
    'docker exec dms-test-frontend sh -c "ls /usr/share/nginx/html/assets"',
    'docker exec dms-test-frontend sh -c "ls /usr/share/nginx/html/admin"',
    # 检查 index.html 是不是新的
    'docker exec dms-test-frontend sh -c "head -10 /usr/share/nginx/html/index.html"',
    'docker exec dms-test-frontend sh -c "head -10 /usr/share/nginx/html/admin/index.html"',
]:
    si, so, se = c.exec_command(cmd, timeout=10)
    out = so.read().decode('utf-8', errors='replace').rstrip()
    err = se.read().decode('utf-8', errors='replace').rstrip()
    print(f"--- {cmd} ---")
    print(out)
    if err: print('STDERR:', err)
c.close()
