import paramiko
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', port=22, username='root', password='Welcomeyyx0616', timeout=10, allow_agent=False, look_for_keys=False)
for cmd in [
    'docker exec dms-test-redis redis-cli --scan --pattern "dms:cfg:*" | head -5',
    'docker exec dms-test-redis redis-cli EVAL "for _,k in ipairs(redis.call(\\"keys\\", ARGV[1])) do redis.call(\\"del\\", k) end return 1" 0 "dms:cfg:*"',
    'docker exec dms-test-redis redis-cli --scan --pattern "dms:cfg:*" | head -3',
]:
    si, so, se = c.exec_command(cmd, timeout=10)
    out = so.read().decode('utf-8', errors='replace').rstrip()
    err = se.read().decode('utf-8', errors='replace').rstrip()
    print(f"--- {cmd} ---")
    print(out)
    if err: print('STDERR:', err)
c.close()
