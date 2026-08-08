import paramiko, sys, time
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', username='root', password='Welcomeyyx0616', timeout=15, allow_agent=False, look_for_keys=False)
def run(cmd, timeout=120):
 print('\n>>>',cmd[:260])
 si,so,se=c.exec_command(cmd,timeout=timeout)
 print(so.read().decode('utf-8','replace').rstrip())
 err=se.read().decode('utf-8','replace').rstrip()
 if err: print('STDERR:',err[:4000])
run('docker rm -f dms-backend-flyway-preview 2>/dev/null || true')
run('docker run -d --name dms-backend-flyway-preview --network dms-net '
    '-e TZ=Asia/Shanghai -e DB_HOST=dms-postgres -e DB_PORT=5432 -e DB_NAME=dms_flyway_preview '
    '-e DB_USERNAME=dms -e DB_USER=dms -e DB_PASSWORD=dms123456 '
    '-e REDIS_HOST=dms-redis -e REDIS_PORT=6379 -e MINIO_ENDPOINT=http://dms-minio:9000 '
    '-e MINIO_ACCESS_KEY=minioadmin -e MINIO_SECRET_KEY=minioadmin '
    '-e SPRING_FLYWAY_ENABLED=true -e SPRING_FLYWAY_BASELINE_ON_MIGRATE=true -e SPRING_FLYWAY_VALIDATE_ON_MIGRATE=false '
    '-e SEED_ENABLED=false -e JAVA_OPTS="-Xms256m -Xmx768m -Duser.timezone=Asia/Shanghai" '
    'dms-backend:latest')
for i in range(45):
    time.sleep(3)
    out,_=run('docker logs --tail 5 dms-backend-flyway-preview 2>&1', timeout=20)
    if 'Started DmsApplication' in out or 'APPLICATION FAILED TO START' in out or 'Migration' in out and 'failed' in out.lower():
        break
run('docker logs --tail 220 dms-backend-flyway-preview 2>&1', timeout=60)
run("docker exec dms-postgres psql -U dms -d dms_flyway_preview -c \"select installed_rank,version,description,success from flyway_schema_history order by installed_rank;\"", timeout=60)
run("docker exec dms-postgres psql -U dms -d dms_flyway_preview -c \"select count(*) as tables from information_schema.tables where table_schema='public';\"")
run("docker exec dms-postgres psql -U dms -d dms_flyway_preview -c \"select to_regclass('public.op_log') as op_log,to_regclass('public.api_http_logs') as api_http_logs,to_regclass('public.api_call_log') as api_call_log,to_regclass('public.operation_log') as operation_log;\"")
c.close()
