from pathlib import Path
p=Path('.memory/layers/layer2-conventions.md')
s=p.read_text(encoding='utf-8')
start=s.index('## 十八、列表页布局规范')
section=Path('tools/_section18.md').read_text(encoding='utf-8') if Path('tools/_section18.md').exists() else ''
p.write_text(s[:start].rstrip()+"\n\n"+section+"\n", encoding='utf-8', newline='\n')
