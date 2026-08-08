import paramiko, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', username='root', password='Welcomeyyx0616', timeout=15, allow_agent=False, look_for_keys=False)
def run(cmd, timeout=60):
 print('\n>>>',cmd[:240])
 si,so,se=c.exec_command(cmd,timeout=timeout)
 print(so.read().decode('utf-8','replace').rstrip())
 err=se.read().decode('utf-8','replace').rstrip()
 if err: print('STDERR:',err[:4000])
run('docker run --rm --entrypoint sh dms-backend:latest -c "unzip -p app.jar BOOT-INF/classes/db/migration/V55__realistic_demo_profile_data.sql | nl -ba | sed -n 65,82p"')
run('grep -n "to_jsonb\|jsonb_build_object\|tier_hit" -C2 backend/src/main/resources/db/migration/V55__realistic_demo_profile_data.sql 2>/dev/null || true')
c.close()
