#!/bin/bash
set -e
cd /home/ubuntu/fe-build
rm -rf _patch_extract
mkdir _patch_extract
cd _patch_extract
unzip -o /home/ubuntu/fe-patch-v428b.zip
cp dict.js /home/ubuntu/fe-build/src/utils/dict.js
cp ResourceDetail.vue /home/ubuntu/fe-build/src/views/ResourceDetail.vue
cd /home/ubuntu/fe-build
grep -n "translateEnumTokens" src/utils/dict.js | head -2
grep -n "productMap" src/views/ResourceDetail.vue | head -3
rm -rf dist
npm run build > /tmp/patch-build.log 2>&1
echo "BUILD_EXIT=$?"
ls -la dist/assets/ | grep -E "ResourceDetail|dict|index-" | head -20
TS=$(date +%Y%m%d-%H%M%S)
sudo mkdir -p /opt/dms/backups/frontend-$TS
sudo cp -r /opt/dms/test/frontend/assets /opt/dms/backups/frontend-$TS/assets 2>/dev/null || true
sudo cp /opt/dms/test/frontend/index.html /opt/dms/backups/frontend-$TS/index.html 2>/dev/null || true
sudo rm -rf /opt/dms/test/frontend/assets
sudo cp -r dist/assets /opt/dms/test/frontend/assets
sudo cp dist/index.html /opt/dms/test/frontend/index.html
sudo chown -R root:root /opt/dms/test/frontend/assets /opt/dms/test/frontend/index.html
echo "DEPLOY_DONE"
NEW_INDEX=$(grep -oE 'index-[A-Za-z0-9_-]+\.js' /opt/dms/test/frontend/index.html | head -1)
NEW_RD=$(ls /opt/dms/test/frontend/assets/ | grep ResourceDetail | head -1)
echo "NEW_INDEX=$NEW_INDEX"
echo "NEW_RD=$NEW_RD"
grep -c "全部经销商" /opt/dms/test/frontend/assets/$NEW_RD || true
grep -oE "产品=[^\"<]{0,40}" /opt/dms/test/frontend/assets/dict-*.js 2>/dev/null | head -3 || true
