import paramiko, time
c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy()); c.connect('8.133.193.238', username='root', password='Welcomeyyx0616', timeout=15)
for i in range(12):
    si,so,se=c.exec_command('curl -s -m 5 http://localhost:8082/actuator/health || true', timeout=8)
    out=so.read().decode('utf-8','replace')
    print(i, out)
    if '"UP"' in out: break
    time.sleep(5)
si,so,se=c.exec_command('docker logs --tail 40 dms-test-backend 2>&1', timeout=20)
print(so.read().decode('utf-8','replace'))
c.close()
