import paramiko, sys, time
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', username='root', password='Welcomeyyx0616', timeout=20, allow_agent=False, look_for_keys=False)
def run(cmd, timeout=180):
 print(f'\n>>> {cmd[:260]}')
 si,so,se=c.exec_command(cmd, timeout=timeout)
 out=so.read().decode('utf-8','replace').rstrip()
 err=se.read().decode('utf-8','replace').rstrip()
 if out: print(out)
 if err: print('STDERR:',err[:4000])
 return out,err
run('docker rm -f dms-backend 2>/dev/null || true')
run('docker run -d --name dms-backend --restart unless-stopped --network dms-net -p 8080:8080 '
    '-e TZ=Asia/Shanghai -e DB_HOST=dms-postgres -e DB_PORT=5432 -e DB_NAME=dms -e DB_USERNAME=dms -e DB_USER=dms -e DB_PASSWORD=dms123456 '
    '-e REDIS_HOST=dms-redis -e REDIS_PORT=6379 -e MINIO_ENDPOINT=http://dms-minio:9000 -e MINIO_ACCESS_KEY=minioadmin -e MINIO_SECRET_KEY=minioadmin '
    '-e JAVA_OPTS="-Xms256m -Xmx1024m -Duser.timezone=Asia/Shanghai" dms-backend:latest')
for i in range(45):
 time.sleep(2)
 out,_=run('curl -s -m 3 http://127.0.0.1:8080/actuator/health || true', timeout=10)
 if 'UP' in out:
  print('backend UP after', i+1, 'attempts')
  break
else:
 run('docker logs --tail 120 dms-backend 2>&1', timeout=30)
run('docker rm -f dms-backend-flyway dms-backend-flyway-preview dms-v55-debug 2>/dev/null || true')
run('docker ps --filter name=dms-backend --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}"')
run("docker exec dms-postgres psql -U dms -d postgres -c \"SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='dms_flyway_preview' AND pid <> pg_backend_pid();\"")
run("docker exec dms-postgres psql -U dms -d postgres -c 'DROP DATABASE IF EXISTS dms_flyway_preview;'")
c.close()
