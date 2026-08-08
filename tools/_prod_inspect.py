import paramiko, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', username='root', password='Welcomeyyx0616', timeout=15, allow_agent=False, look_for_keys=False)
for cmd in [
 'hostname; date',
 'docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}"',
 'ls -la /opt/dms | head -50',
 'find /opt/dms -maxdepth 3 -iname "Dockerfile*" -o -iname "app.jar" | head -50'
]:
 print('\n>>>',cmd)
 si,so,se=c.exec_command(cmd,timeout=30)
 print(so.read().decode('utf-8','replace').rstrip())
 err=se.read().decode('utf-8','replace').rstrip()
 if err: print('STDERR:',err)
c.close()
