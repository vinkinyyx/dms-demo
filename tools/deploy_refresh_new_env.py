import paramiko, tarfile, io, os, time, sys
from pathlib import Path
HOST='43.128.145.141'; USER='ubuntu'; PWD='Welcomeyyx0616'
ROOT=Path('.')
front=ROOT/'frontend-vue/dist'; admin=ROOT/'admin-vue/dist'
assert (front/'index.html').exists() and (admin/'index.html').exists()

def tar_bytes(path):
    buf=io.BytesIO()
    with tarfile.open(fileobj=buf,mode='w:gz') as t:
        for p in path.rglob('*'):
            if p.is_file(): t.add(p, arcname=str(p.relative_to(path)).replace('\\','/'))
    return buf.getvalue()
front_tar=tar_bytes(front); admin_tar=tar_bytes(admin)
print('packed frontend',len(front_tar),'admin',len(admin_tar), flush=True)

def run(c,cmd,timeout=300):
    print('\n$',cmd, flush=True)
    _,o,e=c.exec_command(cmd,timeout=timeout)
    out=o.read().decode(errors='replace'); err=e.read().decode(errors='replace'); code=o.channel.recv_exit_status()
    print(out, end='')
    if err.strip(): print('STDERR:',err[:4000], file=sys.stderr)
    if code!=0: raise SystemExit(f'command failed {code}: {cmd}')
    return out

c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(HOST,22,USER,PWD,timeout=15,banner_timeout=20,auth_timeout=20,allow_agent=False,look_for_keys=False)
sftp=c.open_sftp()
for name,data in [('dms-frontend-refresh.tar.gz',front_tar),('dms-admin-refresh.tar.gz',admin_tar)]:
    with sftp.file('/home/ubuntu/'+name,'wb') as f:
        f.set_pipelined(True); f.write(data)
    print('uploaded',name, flush=True)
sftp.close()
stamp=time.strftime('%Y%m%d-%H%M%S')
SUDO='echo Welcomeyyx0616 | sudo -S -p ""'
run(c, f'{SUDO} mkdir -p /opt/dms/backups /opt/dms/test/frontend/admin')
run(c, f'{SUDO} cp -a /opt/dms/test/frontend /opt/dms/backups/frontend-{stamp}')
run(c, f'{SUDO} find /opt/dms/test/frontend -mindepth 1 -maxdepth 1 -exec rm -rf {{}} +')
run(c, f'{SUDO} tar -xzf /home/ubuntu/dms-frontend-refresh.tar.gz -C /opt/dms/test/frontend')
run(c, f'{SUDO} mkdir -p /opt/dms/test/frontend/admin')
run(c, f'{SUDO} tar -xzf /home/ubuntu/dms-admin-refresh.tar.gz -C /opt/dms/test/frontend/admin')
run(c, f'{SUDO} chown -R ubuntu:ubuntu /opt/dms/test/frontend')
run(c, f'{SUDO} docker exec dms-test-nginx nginx -t')
run(c, f'{SUDO} docker exec dms-test-nginx nginx -s reload')
run(c, "grep -oE 'assets/[^\"']+' /opt/dms/test/frontend/index.html | head -8")
run(c, "grep -oE 'assets/[^\"']+' /opt/dms/test/frontend/admin/index.html | head -8")
run(c, 'rm -f /home/ubuntu/dms-frontend-refresh.tar.gz /home/ubuntu/dms-admin-refresh.tar.gz')
c.close()
print('\nDEPLOYED backup=/opt/dms/backups/frontend-%s' % stamp)

