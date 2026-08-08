import paramiko
c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy()); c.connect('8.133.193.238', username='root', password='Welcomeyyx0616', timeout=15)
for cmd in ['docker logs --tail 120 dms-test-backend 2>&1 | grep -A30 -B10 "Exception\\|ERROR\\|tenant-ui" || true']:
 si,so,se=c.exec_command(cmd, timeout=30)
 print(so.read().decode('utf-8','replace'))
 print(se.read().decode('utf-8','replace'))
c.close()
