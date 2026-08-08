import paramiko, sys, time
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', username='root', password='Welcomeyyx0616', timeout=15, allow_agent=False, look_for_keys=False)
def run(cmd, timeout=120):
 print('\n>>>',cmd[:260])
 si,so,se=c.exec_command(cmd,timeout=timeout)
 out=so.read().decode('utf-8','replace').rstrip()
 err=se.read().decode('utf-8','replace').rstrip()
 if out: print(out)
 if err: print('STDERR:',err[:4000])
 return out, err
for i in range(40):
    out,_=run('docker logs --tail 20 dms-backend-flyway-preview 2>&1', timeout=20)
    if 'Started DmsApplication' in out or 'APPLICATION FAILED TO START' in out:
        print('detected startup state after', i+1)
        break
    time.sleep(3)
run('docker ps -a --filter name=dms-backend-flyway-preview --format "table {{.Names}}\t{{.Status}}"')
run('docker logs --tail 260 dms-backend-flyway-preview 2>&1', timeout=60)
run("docker exec dms-postgres psql -U dms -d dms_flyway_preview -c \"select installed_rank,version,description,success from flyway_schema_history order by installed_rank;\"", timeout=60)
run("docker exec dms-postgres psql -U dms -d dms_flyway_preview -c \"select count(*) as tables from information_schema.tables where table_schema='public';\"")
run("docker exec dms-postgres psql -U dms -d dms_flyway_preview -c \"select to_regclass('public.op_log') as op_log,to_regclass('public.api_http_logs') as api_http_logs,to_regclass('public.api_call_log') as api_call_log,to_regclass('public.operation_log') as operation_log;\"")
c.close()
