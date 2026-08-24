#!/bin/bash
set -e
cd /home/ubuntu/fe-build
echo '=== syncing updated source ==='
cp /home/ubuntu/fe-src-update/ResourceDetail.vue /home/ubuntu/fe-build/src/views/ResourceDetail.vue 2>/dev/null || true
echo '=== build ==='
rm -f build2.log build2.done
( npm run build > build2.log 2>&1; echo "DONE_$?" > build2.done ) &
echo "BUILD_PID=$!"
