import paramiko, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', username='root', password='Welcomeyyx0616', timeout=15, allow_agent=False, look_for_keys=False)
for cmd in [
 'ls -la /opt/dms/backend /opt/dms/frontend-vue',
 'cat /opt/dms/backend/Dockerfile 2>/dev/null || true',
 'docker inspect dms-backend --format "{{json .Config.Env}}"',
 'docker inspect dms-backend --format "{{json .HostConfig.Binds}}"',
 'docker logs --tail 30 dms-backend 2>&1'
]:
 print('\n>>>',cmd[:200])
 si,so,se=c.exec_command(cmd,timeout=30)
 print(so.read().decode('utf-8','replace').rstrip())
 err=se.read().decode('utf-8','replace').rstrip()
 if err: print('STDERR:',err)
c.close()
