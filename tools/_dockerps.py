import paramiko
ssh=paramiko.SSHClient(); ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy()); ssh.connect('8.133.193.238', username='root', password='Welcomeyyx0616', timeout=15)
for cmd in ["docker ps --format '{{.Names}}'", "docker ps -a --format '{{.Names}}' | grep -i dms", "ls /opt/dms/dms-test"]:
    stdin,stdout,stderr=ssh.exec_command(cmd, timeout=30)
    print('---',cmd,'---')
    print(stdout.read().decode('utf-8','replace'))
    print(stderr.read().decode('utf-8','replace'))
ssh.close()
