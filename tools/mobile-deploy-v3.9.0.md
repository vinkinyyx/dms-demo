# DMS 移动端 v3.9.0 部署记录

**日期**: 2026-08-04
**变更**: 移动端精简（销售场景）
**工具**: paramiko (替代 MCP ssh-manager)
**后端改动**: 无

## 涉及容器

| 环境 | 容器 | 端口 | 宿主机路径 | nginx 代理目标 |
|------|------|------|-----------|----------------|
| 测试 | dms-test-frontend | 8083 | /opt/dms/dms-test/frontend-dist | 172.17.0.1:8082 (dms-test-backend) |
| 生产 | dms-frontend-vue  | 8081 | /opt/dms/frontend-vue | dms-backend:8080 |

## 1. 本地打包

```powershell
$version = "v3.9.0"
$tmp = Join-Path $env:TEMP "dms-frontend-$version"
Copy-Item -Path "D:\Workspace\TRAE\DMS\frontend-vue\dist\*" -Destination $tmp -Recurse -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory($tmp, "$env:TEMP\dms-frontend-$version.zip", "Optimal", $false)
# 产出: %TEMP%\dms-frontend-v3.9.0.zip  ~1MB
```

## 2. 测试环境 (8083) - dist-only 模式

测试容器 dms-frontend-test:latest 内 dist 通过 docker cp 注入，无需重建镜像。

```bash
# a) 上传 zip
# b) 备份 dist -> dist.bak.<timestamp>
# c) unzip zip -d dist
# d) docker cp dist/. dms-test-frontend:/usr/share/nginx/html/
# e) docker restart dms-test-frontend
```

执行脚本: `tools\_deploy_test.py`

## 3. 生产环境 (8081) - 重建镜像

生产容器 dms-frontend-vue 镜像内 dist 是构建期定格的，必须 `docker build`。

```bash
# a) 上传 zip
# b) 备份镜像 -> dms-frontend-vue:backup-<timestamp>
# c) 解压到 /opt/dms/frontend-vue/dist/
# d) 写 /opt/dms/frontend-vue/Dockerfile.dist
#    FROM nginx:1.25-alpine
#    COPY dist /usr/share/nginx/html
#    COPY nginx-vue.conf /etc/nginx/nginx.conf
# e) docker rmi dms-frontend-vue:latest
# f) docker builder prune -af
# g) docker build -f Dockerfile.dist -t dms-frontend-vue:latest .
# h) docker rm -f dms-frontend-vue
# i) docker run -d --name dms-frontend-vue --restart unless-stopped -p 8081:80 --network dms-net dms-frontend-vue:latest
```

执行脚本: `tools\_deploy_prod.py`

## 4. 验证 (E2E)

9/9 接口全 200:

| 接口 | 8083 | 8081 |
|------|------|------|
| GET /api/auth/login (admin/Sh123456) | 200 | 200 |
| GET /api/dashboard/kpi?period=today | 200 | 200 |
| GET /api/dashboard/kpi?period=month | 200 | 200 |
| GET /api/orders?page=1&size=5 | 200 | 200 |
| GET /api/surgery-reports?page=1&size=5 | 200 | 200 |
| GET /api/dashboard/sales-trend | 200 | 200 |
| GET /api/dashboard/top-dealers?period=month | 200 | 200 |
| GET /api/lookups/dealers?limit=5 | 200 | 200 |
| GET /api/lookups/hospitals?limit=5 | 200 | 200 |
| GET /api/lookups/warehouses?limit=5 | 200 | 200 |

iPhone UA 模拟: `/`, `/mobile/login`, `/mobile/home`, `/assets/M*.js` 全部 200。

## 5. 回滚

```bash
# 生产:
docker rm -f dms-frontend-vue
docker run -d --name dms-frontend-vue --restart unless-stopped -p 8081:80 --network dms-net dms-frontend-vue:backup-<timestamp>

# 测试:
docker cp /opt/dms/dms-test/frontend-dist/dist.bak.<timestamp>/. dms-test-frontend:/usr/share/nginx/html/
docker restart dms-test-frontend
```

## 6. 后续清理

- 测试: `dist.bak.20260804-003243` 保留 7 天
- 生产: `dms-frontend-vue:backup-20260804-003340` 保留 7 天
- 本次部署的 zip 保留在 `tools/` 与 `%TEMP%`

## 7. 铁律自检

- [x] 源码 100% 替换 (dist grep 命中 MHome/MOrderDetail/MSurgeryReports/MSurgeryReportDetail)
- [x] 旧镜像删除 + 构建缓存清理 (docker builder prune -af)
- [x] 必须校验最终产物 (docker exec 验证 dist 内容 + curl chunk 200)
- [x] 临时压缩包保留 (tools/ + %TEMP%)
- [x] 替换目录用 rm + mkdir (清空 dist 后 unzip)
- [x] Nginx 代理指向正确环境 (测试 -> 172.17.0.1:8082, 生产 -> dms-backend:8080)
- [x] 部署后端到端验证 (9/9 API + 主页 + chunk + UA 模拟)
- [x] Docker 构建缓存清理