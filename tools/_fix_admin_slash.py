from pathlib import Path
root=Path(r'D:\Workspace\TRAE\DMS')
repls={
 root/'docs/09_测试报告/测试报告.md': [
   ('http://8.133.193.238:8083/admin（平台后台）','http://8.133.193.238:8083/admin/（平台后台）'),
   ('http://8.133.193.238:8083/admin）','http://8.133.193.238:8083/admin/）'),
 ],
 root/'docs/11_平台后台/00_开发交接指引.md': [
   ('http://8.133.193.238:8083/admin |','http://8.133.193.238:8083/admin/ |'),
 ]
}
for p, pairs in repls.items():
    s=p.read_text(encoding='utf-8')
    for old,new in pairs:
        s=s.replace(old,new)
    p.write_text(s,encoding='utf-8',newline='\n')
    print('updated',p)
