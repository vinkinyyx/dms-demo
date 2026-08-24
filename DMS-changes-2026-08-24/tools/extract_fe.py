import zipfile, os, sys
src = '/home/ubuntu/frontend-src-v428.zip'
dst = '/home/ubuntu/fe-build'
os.system('rm -rf ' + dst)
os.makedirs(dst, exist_ok=True)
z = zipfile.ZipFile(src)
for info in z.infolist():
    name = info.filename.replace('\\', '/')
    if name.endswith('/'):
        os.makedirs(os.path.join(dst, name), exist_ok=True)
        continue
    parts = name.split('/')
    if len(parts) > 1 and parts[0] == 'frontend-vue':
        rel = '/'.join(parts[1:])
    else:
        rel = name
    target = os.path.join(dst, rel)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    if not info.is_dir():
        with z.open(info) as src_f, open(target, 'wb') as out_f:
            out_f.write(src_f.read())
print('extracted to', dst)
print(os.listdir(dst))
