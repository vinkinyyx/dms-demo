import paramiko, sys
host='43.128.145.141'; user='ubuntu'; password='Welcomeyyx0616'
client=paramiko.SSHClient(); client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(hostname=host, username=user, password=password, timeout=20)
cmds = [
  'hostname && whoami && pwd',
  'ls -la /opt/dms/test || true',
  'ps -ef | grep -E "java|dms" | grep -v grep || true',
  'systemctl status dms-test --no-pager -l | head -80 || true',
  'docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}" || true'
]
for cmd in cmds:
    print('\n### CMD:', cmd)
    stdin, stdout, stderr = client.exec_command(cmd, timeout=30)
    out=stdout.read().decode('utf-8','replace')
    err=stderr.read().decode('utf-8','replace')
    print(out)
    if err.strip(): print('ERR:', err)
client.close()
