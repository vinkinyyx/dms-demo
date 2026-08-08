import paramiko, sys, time
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', username='root', password='Welcomeyyx0616', timeout=20, allow_agent=False, look_for_keys=False)
def run(cmd, timeout=300):
    print(f'\n>>> {cmd[:260]}')
    si,so,se=c.exec_command(cmd, timeout=timeout)
    out=so.read().decode('utf-8','replace').rstrip()
    err=se.read().decode('utf-8','replace').rstrip()
    if out: print(out)
    if err: print('STDERR:', err[:4000])
    return out, err
run('docker logs --tail 5 dms-backend 2>&1 || true')
run('docker rm -f dms-backend-flyway 2>/dev/null || true')
run('docker rm -f dms-backend 2>/dev/null || true')
run('docker run -d --name dms-backend-flyway --network dms-net -e TZ=Asia/Shanghai '
    '-e DB_HOST=dms-postgres -e DB_PORT=5432 -e DB_NAME=dms -e DB_USERNAME=dms -e DB_USER=dms -e DB_PASSWORD=dms123456 '
    '-e REDIS_HOST=dms-redis -e REDIS_PORT=6379 -e MINIO_ENDPOINT=http://dms-minio:9000 -e MINIO_ACCESS_KEY=minioadmin -e MINIO_SECRET_KEY=minioadmin '
    '-e SPRING_FLYWAY_ENABLED=true -e SPRING_FLYWAY_BASELINE_ON_MIGRATE=true -e SPRING_FLYWAY_VALIDATE_ON_MIGRATE=false -e SEED_ENABLED=false '
    '-e JAVA_OPTS="-Xms256m -Xmx1024m -Duser.timezone=Asia/Shanghai" dms-backend:latest')
for i in range(60):
    time.sleep(3)
    out,_=run('docker logs --tail 10 dms-backend-flyway 2>&1', timeout=20)
    if 'Started DmsApplication' in out or 'APPLICATION FAILED TO START' in out:
        print('flyway startup state after', i+1)
        break
run('docker ps -a --filter name=dms-backend-flyway --format "table {{.Names}}\t{{.Status}}"')
run('docker logs --tail 220 dms-backend-flyway 2>&1', timeout=60)
run("docker exec dms-postgres psql -U dms -d dms -c \"select max(version) as max_version, count(*) as applied, count(*) filter (where success) as success from flyway_schema_history;\"")
run("docker exec dms-postgres psql -U dms -d dms -c \"select count(*) as tables from information_schema.tables where table_schema='public';\"")
c.close()
