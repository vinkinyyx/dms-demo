from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
root=Path('dist')
out=Path('dist.zip')
if out.exists(): out.unlink()
with ZipFile(out,'w',ZIP_DEFLATED) as z:
    for p in root.rglob('*'):
        if p.is_file(): z.write(p, p.relative_to(root).as_posix())
print(out.resolve(), out.stat().st_size)
