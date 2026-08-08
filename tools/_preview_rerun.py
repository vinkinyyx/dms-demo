import paramiko, sys, time
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', username='root', password='Welcomeyyx0616', timeout=20, allow_agent=False, look_for_keys=False)
def run(cmd, timeout=300):
    print(f'\n>>> {cmd[:220]}')
    si,so,se=c.exec_command(cmd, timeout=timeout)
    out=so.read().decode('utf-8','replace').rstrip()
    err=se.read().decode('utf-8','replace').rstrip()
    if out: print(out)
    if err: print('STDERR:', err[:4000])
    return out, err
sftp=c.open_sftp(); sftp.put(r'D:\Workspace\TRAE\DMS\backend\target\dms-backend.jar','/opt/dms/backend/app.jar'); sftp.close()
run('cd /opt/dms/backend && docker build -t dms-backend:latest .', timeout=600)
run('docker rm -f dms-backend-flyway-preview 2>/dev/null || true')
run("docker exec dms-postgres psql -U dms -d postgres -c \"SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='dms_flyway_preview' AND pid <> pg_backend_pid();\"")
run("docker exec dms-postgres psql -U dms -d postgres -c 'DROP DATABASE IF EXISTS dms_flyway_preview;'")
run("docker exec dms-postgres psql -U dms -d postgres -c 'CREATE DATABASE dms_flyway_preview OWNER dms;'")
run('gunzip -c /opt/dms/backups/dms_20260808_165335.sql.gz | docker exec -i dms-postgres psql -U dms -d dms_flyway_preview -v ON_ERROR_STOP=1 > /tmp/preview_restore.log 2>&1; tail -10 /tmp/preview_restore.log', timeout=300)
run('docker run -d --name dms-backend-flyway-preview --network dms-net -e TZ=Asia/Shanghai -e DB_HOST=dms-postgres -e DB_PORT=5432 -e DB_NAME=dms_flyway_preview -e DB_USERNAME=dms -e DB_USER=dms -e DB_PASSWORD=dms123456 -e REDIS_HOST=dms-redis -e REDIS_PORT=6379 -e MINIO_ENDPOINT=http://dms-minio:9000 -e MINIO_ACCESS_KEY=minioadmin -e MINIO_SECRET_KEY=minioadmin -e SPRING_FLYWAY_ENABLED=true -e SPRING_FLYWAY_BASELINE_ON_MIGRATE=true -e SPRING_FLYWAY_VALIDATE_ON_MIGRATE=false -e SEED_ENABLED=false -e JAVA_OPTS="-Xms256m -Xmx768m -Duser.timezone=Asia/Shanghai" dms-backend:latest')
for i in range(50):
    time.sleep(3)
    out,_=run('docker logs --tail 8 dms-backend-flyway-preview 2>&1', timeout=20)
    if 'Started DmsApplication' in out or 'APPLICATION FAILED TO START' in out:
        print('startup state after', i+1)
        break
run('docker ps -a --filter name=dms-backend-flyway-preview --format "table {{.Names}}\t{{.Status}}"')
run('docker logs --tail 160 dms-backend-flyway-preview 2>&1', timeout=60)
run("docker exec dms-postgres psql -U dms -d dms_flyway_preview -c \"select max(version) as max_version, count(*) as applied, count(*) filter (where success) as success from flyway_schema_history;\"")
run("docker exec dms-postgres psql -U dms -d dms_flyway_preview -c \"select count(*) as tables from information_schema.tables where table_schema='public';\"")
c.close()
