import requests, json
BASE='http://8.133.193.238:8081'
for payload in [
 {'username':'admin','password':'admin123'},
 {'username':'platform','password':'admin123'},
 {'username':'admin','password':'Sh123456'}
]:
 r=requests.post(BASE+'/api/admin/auth/login',json=payload,timeout=10)
 print(payload, r.status_code, r.text[:1000])
