import paramiko, os, time
host='8.133.193.238'; pw='Welcomeyyx0616'
zipf=os.path.join(os.environ['TEMP'],'dms-frontend-v3.8.9.zip')
c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy()); c.connect(host, username='root', password=pw, timeout=15)
sftp=c.open_sftp(); sftp.put(zipf, '/tmp/dms-frontend-v3.8.9.zip'); sftp.close()
for cmd in [
 'rm -rf /opt/dms/dms-test/frontend-dist/dist && mkdir -p /opt/dms/dms-test/frontend-dist/dist && unzip -q -o /tmp/dms-frontend-v3.8.9.zip -d /opt/dms/dms-test/frontend-dist/dist',
 'docker exec dms-test-frontend sh -c "rm -rf /usr/share/nginx/html/assets /usr/share/nginx/html/index.html"',
 'docker cp /opt/dms/dms-test/frontend-dist/dist/. dms-test-frontend:/usr/share/nginx/html/',
 'docker restart dms-test-frontend'
]:
 print('>>>',cmd)
 si,so,se=c.exec_command(cmd, timeout=120)
 print(so.read().decode('utf-8','replace'))
 err=se.read().decode('utf-8','replace')
 if err: print('STDERR',err)
time.sleep(3)
si,so,se=c.exec_command('curl -s -I http://localhost:8083/ | head -5', timeout=10)
print(so.read().decode('utf-8','replace'))
c.close()
