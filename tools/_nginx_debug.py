import paramiko, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', username='root', password='Welcomeyyx0616', timeout=15, allow_agent=False, look_for_keys=False)
def run(cmd, timeout=60):
 print('\n>>>',cmd[:260])
 si,so,se=c.exec_command(cmd,timeout=timeout)
 print(so.read().decode('utf-8','replace').rstrip())
 err=se.read().decode('utf-8','replace').rstrip()
 if err: print('STDERR:',err[:4000])
for cmd in [
 'curl -i -s -m 5 http://127.0.0.1:8080/actuator/health | head -20',
 'curl -i -s -m 10 -X POST -H "Content-Type: application/json" -d \'{"tenantCode":"default","username":"admin","password":"Sh123456"}\' http://127.0.0.1:8080/api/auth/login | head -40',
 'docker exec dms-frontend-vue sh -c "getent hosts dms-backend; wget -S -O- --timeout=5 http://dms-backend:8080/actuator/health 2>&1 | head -30"',
 'docker exec dms-frontend-vue cat /etc/nginx/nginx.conf',
 'docker logs --tail 80 dms-frontend-vue 2>&1',
 'docker logs --tail 80 dms-nginx 2>&1'
]: run(cmd)
c.close()
