from pathlib import Path
root=Path('D:/Workspace/TRAE/DMS/backend/src/main/java')
for p in root.rglob('*.java'):
    b=p.read_bytes()
    if b.startswith(b'\xef\xbb\xbf'):
        p.write_bytes(b[3:])
        print('stripped', p)
