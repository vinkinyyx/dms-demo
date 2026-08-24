#!/bin/bash
set -e
TS=$(date +%Y%m%d-%H%M%S)
mkdir -p /opt/dms/backups
cp -a /opt/dms/test/frontend /opt/dms/backups/frontend-$TS
echo "BACKUP=/opt/dms/backups/frontend-$TS"
echo "---SRC-DIST---"
ls /home/ubuntu/fe-build/dist/
echo "---DEST-BEFORE---"
ls /opt/dms/test/frontend/
rm -rf /opt/dms/test/frontend/assets /opt/dms/test/frontend/index.html
cp -a /home/ubuntu/fe-build/dist/index.html /opt/dms/test/frontend/index.html
cp -a /home/ubuntu/fe-build/dist/assets /opt/dms/test/frontend/assets
chown -R root:root /opt/dms/test/frontend/index.html /opt/dms/test/frontend/assets
echo "---DEST-AFTER---"
ls /opt/dms/test/frontend/
echo "---ADMIN-PRESERVED---"
ls /opt/dms/test/frontend/admin/ | head
echo "---VERIFY SERVED FILE---"
grep -o 'index-[A-Za-z0-9_]*\.js' /opt/dms/test/frontend/index.html
