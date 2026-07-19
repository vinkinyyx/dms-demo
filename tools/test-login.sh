docker exec dms-postgres psql -U dms -d dms -c "SELECT username, LEFT(password_hash, 20) AS hash, status, tenant_id FROM users WHERE username='admin';"
echo "==="
docker exec dms-postgres psql -U dms -d dms -c "SELECT code, name FROM tenants;"
echo "=== try login with tenantCode ==="
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantCode":"default","username":"admin","password":"'"${DMS_PWD:-Sh123456}"'"}'
echo
echo "=== try login without tenantCode ==="
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"'"${DMS_PWD:-Sh123456}"'"}'
echo
echo "=== backend logs (last 30) ==="
docker logs --tail 30 dms-backend
