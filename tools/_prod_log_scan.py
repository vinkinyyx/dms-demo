import paramiko, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', username='root', password='Welcomeyyx0616', timeout=15, allow_agent=False, look_for_keys=False)
for cmd in [
 'docker logs --since 5m dms-backend 2>&1 | grep -E "ERROR|Exception|Caused by" | tail -80 || true',
 'docker logs --since 5m dms-frontend-vue 2>&1 | grep -E "error|emerg|alert" | tail -40 || true',
 'docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep -E "dms-backend|dms-frontend-vue|dms-postgres|dms-redis|dms-minio"',
 "docker exec dms-postgres psql -U dms -d dms -c \"select max(version) as max_version,count(*) as applied from flyway_schema_history; select count(*) as tables from information_schema.tables where table_schema='public';\""
]:
 print('\n>>>',cmd[:240])
 si,so,se=c.exec_command(cmd,timeout=30)
 print(so.read().decode('utf-8','replace').rstrip())
 err=se.read().decode('utf-8','replace').rstrip()
 if err: print('STDERR:',err)
c.close()
