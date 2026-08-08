import requests
urls = [
 'http://8.133.193.238:8081/login',
 'http://8.133.193.238:8081/',
 'http://8.133.193.238:8081/mobile/login',
 'http://8.133.193.238:8081/admin/',
 'http://8.133.193.238:8083/login',
 'http://8.133.193.238:8083/',
 'http://8.133.193.238:8083/mobile/login',
 'http://8.133.193.238:8083/admin/'
]
for u in urls:
    try:
        r=requests.get(u,timeout=8,allow_redirects=True)
        print(r.status_code, u, r.url, r.text[:30].replace('\n',' '))
    except Exception as e:
        print('ERR',u,e)
