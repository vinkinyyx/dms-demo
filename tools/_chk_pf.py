import paramiko
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', port=22, username='root', password='Welcomeyyx0616', timeout=10, allow_agent=False, look_for_keys=False)
for cmd in [
    "docker exec dms-test-postgres psql -U dms -d dms_test -c \"SELECT page_key, tenant_type, count(*) FROM platform_page_configs GROUP BY page_key, tenant_type ORDER BY page_key, tenant_type\" 2>&1",
]:
    si, so, se = c.exec_command(cmd, timeout=15)
    out = so.read().decode('utf-8', errors='replace').rstrip()
    print(out)
c.close()
