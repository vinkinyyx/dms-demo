import paramiko, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', username='root', password='Welcomeyyx0616', timeout=15, allow_agent=False, look_for_keys=False)
for cmd in ['docker logs --tail 160 dms-backend 2>&1 | grep -n -A30 -B10 "7096c3549fb74b25b246a8cf84083ca3\\|Exception\\|ERROR" | tail -220']:
 print('\n>>>',cmd)
 si,so,se=c.exec_command(cmd,timeout=30)
 print(so.read().decode('utf-8','replace').rstrip())
 err=se.read().decode('utf-8','replace').rstrip()
 if err: print('STDERR:',err)
c.close()
