import paramiko
host='43.128.145.141'; user='ubuntu'; password='Welcomeyyx0616'
client=paramiko.SSHClient(); client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(hostname=host, username=user, password=password, timeout=20)
cmds = [
  "docker exec dms-test-postgres psql -U dms -d dms_test -c \"\\dt\" | grep -Ei 'admin|user|account' || true",
  "docker exec dms-test-postgres psql -U dms -d dms_test -c \"SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name ILIKE '%admin%';\"",
  "docker exec dms-test-postgres psql -U dms -d dms_test -c \"SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name ILIKE '%user%';\""
]
for cmd in cmds:
    print('\n### CMD:', cmd)
    stdin, stdout, stderr = client.exec_command(cmd, timeout=30)
    print(stdout.read().decode('utf-8','replace'))
    err=stderr.read().decode('utf-8','replace')
    if err.strip(): print('ERR:', err)
client.close()
