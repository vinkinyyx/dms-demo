from pathlib import Path
s=Path('.memory/layers/layer2-conventions.md').read_text(encoding='utf-8')
start=s.index('## 十八、列表页布局规范')
print(s.find('## 十九', start))
print(s[start:start+500])
