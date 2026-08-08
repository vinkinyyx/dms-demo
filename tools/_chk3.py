import paramiko
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', port=22, username='root', password='Welcomeyyx0616', timeout=10, allow_agent=False, look_for_keys=False)
for cmd in [
    'ls -la /opt/dms/dms-test/admin-dist 2>&1 | head -20',
    'ls /opt/dms/dms-test/admin-dist/assets 2>&1 | head -5',
    'ls /opt/dms/dms-test/frontend-dist/dist 2>&1 | head -10',
    'ls /opt/dms/dms-test/frontend-dist/dist/assets 2>&1 | head -5',
    'docker ps --format "{{.Names}} {{.Status}}" | grep -E "dms-test"',
    'docker logs dms-test-backend --tail 30 2>&1',
    'docker logs dms-test-frontend --tail 20 2>&1',
    'curl -s -m 3 http://localhost:8082/actuator/health 2>&1',
    'curl -s -m 3 http://localhost:8083/ 2>&1 | head -5',
]:
    si, so, se = c.exec_command(cmd, timeout=10)
    out = so.read().decode('utf-8', errors='replace').rstrip()
    err = se.read().decode('utf-8', errors='replace').rstrip()
    print(f"--- {cmd} ---")
    print(out)
    if err: print('STDERR:', err)
c.close()
