import os, zipfile, pathlib
src = pathlib.Path('frontend-vue/dist')
out = pathlib.Path(os.environ['TEMP']) / 'dms-frontend-v3.8.9.zip'
if out.exists(): out.unlink()
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
    for p in src.rglob('*'):
        if p.is_file():
            z.write(p, p.relative_to(src).as_posix())
print(out)
