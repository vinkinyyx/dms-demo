#!/usr/bin/env bash
# DMS 服务看门狗：巡检容器状态/健康检查/HTTP 入口，异常自动拉起（start/restart/compose 重建）
# 部署：/opt/dms/scripts/service-watchdog.sh，cron 每 2 分钟以 root 执行
# 环境变量覆盖（生产预留）：DMS_ENV / DMS_COMPOSE_DIR / DMS_NAME_PREFIX / DMS_HTTP_BASE
set -u

ENV_NAME="${DMS_ENV:-test}"
COMPOSE_DIR="${DMS_COMPOSE_DIR:-/opt/dms/${ENV_NAME}}"
NAME_PREFIX="${DMS_NAME_PREFIX:-dms-test-}"
HTTP_BASE="${DMS_HTTP_BASE:-http://127.0.0.1}"
CHECK_ORDER="${DMS_CHECK_ORDER:-postgres redis minio backend nginx}"
LOG_DIR="${DMS_LOG_DIR:-/opt/dms/logs/watchdog}"
LOCK_FILE="${DMS_LOCK_FILE:-/run/dms-watchdog.lock}"
RECHECK_MAX=12
RECHECK_INTERVAL=10
RETAIN_DAYS=14

mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/watchdog-$(date +%Y%m%d).log"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >> "$LOG_FILE"; }

exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  log "WARN: 上一轮巡检仍在执行，本轮跳过"
  exit 0
fi

log "=== watchdog 巡检开始 (env=$ENV_NAME, compose=$COMPOSE_DIR) ==="

declare -A SVC_OF
HAS_COMPOSE=0
[ -f "$COMPOSE_DIR/docker-compose.yml" ] && HAS_COMPOSE=1
for short in $CHECK_ORDER; do
  SVC_OF["${NAME_PREFIX}${short}"]="${short}-${ENV_NAME}"
done

container_state() { docker inspect -f '{{.State.Status}}' "$1" 2>/dev/null; }
container_health() { docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$1" 2>/dev/null; }

http_probe() {
  local path="$1" pattern="$2" body
  body=$(curl -fsS --max-time 8 "$HTTP_BASE$path" 2>/dev/null) || return 1
  [ -z "$pattern" ] && return 0
  echo "$body" | grep -q "$pattern"
}

wait_recovered() {
  local name="$1" i=0 st hl
  while [ "$i" -lt "$RECHECK_MAX" ]; do
    sleep "$RECHECK_INTERVAL"
    st=$(container_state "$name"); hl=$(container_health "$name")
    if [ "$st" = "running" ] && { [ "$hl" = "healthy" ] || [ "$hl" = "none" ]; }; then
      return 0
    fi
    i=$((i + 1))
  done
  return 1
}

recover() {
  local name="$1" st svc
  st=$(container_state "$name")
  svc="${SVC_OF[$name]:-}"
  if [ -z "$st" ]; then
    if [ "$HAS_COMPOSE" != "1" ] || [ -z "$svc" ]; then
      log "ERROR: $name 容器已删除且无 compose 服务映射，无法自动重建，需人工介入"
      return 1
    fi
    log "RECOVER: $name 容器不存在，docker compose up -d $svc 重建"
    (cd "$COMPOSE_DIR" && docker compose up -d "$svc") >>"$LOG_FILE" 2>&1
  elif [ "$st" != "running" ]; then
    log "RECOVER: $name 状态=$st，执行 docker start"
    if ! docker start "$name" >>"$LOG_FILE" 2>&1; then
      if [ "$HAS_COMPOSE" = "1" ] && [ -n "$svc" ]; then
        log "RECOVER: docker start 失败，回退 docker compose up -d $svc"
        (cd "$COMPOSE_DIR" && docker compose up -d "$svc") >>"$LOG_FILE" 2>&1
      fi
    fi
  else
    log "RECOVER: $name running 但 health=unhealthy，执行 docker restart"
    docker restart "$name" >>"$LOG_FILE" 2>&1
  fi
}

FAILURES=0
for short in $CHECK_ORDER; do
  name="${NAME_PREFIX}${short}"
  st=$(container_state "$name")
  hl=$(container_health "$name")
  if [ -z "$st" ] || [ "$st" != "running" ] || [ "$hl" = "unhealthy" ]; then
    log "ALERT: $name 异常 (state=${st:-missing}, health=$hl)"
    recover "$name"
    if wait_recovered "$name"; then
      log "OK: $name 已恢复 (state=$(container_state "$name"), health=$(container_health "$name"))"
    else
      log "CRITICAL: $name 自动恢复失败，需人工介入"
      FAILURES=$((FAILURES + 1))
    fi
  else
    log "OK: $name 正常 (state=$st, health=$hl)"
  fi
done

if http_probe "/actuator/health" '"status":"UP"'; then
  log "OK: HTTP /actuator/health = UP"
else
  log "CRITICAL: HTTP /actuator/health 探测失败（backend 或 nginx 代理异常）"
  FAILURES=$((FAILURES + 1))
  nginx_name="${NAME_PREFIX}nginx"
  if [ "$(container_state "$nginx_name")" = "running" ]; then
    log "RECOVER: 重启 $nginx_name 尝试恢复代理"
    docker restart "$nginx_name" >>"$LOG_FILE" 2>&1
    sleep 10
    http_probe "/actuator/health" '"status":"UP"' && log "OK: 重启 nginx 后 /actuator/health 恢复" || log "CRITICAL: 重启 nginx 后仍失败，需人工介入"
  fi
fi

if http_probe "/dms/" ""; then
  log "OK: HTTP /dms/ 入口可达"
else
  log "WARN: HTTP /dms/ 入口探测异常"
  FAILURES=$((FAILURES + 1))
fi

find "$LOG_DIR" -name 'watchdog-*.log' -mtime +"$RETAIN_DAYS" -delete 2>/dev/null

if [ "$FAILURES" -eq 0 ]; then
  log "=== 巡检完成：全部正常 ==="
else
  log "=== 巡检完成：$FAILURES 项未恢复，需人工介入 ==="
fi
exit 0
