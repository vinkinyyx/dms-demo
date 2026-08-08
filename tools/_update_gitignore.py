from pathlib import Path
p=Path('.gitignore')
s=p.read_text(encoding='utf-8')
line='backend/src/main/resources/application-local.yml'
if line not in s:
    s=s.rstrip()+"\n# Local developer profile\n"+line+"\n"
p.write_text(s, encoding='utf-8', newline='\n')
print('updated .gitignore')
