import paramiko, time, os, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
host='8.133.193.238'; user='root'; pw='Welcomeyyx0616'
jar=r'D:\Workspace\TRAE\DMS\backend\target\dms-backend.jar'
zipf=os.path.join(os.environ['TEMP'],'dms-frontend-v3.8.9.zip')
c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy()); c.connect(host, username=user, password=pw, timeout=15, allow_agent=False, look_for_keys=False)
sftp=c.open_sftp()
sftp.put(jar, '/opt/dms/dms-test/app.jar.new')
sftp.put(zipf, '/tmp/dms-frontend-v3.8.9.zip')
sftp.close()
cmds=[
 'docker exec dms-test-backend sh -c "cp /app/app.jar /app/app.jar.bak.$(date +%Y%m%d-%H%M%S)"',
 'docker cp /opt/dms/dms-test/app.jar.new dms-test-backend:/app/app.jar',
 'docker restart dms-test-backend',
 'docker exec dms-test-redis redis-cli EVAL "for _,k in ipairs(redis.call(\'keys\', ARGV[1])) do redis.call(\'del\', k) end return 1" 0 "dms:cfg:*"',
 'rm -rf /opt/dms/dms-test/frontend-dist/dist && mkdir -p /opt/dms/dms-test/frontend-dist/dist && unzip -q -o /tmp/dms-frontend-v3.8.9.zip -d /opt/dms/dms-test/frontend-dist/dist',
 'docker exec dms-test-frontend sh -c "rm -rf /usr/share/nginx/html/assets /usr/share/nginx/html/index.html"',
 'docker cp /opt/dms/dms-test/frontend-dist/dist/. dms-test-frontend:/usr/share/nginx/html/',
 'docker restart dms-test-frontend',
]
for cmd in cmds:
    print('>>>', cmd[:160])
    si,so,se=c.exec_command(cmd, timeout=120)
    print(so.read().decode('utf-8','replace').rstrip())
    err=se.read().decode('utf-8','replace').rstrip()
    if err: print('STDERR:',err)
for i in range(30):
    si,so,se=c.exec_command('curl -s -m 3 http://localhost:8082/actuator/health', timeout=5)
    out=so.read().decode('utf-8','replace')
    if '"UP"' in out:
        print('backend UP after', i, 'tries', out)
        break
    time.sleep(2)
else:
    print('backend not UP')
c.close()
