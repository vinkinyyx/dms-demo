import paramiko, time
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', port=22, username='root', password='Welcomeyyx0616', timeout=10, allow_agent=False, look_for_keys=False)
for i in range(30):
    si, so, se = c.exec_command('curl -s -m 2 http://localhost:8082/actuator/health 2>&1', timeout=5)
    out = so.read().decode('utf-8', errors='replace').rstrip()
    print(i, out)
    if 'UP' in out: break
    time.sleep(3)
c.close()
