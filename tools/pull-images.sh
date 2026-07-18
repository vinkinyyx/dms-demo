#!/bin/bash
# 尝试从多个镜像源拉 postgres:14-alpine
set +e
for m in dockerpull.org hub.rat.dev docker.1panel.live registry.dockermirror.com; do
  echo "==> Try mirror: $m"
  timeout 45 docker pull ${m}/library/postgres:14-alpine 2>&1 | tail -3
  if docker image inspect ${m}/library/postgres:14-alpine >/dev/null 2>&1; then
    echo "SUCCESS! tagging..."
    docker tag ${m}/library/postgres:14-alpine postgres:14-alpine
    docker rmi ${m}/library/postgres:14-alpine
    break
  fi
done

# 再尝试其他镜像
for img in redis:7-alpine maven:3.9-eclipse-temurin-17 eclipse-temurin:17-jre-alpine; do
  echo "==> pulling $img"
  ok=0
  for m in dockerpull.org hub.rat.dev docker.1panel.live registry.dockermirror.com; do
    src="${m}/library/${img}"
    # 部分镜像不用 library 前缀
    timeout 90 docker pull $src 2>&1 | tail -2
    if docker image inspect $src >/dev/null 2>&1; then
      docker tag $src $img
      docker rmi $src
      ok=1
      break
    fi
    # 试无 library
    src2="${m}/${img}"
    timeout 90 docker pull $src2 2>&1 | tail -2
    if docker image inspect $src2 >/dev/null 2>&1; then
      docker tag $src2 $img
      docker rmi $src2
      ok=1
      break
    fi
  done
  if [ $ok -eq 0 ]; then
    echo "FAILED to pull $img"
  fi
done

echo ""
echo "=== Final images ==="
docker images
