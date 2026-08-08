import time, requests, sys
for i in range(60):
    try:
        r=requests.get('http://8.133.193.238:8080/actuator/health', timeout=3)
        print(i, r.status_code, r.text)
        if r.ok and 'UP' in r.text: break
    except Exception as e:
        print(i, 'ERR', e)
    time.sleep(2)
else:
    sys.exit(1)
