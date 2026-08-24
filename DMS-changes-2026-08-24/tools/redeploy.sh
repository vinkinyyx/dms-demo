#!/bin/bash
set -e
SRC=/home/ubuntu/fe-build/dist
DST=/opt/dms/test/frontend
echo "=== new index chunk ==="
grep -o 'index-[A-Za-z0-9_]*\.js' $SRC/index.html
echo "=== deploying ==="
rm -rf $DST/assets $DST/index.html
cp -a $SRC/index.html $DST/index.html
cp -a $SRC/assets $DST/assets
chown -R root:root $DST/index.html $DST/assets
echo "=== dest after ==="
ls $DST
echo "=== verify resolveLookup in served chunk ==="
grep -rl 'resolveLookup' $DST/assets/*.js | head -2
echo "=== verify 全部经销商 in served chunk ==="
grep -rl '全部经销商' $DST/assets/*.js | head -2
echo "=== admin preserved ==="
ls $DST/admin/ | head -3
