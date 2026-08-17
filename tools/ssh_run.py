import socket,time,paramiko,sys,base64
HOST='8.133.193.238'; USER='root'; PASSWORD='Welcomeyyx0616'
class S:
    def __init__(s,sock): s.sock=sock; s._closed=False
    def sendall(s,*a,**k): return s.sock.sendall(*a,**k)
    def send(s,*a,**k): return s.sock.send(*a,**k)
    def recv(s,size):
        while not s._closed:
            try: return s.sock.recv(size)
            except socket.timeout: pass
        return b''
    def close(s):
        s._closed=True
        try: s.sock.close()
        except Exception: pass
    def settimeout(s,v): return s.sock.settimeout(v)
    def gettimeout(s): return s.sock.gettimeout()
    def fileno(s): return s.sock.fileno()
    def shutdown(s,*a,**k): return s.sock.shutdown(*a,**k)
def connect(n=12):
    for attempt in range(n):
        try:
            raw=socket.create_connection((HOST,22),timeout=20); raw.settimeout(15)
            t=paramiko.Transport(S(raw))
            t.auth_timeout=60; t.banner_timeout=60; t.handshake_timeout=60
            t.start_client(timeout=60)
            try: t.auth_password(USER,PASSWORD)
            except Exception: t.auth_interactive_dumb(USER, lambda *a,**k:[PASSWORD])
            if t.is_authenticated():
                c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy()); c._transport=t
                return c
        except Exception as e:
            print('retry',attempt,repr(e)[:100],flush=True); time.sleep(8)
    raise RuntimeError('no connect')
c=connect()
def run(cmd,timeout=180):
    enc=base64.b64encode(cmd.encode()).decode()
    remote='echo '+enc+' | base64 -d | bash'
    print('>>>',cmd.split(chr(10))[0][:80],flush=True)
    si,so,se=c.exec_command(remote,timeout=timeout)
    out=so.read().decode('utf-8','replace'); err=se.read().decode('utf-8','replace'); code=so.channel.recv_exit_status()
    print(out,flush=True)
    if err: print('ERR',err[:1500],file=sys.stderr)
    print('[exit',code,']',flush=True); return out,err,code
if __name__=='__main__':
    cmd=sys.argv[1] if len(sys.argv)>1 else 'uptime'
    run(cmd)
    c.close()
