import paramiko, sys, time, os
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
host='8.133.193.238'; user='root'; pwd='Welcomeyyx0616'
local_jar=r'D:\Workspace\TRAE\DMS\backend\target\dms-backend.jar'
c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(host, username=user, password=pwd, timeout=20, allow_agent=False, look_for_keys=False)
def run(cmd, timeout=300):
    print(f'\n>>> {cmd[:200]}')
    si,so,se=c.exec_command(cmd, timeout=timeout)
    out=so.read().decode('utf-8','replace').rstrip()
    err=se.read().decode('utf-8','replace').rstrip()
    if out: print(out)
    if err: print('STDERR:', err[:4000])
    return out, err
run('mkdir -p /opt/dms/backend')
sftp=c.open_sftp()
print('\n>>> uploading jar...')
sftp.put(local_jar, '/opt/dms/backend/app.jar')
sftp.close()
run('ls -lh /opt/dms/backend/app.jar')
dockerfile='''FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl tzdata \\
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \\
    && echo "Asia/Shanghai" > /etc/timezone
COPY app.jar app.jar
ENV JAVA_OPTS="-Xms256m -Xmx1024m -Duser.timezone=Asia/Shanghai"
EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
'''
sftp=c.open_sftp()
with sftp.open('/opt/dms/backend/Dockerfile','w') as f:
    f.write(dockerfile)
sftp.close()
stamp=time.strftime('%Y%m%d-%H%M%S')
run(f'docker tag dms-backend:latest dms-backend:backup-{stamp} 2>/dev/null || true')
run('cd /opt/dms/backend && docker build -t dms-backend:latest .', timeout=600)
run('docker rm -f dms-backend 2>/dev/null || true')
run('docker run -d --name dms-backend --restart unless-stopped --network dms-net -p 8080:8080 '
    '-e TZ=Asia/Shanghai -e DB_HOST=dms-postgres -e DB_PORT=5432 -e DB_NAME=dms -e DB_USERNAME=dms -e DB_USER=dms -e DB_PASSWORD=dms123456 '
    '-e REDIS_HOST=dms-redis -e REDIS_PORT=6379 -e MINIO_ENDPOINT=http://dms-minio:9000 -e MINIO_ACCESS_KEY=minioadmin -e MINIO_SECRET_KEY=minioadmin '
    '-e JAVA_OPTS="-Xms256m -Xmx1024m -Duser.timezone=Asia/Shanghai" dms-backend:latest')
for i in range(30):
    time.sleep(2)
    out,_=run('curl -s -m 3 http://127.0.0.1:8080/actuator/health || true', timeout=10)
    if 'UP' in out:
        print('backend UP after', i+1, 'attempts')
        break
else:
    run('docker logs --tail 120 dms-backend 2>&1', timeout=30)
run('docker ps --filter name=dms-backend --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}"')
c.close()
