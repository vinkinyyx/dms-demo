# 部署方案：Docker Compose + Seed 测试数据

> 版本 **V3.0**（已完成阿里云演示环境部署）
> 目标：`docker-compose up -d` 一键启动完整 DMS（Nginx + 前端 + 后端 + PostgreSQL 14 + Redis + Mock 服务），首次启动自动执行 SQL 迁移与 Seed 数据生成。
>
> **V3.0 交付范围**：
> - ✅ **本地部署包**（Docker Compose）
> - ✅ **阿里云演示环境**（http://<YOUR_SERVER_IP>/ · dms-backend:2.0.2）
> - 生产环境部署由客户自行完成
>
> 本文档"九、备份 & 迁移到阿里云"章节已作为**实际部署经验**沉淀。

---

## 部署版本变迁

| 版本 | 关键变化 |
|---|---|
| V1.0 | 首版 Docker Compose 本地部署方案 |
| **V2.0** | 阿里云初次部署 · 分层清理磁盘 · 首次演示上线 |
| **V3.0** | 后端镜像升级到 2.0.2 · V8 数据库迁移 · 完成 4/4 容器稳定运行 · 磁盘清理策略沉淀 |



---

## 一、拓扑总览

```
┌─────────────────── Docker Compose Network ────────────────────┐
│                                                                 │
│  [nginx:80/443] ─── 反向代理 + 静态资源                          │
│      │                                                          │
│      ├── /  → web-pc（Vue3/Vite dist）                          │
│      ├── /m → web-h5（Vue3 移动端 dist）                        │
│      └── /api → api-gateway                                     │
│                                                                 │
│  [api-gateway:8080] (SpringBoot 3 或 NestJS)                    │
│      ├─ auth-service / user-service                             │
│      ├─ contract-service                                        │
│      ├─ order-service / promotion-service                       │
│      ├─ inventory-service / sales-service                       │
│      ├─ invoice-service / rma-service                           │
│      ├─ report-service                                          │
│      └─ integration-service（Mock 层）                          │
│                                                                 │
│  [job-worker:8081] 异步任务消费者（导入/导出/PDF/返利/UDI 上报）│
│                                                                 │
│  [postgres:5432] PostgreSQL 14                                  │
│  [redis:6379]    Redis 7（缓存/session/队列）                   │
│  [minio:9000]    MinIO（对象存储）                               │
│  [mock-server:9090] Mock 外部系统（ERP/CA/WMS/HR + 企微 Webhook / 飞书 Webhook / 微信扫码） │
│                                                                 │
│  [seed:oneshot]  一次性容器，执行 db migrate + seed             │
└─────────────────────────────────────────────────────────────────┘
```

## 二、docker-compose.yml 结构（骨架）

```yaml
version: "3.9"

x-common-env: &common-env
  SPRING_PROFILES_ACTIVE: docker
  DB_HOST: postgres
  DB_PORT: 5432
  DB_NAME: dms
  DB_USER: dms
  DB_PASSWORD: ${DB_PASSWORD:-dms123}
  REDIS_HOST: redis
  MINIO_ENDPOINT: http://minio:9000
  MINIO_ACCESS_KEY: dms
  MINIO_SECRET_KEY: dms12345
  MOCK_BASE_URL: http://mock-server:9090
  MOCK_ENABLED: "true"

services:
  nginx:
    image: nginx:1.25-alpine
    ports: ["80:80"]
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./web-pc/dist:/usr/share/nginx/html:ro
      - ./web-h5/dist:/usr/share/nginx/html/m:ro
    depends_on: [api-gateway]

  api-gateway:
    image: dms/api-gateway:1.0.0
    environment:
      <<: *common-env
      WECHAT_APP_ID: ${WECHAT_APP_ID:-mock_appid}
      WECHAT_APP_SECRET: ${WECHAT_APP_SECRET:-mock_secret}
    ports: ["8080:8080"]
    depends_on:
      postgres: {condition: service_healthy}
      redis:    {condition: service_started}
      minio:    {condition: service_started}
    restart: on-failure

  job-worker:
    image: dms/job-worker:1.0.0
    environment: *common-env
    depends_on: [api-gateway]

  postgres:
    image: postgres:14-alpine
    environment:
      POSTGRES_DB: dms
      POSTGRES_USER: dms
      POSTGRES_PASSWORD: ${DB_PASSWORD:-dms123}
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./db/init:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U dms -d dms"]
      interval: 5s
      retries: 10
    ports: ["5432:5432"]

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
    volumes: [redisdata:/data]

  minio:
    image: minio/minio:RELEASE.2026-05-01
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: dms
      MINIO_ROOT_PASSWORD: dms12345
    ports: ["9000:9000", "9001:9001"]
    volumes: [miniodata:/data]

  mock-server:
    image: dms/mock-server:1.0.0
    ports: ["9090:9090"]
    volumes:
      - ./mocks:/app/mocks:ro
      - ./mocks/wechat:/app/mocks/wechat:ro

  seed:
    image: dms/seed:1.0.0
    environment:
      <<: *common-env
      SEED_ENABLED: "${SEED_ENABLED:-true}"
    depends_on:
      postgres: {condition: service_healthy}
    restart: "no"

volumes:
  pgdata: {}
  redisdata: {}
  miniodata: {}
```

## 三、SQL 迁移与初始化

### 3.1 目录结构
```
db/
├── init/
│   ├── 00_extensions.sql       -- create extension pgcrypto; uuid-ossp
│   └── 01_role_grant.sql
├── migrations/
│   ├── V1.0__create_platform_and_rbac.sql
│   ├── V1.1__create_masterdata.sql
│   ├── V1.2__create_contract.sql
│   ├── V1.3__create_order_authorization.sql
│   ├── V1.4__create_inventory_sales.sql
│   ├── V1.5__create_rma_invoice_promotion.sql
│   ├── V1.6__create_report_audit_system.sql
│   ├── V1.7__partitions_inv_txn_audit.sql
│   └── V1.8__mv_indexes.sql
└── seed/
    ├── 01_tenant_and_settings.sql
    ├── 02_dict_and_workflow.sql
    ├── 03_orgs_and_users.sql
    ├── 04_masterdata.sql
    ├── 05_dealers_warehouses.sql
    ├── 06_contracts_authorizations.sql
    ├── 07_products_prices.sql
    ├── 08_orders_and_inventory.sql
    ├── 09_sales_out_facts.sql
    ├── 10_promotions.sql
    └── 99_kpi_and_rebate_previews.sql
```

- 使用 **Flyway** 管理迁移，`seed` 容器执行 `flyway migrate && psql -f seed/*.sql`。
- 分区表初始化脚本每月自动 append 新分区（cron job）。

### 3.2 环境变量控制
- `SEED_ENABLED=true`（默认）→ 执行 seed；
- `SEED_ENABLED=false` → 跳过 seed；
- `RESET_DB=true`（危险）→ drop+recreate schema，仅开发环境用；

## 四、Seed 测试数据规模（D-23）

| 表 | 数量 | 说明 |
|---|---|---|
| tenants | 1 | 默认租户 `default`, industry='medical', timezone Asia/Shanghai |
| org_units | 15 | 总部/华北大区/华东大区/华南大区/西南大区/... 3 级树 |
| roles | 8 | 系统管理员/销售经理/销售/客服/商务/财务/合同专员/经销商管理员 |
| users | 20 | 10 厂商 + 10 经销商，含 admin/Sh123456；admin/Sh123456 固化；10 个厂商用户中 2 个已绑定 wechat_openid 用于测试微信登录 |
| product_categories | 8 | 器械 > 耗材/设备/试剂 |
| products | 200 | 医疗器械 200 条，含 UDI required |
| price_lists | 200 | 每产品一条生效价 |
| hospitals | 100 | 覆盖全国省份 |
| regions | 30 | 国内一级/二级行政区 |
| dealers | 50 | 20 一级 + 30 二级；跨区分布 |
| warehouses | 150 | 每 dealer 主+分+医院仓（3 个）|
| workflows | 5 | 合同/订单/RMA/调整/促销审批流 |
| contract_applications | 30 | 各状态混合 |
| contracts | 25 | 已生效 20 + 已终止 5 |
| authorizations | 500 | 合同下发的自动授权 |
| orders | 500 | 覆盖 8 种状态；含拆单/退回样例 |
| order_lines | 2000 | 每单平均 4 行 |
| receipts | 400 | 已确认 350 + 待接收 50 |
| inventory | 3000 | 各批号/序列号分散 |
| inventory_transactions | 5000 | 6 个月历史流水 |
| stock_moves | 100 | 各仓间移库 |
| sales_outs | 300 | 报台 250 + 红冲 50 |
| sales_out_facts | 800 | 汇总事实表 |
| rma_authorizations | 30 | 授权 |
| rma_orders | 40 | 退换货单 |
| purchase_invoices | 300 | 对应完成订单 |
| promotions | 10 | 全部为 MOQ 或 FULL_REDUCTION 类型；V1 不生成 GIFT/BUNDLE |
| promotion_rules | 20 | |
| audit_logs | 2000 | 各类操作示例 |
| notifications | 100 | 未读/已读混合 |
| rebate_previews | 50 | 每 dealer × 3 个月 |

### 4.1 Seed 生成脚本（示例思路）

- 使用 **Python + Faker + psycopg2**（或 Node.js + @faker-js/faker）；
- 关键约束：主外键、tenant_id 一致、批号/序列号唯一、库存与流水一致；
- 每个产品的 inventory 与 inventory_transactions 保持"总和 = qty"平衡。

```bash
# 伪代码示例
python seed/generate.py \
  --tenant-code default \
  --industry medical \
  --dealers 50 --products 200 --orders 500
```

## 五、Nginx 反向代理示例

```nginx
worker_processes auto;
events { worker_connections 1024; }
http {
  include mime.types;
  gzip on;

  server {
    listen 80;
    server_name _;
    client_max_body_size 30m;

    root /usr/share/nginx/html;
    index index.html;

    location /api/ {
      proxy_pass http://api-gateway:8080;
      proxy_set_header X-Real-IP $remote_addr;
      proxy_set_header Host $host;
      proxy_read_timeout 300s;
    }

    location /m/ {
      alias /usr/share/nginx/html/m/;
      try_files $uri $uri/ /m/index.html;
    }

    location / {
      try_files $uri $uri/ /index.html;
    }
  }
}
```

## 六、Mock Server 设计

- 单独镜像 `dms/mock-server`（Node.js + Express 或 WireMock）；
- 静态路由：`/mocks/erp/products/sync.json`；
- 每个接口默认返回样例；管理员可通过 `/mocks/admin/switch` 切换成功/失败场景以做联调。

## 七、启动与验证步骤

```bash
# 1. 克隆代码 + 进入根目录
git clone <repo> && cd dms

# 2. 复制环境变量
cp .env.example .env

# 2.1 .env.example 示例段（新增微信扫码相关变量）
# ------------------------------------------------
# DB_PASSWORD=dms123
# SEED_ENABLED=true
# # 微信开放平台 / 公众号扫码登录凭证（V1 默认使用 mock 值，本地部署无需真实注册）
# WECHAT_APP_ID=mock_appid          # 微信 AppID；生产环境替换为微信开放平台申请的真实值
# WECHAT_APP_SECRET=mock_secret     # 微信 AppSecret；请妥善保管，勿提交至版本库
# ------------------------------------------------

# 3. 启动全部服务
docker compose up -d

# 4. 观察日志（等待 seed 完成）
docker compose logs -f seed

# 5. 浏览器访问
open http://localhost           # PC 端
open http://localhost/m/        # H5
open http://localhost:9001      # MinIO 控制台
open http://localhost:8080/actuator/health   # 健康检查

# 6. 默认账号
# admin / Sh123456   （首登强制改密）
# D00001_01 / Sh123456  （经销商）
```

## 八、健康检查 & 观测（可选）

- 后端：Spring Actuator `/actuator/health`；
- 数据库：`pg_isready`；
- Redis：`redis-cli PING`；
- MinIO：`/minio/health/live`；
- 前端：`GET /`；

后续可加 Prometheus + Grafana 监控（V1 可选）。

## 九、备份 & 迁移到阿里云

> ⚠️ 本章节为**参考文档**，**不属于 V1 交付范围**。V1 仅交付本地 Docker Compose 部署包，测试/UAT/生产环境的部署由客户自行完成。

- 每日 pg_dump 到 MinIO bucket `backups/`；
- 阿里云迁移路径：
  1. 阿里云 RDS PostgreSQL 14 建库；
  2. 使用 `pg_dumpall` 或 DTS 迁移；
  3. K8s 部署（Helm Chart 后续提供）；
  4. 前端静态资源上 OSS + CDN；
  5. Nginx → SLB。

## 十、V1 决策变更影响（D-24 ~ D-41）

以下为 V1 阶段相对早期方案的关键变更，供实施与联调参考：

| 维度 | V1 决策 | 说明 |
|---|---|---|
| **交付形态** | 仅本地部署 | 仅交付 Docker Compose 本地部署包；测试/UAT/生产由客户自行部署 |
| **移除服务** | 邮件 Mock、短信 Mock | 已从 `mock-server` 拓扑与配置中移除相关引用 |
| **新增服务** | 企微 Webhook Mock、飞书 Webhook Mock、微信扫码 Mock | 通过 `mocks/wechat` 目录挂载；企微/飞书通过 Webhook 路由回放消息 |
| **促销降级** | Seed 仅生成 MOQ 与 FULL_REDUCTION | 不再生成 GIFT / BUNDLE 类型促销；`promotions` 表 10 条全部落在这两种类型 |
| **认证方案** | 无 SSO，新增微信扫码流程 | 移除 SSO Mock；`api-gateway` 新增 `WECHAT_APP_ID` / `WECHAT_APP_SECRET` 环境变量；本地默认 `mock_appid` / `mock_secret` |
| **上云计划** | 暂不属于 V1 交付 | 第九章"备份 & 迁移到阿里云"仅作参考保留，不作为验收标准 |

—— END ——


---

## 附录 · 阿里云实战部署经验（v3.0 沉淀）

### A. 服务器规格

| 项 | 值 |
|---|---|
| 主机 | `<YOUR_SERVER_IP>` |
| SSH | root / <YOUR_SSH_PASSWORD> |
| OS | Ubuntu (阿里云 ECS) |
| 磁盘 | **40 GB · 演示环境已足够** |
| 内存 | ~1.6 GB（后端限制 -Xmx512m） |

### B. 部署拓扑（实际生产版本）

```
┌──── 阿里云 ECS <YOUR_SERVER_IP> ────┐
│                                  │
│  Docker Network: dms-net (bridge)│
│  ┌────────────────────────────┐  │
│  │ dms-nginx (nginx:1.25)     │──┼── Public :80
│  │ dms-backend (2.0.2)        │──┼── Public :8080
│  │ dms-postgres (14-alpine)   │──┼── Public :5432
│  │ dms-redis (7-alpine)       │  │ Internal :6379
│  └────────────────────────────┘  │
│                                  │
│  Volumes:                        │
│  ├─ dms_pgdata (76 MB)          │
│  └─ dms_redisdata               │
└──────────────────────────────────┘
```

### C. 部署命令（阿里云版）

```bash
# 拉取阿里云镜像加速版本
bash /root/dms/pull-images.sh

# 一键部署
bash /root/dms/deploy-full.sh

# 构建后端最新版
cd /root/dms/backend
docker build -t dms-backend:2.0.2 .

# 运行后端
docker rm -f dms-backend
docker run -d --name dms-backend \
  --network dms-net --restart unless-stopped \
  -e TZ=Asia/Shanghai \
  -e SPRING_PROFILES_ACTIVE=docker \
  -e DB_HOST=dms-postgres -e DB_PORT=5432 \
  -e DB_NAME=dms -e DB_USER=dms -e DB_PASSWORD=dms123456 \
  -e SPRING_DATA_REDIS_HOST=dms-redis \
  -e SPRING_DATA_REDIS_PORT=6379 \
  -e JWT_SECRET='dms-super-secret-key-please-change-in-prod-1234567890abcdef' \
  -e JAVA_OPTS='-Xms256m -Xmx512m -XX:+UseG1GC' \
  -p 8080:8080 -m 768m \
  dms-backend:2.0.2
```

### D. 冒烟测试

```bash
bash /root/dms/test-batch1.sh          # P0 · 15 用例
bash /root/dms/test-batch2.sh          # P1 · 10 用例
bash /root/dms/test-batch3.sh          # P2 · 10 用例
bash /root/dms/test-batch4.sh          # P3 · 12 用例
bash /root/dms/test-po-lowcode.sh      # v3.0 · 14 用例
bash /root/dms/test-order-create.sh    # 下单页 · 3 用例
```

**共 64 项测试用例，全部通过。**

### E. 磁盘清理策略（重要 · 演示环境防止磁盘满）

**日常清理脚本** `tools/clean-disk.sh`：
- `docker builder prune -af`（构建缓存最大头，可释 5-15 GB）
- `docker image prune -f`（孤儿镜像）
- `docker container prune -f` `volume prune -f`
- `apt-get clean`
- `journalctl --vacuum-size=100M`
- `truncate -s 0` 所有容器 json.log
- `find /tmp -mtime +7 -delete`

**深度清理脚本** `tools/deep-clean.sh`：
- 上述所有操作
- 额外清理无关目录（hermes-agent / OMS_Knowledge / huggingface / playwright / medical-ai）

**首次清理释放**：从 94% 用量 → 56% 用量，共 12 GB。

**cron 定时任务建议**（可选，未启用）：
```cron
0 2 * * 0 /root/clean-disk.sh > /var/log/clean-disk.log 2>&1
```

### F. 常见坑与解决

| # | 问题 | 解决 |
|---|---|---|
| 1 | Hibernate native SQL `?N::jsonb` 语法冲突 | 用命名参数 + `CAST(:x AS jsonb)` |
| 2 | 位置参数 `?N` 被引用两次抛异常 | 拆成不同编号的独立参数 |
| 3 | 事务级联失败 | 每个查询用 `@Transactional(propagation=REQUIRES_NEW)` |
| 4 | 邮件 Token GET 端点被 JWT 拦截 | SecurityConfig 白名单 |
| 5 | 数据库列名与代码不一致（`ref_doc_id` vs `ref_id` 等） | 严格按 Flyway 迁移中的列名 |
| 6 | 磁盘满导致 Postgres 挂掉 | 清理旧镜像 + 定期清理构建缓存 |
| 7 | Docker 容器 JSON 日志无限增长 | 配置 `--log-opt max-size=100m max-file=5` |

### G. 数据库直连（供开发调试用）

```
Host:     <YOUR_SERVER_IP>
Port:     5432
Database: dms
User:     dms
Password: dms123456
```

**推荐工具**：DBeaver / pgAdmin4 / DataGrip

⚠️ **注意**：演示环境的弱密码 `dms123456` 不适合生产。生产必须改密码 + 限制阿里云安全组入方向源 IP。

### H. 演示账号

```
租户代码：default
账号：admin
密码：Sh123456
```


