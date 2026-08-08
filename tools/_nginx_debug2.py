import paramiko, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', username='root', password='Welcomeyyx0616', timeout=15, allow_agent=False, look_for_keys=False)
def run(cmd, timeout=60):
 print('\n>>>',cmd[:300])
 si,so,se=c.exec_command(cmd,timeout=timeout)
 print(so.read().decode('utf-8','replace').rstrip())
 err=se.read().decode('utf-8','replace').rstrip()
 if err: print('STDERR:',err[:4000])
cmds=[
 'curl -i -s -m 10 -X POST -H "Content-Type: application/json" -d \'{"tenantCode":"default","username":"admin","password":"Sh123456"}\' http://127.0.0.1:8081/api/auth/login | head -30',
 'curl -i -s -m 10 -X POST -H "Content-Type: application/json" -d \'{"tenantCode":"default","username":"admin","password":"Sh123456"}\' http://127.0.0.1/api/auth/login | head -30',
 'docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Ports}}" | grep -E "dms-nginx|frontend|backend"',
 'docker exec dms-nginx sh -c "cat /etc/nginx/conf.d/default.conf 2>/dev/null || cat /etc/nginx/nginx.conf"',
 'docker logs --tail 40 dms-nginx 2>&1',
 'docker exec dms-frontend-vue sh -c "wget -S -O- --timeout=5 --post-data=\'{\\\"tenantCode\\\":\\\"default\\\",\\\"username\\\":\\\"admin\\\",\\\"password\\\":\\\"Sh123456\\\"}\' --header=\'Content-Type: application/json\' http://dms-backend:8080/api/auth/login 2>&1 | head -40"'
]
for cmd in cmds: run(cmd)
c.close()
