import requests, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
r = requests.get('http://8.133.193.238:8083/', timeout=10)
print(r.text)
