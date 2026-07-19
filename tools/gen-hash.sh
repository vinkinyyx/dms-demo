#!/bin/bash
set -e
pip3 install bcrypt 2>&1 | tail -3 || true
python3 -c "
import bcrypt
h = bcrypt.hashpw(b'Sh123456', bcrypt.gensalt(rounds=10)).decode()
print(h)
" > /root/hash.txt
NEW_HASH=$(cat /root/hash.txt)
echo "Hash generated: $NEW_HASH"

docker exec dms-postgres psql -U dms -d dms -c "UPDATE users SET password_hash='$NEW_HASH', must_change_password=false, login_fail_count=0, locked_until=NULL WHERE username='admin';"

echo "=== Login test ==="
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantCode":"default","username":"admin","password":"'"${DMS_PWD:-Sh123456}"'"}'
echo
