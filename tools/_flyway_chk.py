import paramiko
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', port=22, username='root', password='Welcomeyyx0616', timeout=10, allow_agent=False, look_for_keys=False)
for cmd in [
    "docker exec dms-test-postgres psql -U dms -d dms_test -c \"SELECT version, description, success FROM flyway_schema_history WHERE version >= 'V58' ORDER BY version\" 2>&1 | head -40",
    "docker exec dms-test-postgres psql -U dms -d dms_test -c \"SELECT count(*) AS total, count(DISTINCT page_key) AS pages FROM platform_filter_configs\" 2>&1",
    "docker exec dms-test-postgres psql -U dms -d dms_test -c \"SELECT page_key, count(*) FROM platform_filter_configs GROUP BY page_key ORDER BY page_key\" 2>&1",
    "docker exec dms-test-postgres psql -U dms -d dms_test -c \"SELECT count(*) FROM platform_button_configs\" 2>&1",
]:
    si, so, se = c.exec_command(cmd, timeout=15)
    out = so.read().decode('utf-8', errors='replace').rstrip()
    err = se.read().decode('utf-8', errors='replace').rstrip()
    print(f"--- {cmd} ---")
    print(out)
    if err: print('STDERR:', err)
c.close()
