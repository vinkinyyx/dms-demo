import paramiko, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', username='root', password='Welcomeyyx0616', timeout=15, allow_agent=False, look_for_keys=False)
for cmd in [
 r"docker exec dms-postgres psql -U dms -d dms_flyway_preview -c \"SELECT to_jsonb(CASE WHEN true THEN text 'T3' ELSE text 'T1' END) as j;\"",
 r"docker exec dms-postgres psql -U dms -d dms_flyway_preview -c \"SELECT to_jsonb('T1'::text) as j;\"",
 r"docker exec dms-postgres psql -U dms -d dms_flyway_preview -c \"SELECT pg_typeof(to_jsonb(text 'T1')) as t;\""
]:
 print('\n>>>',cmd)
 si,so,se=c.exec_command(cmd,timeout=20)
 print(so.read().decode('utf-8','replace'))
 err=se.read().decode('utf-8','replace')
 if err: print('STDERR:',err)
c.close()
