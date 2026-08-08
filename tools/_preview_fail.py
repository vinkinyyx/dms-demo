import paramiko, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', username='root', password='Welcomeyyx0616', timeout=15, allow_agent=False, look_for_keys=False)
for cmd in [
 'docker logs dms-backend-flyway-preview 2>&1 | grep -n -A40 -B20 -i "migration\|failed\|exception\|ERROR" | tail -260',
 'docker ps -a --filter name=dms-backend-flyway-preview --format "table {{.Names}}\t{{.Status}}"',
 "docker exec dms-postgres psql -U dms -d dms_flyway_preview -c \"select installed_rank,version,description,success from flyway_schema_history where success=false order by installed_rank;\""
]:
 print('\n>>>',cmd[:240])
 si,so,se=c.exec_command(cmd,timeout=60)
 print(so.read().decode('utf-8','replace').rstrip())
 err=se.read().decode('utf-8','replace').rstrip()
 if err: print('STDERR:',err)
c.close()
