#!/bin/bash
# =====================================================================
# DMS 阿里云完整部署脚本 v2（含前端 Nginx + 后端 + PG + Redis）
# =====================================================================
set +e

echo "========================================"
echo "DMS 阿里云单机部署 v2 (前后端)"
echo "========================================"

# ---------- 拉取 nginx 镜像 ----------
if ! docker image inspect nginx:1.25-alpine >/dev/null 2>&1; then
  echo "==> Pulling nginx from hub.rat.dev..."
  docker pull hub.rat.dev/library/nginx:1.25-alpine
  docker tag hub.rat.dev/library/nginx:1.25-alpine nginx:1.25-alpine
  docker rmi hub.rat.dev/library/nginx:1.25-alpine 2>/dev/null
fi

# ---------- 网络 ----------
docker network inspect dms-net >/dev/null 2>&1 || docker network create dms-net

# ---------- PostgreSQL ----------
if ! docker ps --format '{{.Names}}' | grep -q '^dms-postgres$'; then
  echo "[1/4] Starting PostgreSQL..."
  docker rm -f dms-postgres 2>/dev/null
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
    postgres -c shared_buffers=128MB -c max_connections=50 -c effective_cache_size=384MB -c work_mem=4MB
  echo "Waiting for postgres..."
  for i in {1..30}; do
    if docker exec dms-postgres pg_isready -U dms -d dms >/dev/null 2>&1; then break; fi
    sleep 2
  done
  echo "PostgreSQL ready"
else
  echo "[1/4] PostgreSQL already running"
fi

# ---------- Redis ----------
if ! docker ps --format '{{.Names}}' | grep -q '^dms-redis$'; then
  echo "[2/4] Starting Redis..."
  docker rm -f dms-redis 2>/dev/null
  docker run -d \
    --name dms-redis \
    --network dms-net \
    --restart unless-stopped \
    -p 6379:6379 \
    -v dms_redisdata:/data \
    -m 192m \
    redis:7-alpine \
    redis-server --maxmemory 128mb --maxmemory-policy allkeys-lru --save ""
  echo "Redis ready"
else
  echo "[2/4] Redis already running"
fi

# ---------- Backend ----------
echo "[3/4] Building & starting backend..."
cd /root/dms/backend
docker rm -f dms-backend 2>/dev/null
if ! docker image inspect dms-backend:1.0.0 >/dev/null 2>&1; then
  echo "  building image (5-10 min)..."
  docker build -t dms-backend:1.0.0 . 2>&1 | tee /root/dms/build.log | tail -3
  if ! docker image inspect dms-backend:1.0.0 >/dev/null 2>&1; then
    echo "❌ Build failed. Last lines of build log:"
    tail -30 /root/dms/build.log
    exit 1
  fi
fi
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

echo "Waiting for backend to be healthy..."
for i in {1..90}; do
  if curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo "  Backend is ready"
    break
  fi
  sleep 2
done

# ---------- Nginx 前端 ----------
echo "[4/4] Starting Nginx frontend..."
docker rm -f dms-nginx 2>/dev/null
docker run -d \
  --name dms-nginx \
  --network dms-net \
  --restart unless-stopped \
  -p 80:80 \
  -v /root/dms/frontend/index.html:/usr/share/nginx/html/index.html:ro \
  -v /root/dms/frontend/nginx.conf:/etc/nginx/nginx.conf:ro \
  -m 64m \
  nginx:1.25-alpine

sleep 3

echo ""
echo "========================================"
echo "🎉 部署完成！"
echo "========================================"
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
echo ""
echo "🌐 Public URL:"
echo "  首页:        http://<REPLACE_ME_SERVER_IP>/"
echo "  API 文档:    http://<REPLACE_ME_SERVER_IP>/api/swagger-ui.html"
echo "  健康检查:    http://<REPLACE_ME_SERVER_IP>/api/actuator/health"
echo ""
echo "🔑 默认账号: admin / Sh123456"
echo ""
echo "❤️  最终健康检查:"
curl -s http://localhost:8080/actuator/health || echo "  Backend 尚未启动完毕，请稍等 30s"
echo ""
echo "🖥️  内存使用:"
free -h | head -2
echo ""
echo "📦 磁盘使用:"
df -h / | tail -1

