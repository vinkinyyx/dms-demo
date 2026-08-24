#!/bin/bash
set -e
cd /home/ubuntu/fe-build
rm -rf _patch_extract_e
mkdir _patch_extract_e
cd _patch_extract_e
unzip -o /home/ubuntu/fe-patch-v428e.zip
cp dict.js /home/ubuntu/fe-build/src/utils/dict.js
cd /home/ubuntu/fe-build
echo "=== verify source ==="
grep -n "STANDARD" src/utils/dict.js | head -3
rm -rf dist
echo "=== build ==="
npm run build > /tmp/patch-build-e.log 2>&1
echo "BUILD_EXIT=$?"
tail -3 /tmp/patch-build-e.log
TS=$(date +%Y%m%d-%H%M%S)
sudo mkdir -p /opt/dms/backups/frontend-$TS
sudo cp -r /opt/dms/test/frontend/assets /opt/dms/backups/frontend-$TS/assets 2>/dev/null || true
sudo cp /opt/dms/test/frontend/index.html /opt/dms/backups/frontend-$TS/index.html 2>/dev/null || true
sudo rm -rf /opt/dms/test/frontend/assets
sudo cp -r dist/assets /opt/dms/test/frontend/assets
sudo cp dist/index.html /opt/dms/test/frontend/index.html
sudo chown -R root:root /opt/dms/test/frontend/assets /opt/dms/test/frontend/index.html
echo "DEPLOY_DONE"
NEW_DICT=$(ls /opt/dms/test/frontend/assets/ | grep 'dict-' | head -1)
NEW_INDEX=$(grep -oE 'index-[A-Za-z0-9_-]+\.js' /opt/dms/test/frontend/index.html | head -1)
echo "NEW_INDEX=$NEW_INDEX"
echo "NEW_DICT=$NEW_DICT"
