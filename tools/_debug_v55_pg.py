import paramiko, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', username='root', password='Welcomeyyx0616', timeout=15, allow_agent=False, look_for_keys=False)
def run(cmd, timeout=180):
 print('\n>>>',cmd[:260])
 si,so,se=c.exec_command(cmd,timeout=timeout)
 print(so.read().decode('utf-8','replace').rstrip())
 err=se.read().decode('utf-8','replace').rstrip()
 if err: print('STDERR:',err[:4000])
run('docker cp dms-v55-debug:/tmp/V55.sql /tmp/V55.sql && docker cp /tmp/V55.sql dms-postgres:/tmp/V55.sql')
run("docker exec dms-postgres psql -U dms -d dms_flyway_preview -v ON_ERROR_STOP=1 -f /tmp/V55.sql", timeout=180)
run("docker exec dms-postgres psql -U dms -d dms_flyway_preview -c \"select tier_hit, pg_typeof(tier_hit) from rebate_previews where tenant_id='11111111-1111-1111-1111-111111111111' limit 3;\"")
c.close()
