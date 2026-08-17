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
def connect(n=8):
    for attempt in range(n):
        try:
            raw=socket.create_connection((HOST,22),timeout=15); raw.settimeout(12)
            t=paramiko.Transport(S(raw))
            t.auth_timeout=45; t.banner_timeout=45; t.handshake_timeout=45
            t.start_client(timeout=45)
            try: t.auth_password(USER,PASSWORD)
            except Exception: t.auth_interactive_dumb(USER, lambda *a,**k:[PASSWORD])
            if t.is_authenticated():
                c=paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy()); c._transport=t
                return c
        except Exception as e:
            print('retry',attempt,repr(e)[:90],flush=True); time.sleep(5)
    return None
def quick(cmd, timeout=40):
    c=connect()
    if not c: return None
    try:
        enc=base64.b64encode(cmd.encode()).decode()
        si,so,se=c.exec_command('echo '+enc+' | base64 -d | bash',timeout=timeout)
        out=so.read().decode('utf-8','replace'); err=se.read().decode('utf-8','replace'); code=so.channel.recv_exit_status()
        return out,err,code
    finally:
        try: c.close()
        except Exception: pass
if __name__=='__main__':
    r=quick(sys.argv[1], int(sys.argv[2]) if len(sys.argv)>2 else 40)
    if r is None:
        print('CONNECT_FAILED'); sys.exit(2)
    out,err,code=r
    print(out)
    if err: print('ERR:',err[:800],file=sys.stderr)
    sys.exit(code)
