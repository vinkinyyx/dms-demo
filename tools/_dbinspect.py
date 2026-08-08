import paramiko, textwrap
host='8.133.193.238'; user='root'; pw='Welcomeyyx0616'
cmd = r"""
docker exec dms-test-postgres psql -U dms -d dms_test -P pager=off -c "\d+ platform_filter_configs" -c "\d+ platform_button_configs" -c "SELECT page_key, scope, button_key, label, permission_code, visible, sort_order FROM platform_button_configs WHERE page_key IN ('orders','dealer-profile') AND tenant_id IS NULL ORDER BY scope, sort_order;" -c "SELECT page_key, filter_key, label, component_type FROM platform_filter_configs WHERE page_key='orders' ORDER BY sort_order;"
"""
ssh=paramiko.SSHClient(); ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy()); ssh.connect(host, username=user, password=pw, timeout=15)
stdin,stdout,stderr=ssh.exec_command(cmd, timeout=60)
print(stdout.read().decode('utf-8','replace'))
err=stderr.read().decode('utf-8','replace')
print(err)
ssh.close()
