import paramiko
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', port=22, username='root', password='Welcomeyyx0616', timeout=10, allow_agent=False, look_for_keys=False)
for cmd in [
    'ls /opt/dms/dms-test/ 2>&1',
    'ls /opt/dms/dms-test/backend 2>&1',
    'ls /opt/dms/dms-test/frontend-dist 2>&1',
    'ls /opt/dms/dms-test/admin-dist 2>&1',
    'docker inspect dms-test-backend --format "{{.Mounts}}" 2>&1',
]:
    si, so, se = c.exec_command(cmd, timeout=10)
    out = so.read().decode('utf-8', errors='replace').rstrip()
    err = se.read().decode('utf-8', errors='replace').rstrip()
    print(f"--- {cmd} ---")
    print(out)
    if err: print('STDERR:', err)
c.close()
