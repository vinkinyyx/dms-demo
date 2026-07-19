#!/bin/bash
# =====================================================================
# DMS 阿里云部署脚本（不依赖 docker-compose）
# 用 docker run 手动编排三个容器
# =====================================================================
set -e

echo "========================================"
echo "DMS 阿里云单机部署"
echo "========================================"

# 网络
docker network inspect dms-net >/dev/null 2>&1 || docker network create dms-net

# ---------- PostgreSQL ----------
echo "[1/3] Starting PostgreSQL..."
docker rm -f dms-postgres 2>/dev/null || true
docker run -d \
  --name dms-postgres \
  --network dms-net \
  --restart unless-stopped \
  -e POSTGRES_DB=dms \
  -e POSTGRES_USER=dms \
  -e POSTGRES_PASSWORD=<REPLACE_ME_DB_PASSWORD> \
  -e TZ=Asia/Shanghai \
  -p 5432:5432 \
  -v dms_pgdata:/var/lib/postgresql/data \
  -m 512m \
  postgres:14-alpine \
  postgres \
    -c shared_buffers=128MB \
    -c max_connections=50 \
    -c effective_cache_size=384MB \
    -c work_mem=4MB

echo "Waiting for postgres to be ready..."
for i in {1..30}; do
  if docker exec dms-postgres pg_isready -U dms -d dms > /dev/null 2>&1; then
    echo "PostgreSQL is ready"
    break
  fi
  sleep 2
done

# ---------- Redis ----------
echo "[2/3] Starting Redis..."
docker rm -f dms-redis 2>/dev/null || true
docker run -d \
  --name dms-redis \
  --network dms-net \
  --restart unless-stopped \
  -p 6379:6379 \
  -v dms_redisdata:/data \
  -m 192m \
  redis:7-alpine \
  redis-server --maxmemory 128mb --maxmemory-policy allkeys-lru --save ""

sleep 2
echo "Redis started"

# ---------- Backend build & run ----------
echo "[3/3] Building backend Docker image (this may take 5-10 minutes)..."
cd /root/dms/backend
docker build -t dms-backend:1.0.0 . 2>&1 | tail -20

echo "Starting backend container..."
docker rm -f dms-backend 2>/dev/null || true
docker run -d \
  --name dms-backend \
  --network dms-net \
  --restart unless-stopped \
  -e TZ=Asia/Shanghai \
  -e SPRING_PROFILES_ACTIVE=docker \
  -e DB_HOST=dms-postgres \
  -e DB_PORT=5432 \
  -e DB_NAME=dms \
  -e DB_USER=dms \
  -e DB_PASSWORD=<REPLACE_ME_DB_PASSWORD> \
  -e SPRING_DATA_REDIS_HOST=dms-redis \
  -e SPRING_DATA_REDIS_PORT=6379 \
  -e JWT_SECRET="<REPLACE_ME_JWT_SECRET>" \
  -e WECHAT_APP_ID=mock_appid \
  -e WECHAT_APP_SECRET=mock_secret \
  -e SEED_ENABLED=true \
  -e JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -Duser.timezone=Asia/Shanghai" \
  -p 8080:8080 \
  -m 768m \
  dms-backend:1.0.0

echo ""
echo "========================================"
echo "All containers started"
echo "========================================"
sleep 3
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
echo ""
echo "Waiting for backend to be ready (may take ~60s)..."
for i in {1..60}; do
  if curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo ""
    echo "✅ Backend is ready!"
    curl -s http://localhost:8080/actuator/health
    echo ""
    echo ""
    echo "Access URLs:"
    echo "  Swagger:  http://<REPLACE_ME_SERVER_IP>:8080/swagger-ui.html"
    echo "  Health:   http://<REPLACE_ME_SERVER_IP>:8080/actuator/health"
    echo "  Login:    POST http://<REPLACE_ME_SERVER_IP>:8080/auth/login"
    echo "  Account:  admin / Sh123456"
    exit 0
  fi
  sleep 2
done
echo "⚠️  Backend not ready after 120s. Check: docker logs dms-backend"

