import requests, sys, re
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
BASE = 'http://8.133.193.238:8083'
# 服务器的资产清单
import subprocess
out = subprocess.run(['python', '-c', '''
import paramiko
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('8.133.193.238', port=22, username='root', password='Welcomeyyx0616', timeout=10, allow_agent=False, look_for_keys=False)
si, so, se = c.exec_command('ls /opt/dms/dms-test/frontend-dist/dist/assets | sort', timeout=10)
print(so.read().decode())
c.close()
'''], capture_output=True, text=True).stdout
print('SERVER assets:')
print(out)
