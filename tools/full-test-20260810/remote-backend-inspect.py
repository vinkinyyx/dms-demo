import paramiko
host='43.128.145.141'; user='ubuntu'; password='Welcomeyyx0616'
client=paramiko.SSHClient(); client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(hostname=host, username=user, password=password, timeout=20)
cmds = [
  "docker exec dms-test-backend sh -lc 'ls -la /app && find /app -maxdepth 2 -type f | head -30'",
  "docker exec dms-test-backend sh -lc 'jcmd 1 VM.system_properties | grep -i spring || true'",
  "docker logs dms-test-backend --tail 80 2>&1 | grep -Ei 'admin|登录|login|password|error|exception' || true"
]
for cmd in cmds:
    print('\n### CMD:', cmd)
    stdin, stdout, stderr = client.exec_command(cmd, timeout=30)
    print(stdout.read().decode('utf-8','replace'))
    err=stderr.read().decode('utf-8','replace')
    if err.strip(): print('ERR:', err)
client.close()
