import paramiko
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', port=22, username='root', password='Welcomeyyx0616', timeout=15, allow_agent=False, look_for_keys=False)
sftp = c.open_sftp()
# 1) 上传新 jar
sftp.put(r'D:\Workspace\TRAE\DMS\backend\target\dms-backend.jar', '/opt/dms/dms-test/admin-vue/app.jar.new')
print('jar uploaded')
sftp.close()
for cmd in [
    'docker exec dms-test-backend sh -c "cp /app/app.jar /app/app.jar.bak.$(date +%Y%m%d-%H%M%S)"',
    'docker cp /opt/dms/dms-test/admin-vue/app.jar.new dms-test-backend:/app/app.jar',
    'docker restart dms-test-backend',
    'docker exec dms-test-redis redis-cli EVAL "for _,k in ipairs(redis.call(\\"keys\\", ARGV[1])) do redis.call(\\"del\\", k) end return 1" 0 "dms:cfg:*"',
]:
    si, so, se = c.exec_command(cmd, timeout=30)
    out = so.read().decode('utf-8', errors='replace').rstrip()
    err = se.read().decode('utf-8', errors='replace').rstrip()
    print(f'>>> {cmd[:120]}')
    print(out)
    if err: print('STDERR:', err)
import time
for i in range(20):
    si, so, se = c.exec_command('curl -s -m 2 http://localhost:8082/actuator/health 2>&1', timeout=5)
    out = so.read().decode('utf-8', errors='replace').rstrip()
    if 'UP' in out:
        print('backend UP after', i, 'tries:', out)
        break
    time.sleep(2)
c.close()
