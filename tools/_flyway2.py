import paramiko
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', port=22, username='root', password='Welcomeyyx0616', timeout=10, allow_agent=False, look_for_keys=False)
for cmd in [
    # flyway_schema_history 表可能在 public 或 dms
    "docker exec dms-test-postgres psql -U dms -d dms_test -c \"\\dt\" 2>&1 | head -30",
    "docker exec dms-test-postgres psql -U dms -d dms_test -c \"SELECT version, description, success FROM flyway_schema_history ORDER BY version DESC LIMIT 10\" 2>&1",
    # 查 layout 接口：products 的 filters 实际数据
    "curl -s -H 'Authorization: Bearer none' http://localhost:8082/api/ui/layout/products 2>&1 | head -10",
    # 查 application yml 中的 schema
    "docker exec dms-test-backend sh -c 'cat /app/BOOT-INF/classes/application-docker-test.yml 2>&1 | head -40'",
]:
    si, so, se = c.exec_command(cmd, timeout=15)
    out = so.read().decode('utf-8', errors='replace').rstrip()
    err = se.read().decode('utf-8', errors='replace').rstrip()
    print(f"--- {cmd} ---")
    print(out)
    if err: print('STDERR:', err)
c.close()
