# DMS 项目环境信息 & 部署快照

> **最后同步时间：2026-07-18**
> **服务器版本：dms-backend:2.0.2 · nginx:1.25 · postgres:14 · redis:7**
> **本地目录：d:\Workspace\TRAE\DMS**

---

## 一、部署环境速览

### 阿里云服务器
| 项目 | 值 |
|---|---|
| 主机 | `<YOUR_SERVER_IP>` |
| SSH 用户 | `root` |
| SSH 密码 | `<YOUR_SSH_PASSWORD>` |
| OS | Ubuntu (aliyun ECS) |
| 磁盘 | 40 GB · 当前用 56% · **剩 17 GB** |
| 内存 | ~1.6 GB |

### 访问入口
| 端口 | 用途 | URL |
|---|---|---|
| **80** | Nginx + 前端 | http://<YOUR_SERVER_IP>/ |
| **8080** | 后端 API 直连 | http://<YOUR_SERVER_IP>:8080/actuator/health |
| **5432** | PostgreSQL 直连 | jdbc:postgresql://<YOUR_SERVER_IP>:5432/dms |
| 6379 | Redis（内网） | 仅容器间访问 |

### 数据库连接信息
| 参数 | 值 |
|---|---|
| Host | `<YOUR_SERVER_IP>` |
| Port | `5432` |
| Database | `dms` |
| User | `dms` |
| Password | `dms123456` |
| Schema | `public` |

> ⚠️ 演示环境专用密码。生产环境请务必更改。

### 演示账号
```
租户代码：default
账号：admin
密码：Sh123456
```

---

## 二、Docker 容器结构

```
docker network: dms-net  (bridge)
├─ dms-nginx      nginx:1.25-alpine    Port 80          → 静态前端 + /api 反代到 8080
├─ dms-backend    dms-backend:2.0.2    Port 8080        → Spring Boot 3.x + Java 17
├─ dms-postgres   postgres:14-alpine   Port 5432        → 业务数据库
└─ dms-redis      redis:7-alpine       Port 6379(内网) → 缓存 + Session + Approval Token

Volumes:
├─ dms_pgdata     PostgreSQL 持久化 (76 MB)
└─ dms_redisdata  Redis 持久化
```

---

## 三、镜像版本历史

| 版本 | 交付内容 |
|---|---|
| 1.0.0 - 1.1.1 | 初次上线：MVP 基础功能 |
| 1.2.0 - 1.2.2 | P0 批次：合同 PDF/短信签章/ERP归档/UDI追溯/引用检查/移动端 |
| 1.3.0 | P1 批次：批量导入/异步导出/综合看板/待办列表/合同扩展/收货撤销 |
| 1.4.0 - 1.4.1 | P2 批次：邮件Token审批/超时提醒/缓存监视/RBAC矩阵/流程配置 |
| 1.5.0 - 1.5.1 | P3 批次：集成 Mock/促销审批/返利引擎/借货单/Excel导出/SEED开关 |
| 1.6.0 | 大屏下单页 + 实时库存查询接口 |
| **2.0.0 - 2.0.2** | **重构版：采购销售拆分 + 状态驱动按钮 + 中文详情视图 + 库存联动 + 低代码字段配置 + 字典维护** |

---

## 四、本地目录结构

```
d:\Workspace\TRAE\DMS
├── backend/                       Spring Boot 后端源码
│   ├── src/main/java/com/dms/     87 个 Java 文件
│   │   ├── auth/                  认证登录
│   │   ├── authz/                 授权（合同授权/临时授权）
│   │   ├── common/                通用组件
│   │   ├── config/                Spring 配置
│   │   ├── contract/              合同 + 合同申请
│   │   ├── home/                  首页统计
│   │   ├── integration/           外部集成 Mock（ERP/WMS/HR/UDI/CA）
│   │   ├── inventory/             库存 + 收货/调拨/调整/盘点/追溯
│   │   ├── invoice/               销售/采购发票
│   │   ├── masterdata/            主数据（经销商/产品/仓库/医院/区域）
│   │   ├── notification/          通知
│   │   ├── order/                 订单（销售 + 采购拆分）
│   │   ├── promotion/             促销与返利
│   │   ├── rbac/                  权限
│   │   ├── report/                报表
│   │   ├── rma/                   退货
│   │   ├── sales/                 销售出库
│   │   ├── security/              JWT + 拦截器
│   │   ├── system/                系统管理（Dashboard/Dict/FormConfig/Ops）
│   │   ├── tenant/                租户
│   │   └── user/                  用户
│   ├── src/main/resources/db/migration/  Flyway 迁移（V1-V8）
│   ├── Dockerfile
│   ├── Dockerfile.aliyun          国内镜像加速版
│   └── pom.xml
├── frontend/                      前端静态文件
│   ├── index.html                 登录页
│   ├── workspace.html             业务工作台（销售/采购/库存/合同 45 KB）
│   ├── admin.html                 后台管理（系统/字典/字段配置 48 KB）
│   ├── order-create.html          大屏下单页（销售/采购双模式 28 KB）
│   ├── home.html
│   ├── dms-lib.js                 前端共享库（Picker/statusBadge/labelOf）
│   ├── dms.css                    共享样式
│   ├── nginx.conf                 Nginx 配置
│   └── mobile/                    移动端 H5（7 个页面）
├── mocks/                         外部系统响应 Mock JSON
│   ├── ca/                        电子签章
│   ├── erp/                       ERP 系统
│   ├── feishu/                    飞书
│   ├── wechat/                    微信
│   └── wecom/                     企业微信
├── docs/                          全套文档
│   ├── 01_PRD/
│   ├── 02_需求分析/
│   ├── 03_设计图/                 UI 高保真图
│   ├── 04_功能详细设计/
│   ├── 05_数据库设计/
│   │   ├── 数据库设计_Part1.md
│   │   ├── 数据库设计_Part2.md
│   │   └── schema_export/         ⭐ 阿里云 PG 数据库导出（本次新增）
│   │       ├── dms_schema.sql     纯 Schema（157 KB · 66 张表）
│   │       ├── dms_data.sql       纯数据（2.2 MB）
│   │       └── dms_full.sql       Schema + 数据（2.3 MB · 一键还原）
│   ├── 06_API设计/
│   ├── 07_部署方案/
│   ├── 08_补充线框图/
│   ├── 09_测试报告/
│   │   ├── 交付最终报告.md
│   │   ├── UI优化交付报告_v1.1.md
│   │   ├── UI设计规范_v1.1.md
│   │   ├── 全需求补齐交付报告_v2.0.md
│   │   ├── 阿里云部署报告.md
│   │   └── 采购销售拆分+低代码交付报告_v3.0.md  ⭐ 本次新增
│   └── DMS环境信息.md              ⭐ 本文档（新增）
├── tools/                         工具与部署脚本
│   ├── plink.exe / pscp.exe       PuTTY SSH 客户端
│   ├── maven/                     本地 Maven 3.9
│   ├── deploy-aliyun.sh           初次部署脚本
│   ├── deploy-aliyun-full.sh      完整版部署（含健康检查）
│   ├── deploy-full.sh             改进版部署
│   ├── pull-images.sh             阿里云镜像仓库拉取
│   ├── clean-disk.sh              日常清理脚本
│   ├── deep-clean.sh              深度清理脚本
│   ├── fix-admin.sh               管理员密码修复
│   ├── fix-admin-password.sh
│   ├── test-batch1~4.sh           P0-P3 批次冒烟测试
│   ├── test-order-create.sh       下单页测试
│   ├── test-po-lowcode.sh         采购/低代码测试
│   ├── ui-e2e-test.sh             端到端 UI 测试
│   ├── e2e-test.sh
│   ├── diag-api.sh
│   ├── verify-api.sh
│   ├── daemon.json                Docker daemon 配置
│   ├── gen-hash.sh                密码哈希生成
│   └── downloads/                 依赖下载
├── docker/                        Docker Compose 相关
├── docker-compose.yml             本地开发用
├── docker-compose.aliyun.yml      阿里云生产版（等同服务器上的 docker-compose.yml）
├── CHANGELOG.md
├── README.md                      项目总说明
└── dms-deploy.zip                 首次部署压缩包
```

---

## 五、代码同步策略

**本地 ↔ 服务器 已 100% 对齐**（2026-07-18 验证）：

| 位置 | 大小 | 说明 |
|---|---|---|
| 本地 `backend/src/` | 87 个 Java 文件 | ✅ 与服务器一致 |
| 本地 `frontend/` | 8 个 PC + 7 个 H5 页面 | ✅ 与服务器一致 |
| 本地 `mocks/` | 9 个 JSON | ✅ 与服务器一致 |
| 本地 `tools/*.sh` | 20 个脚本 | ✅ 全部同步 |

**每次改动流程**：
```
本地编辑代码 → pscp 上传服务器 → docker build → docker run → 测试
```

---

## 六、数据库表清单（66 张）

### 平台层
- `tenants` 租户
- `users` 用户 · `user_wechat_bindings` 微信绑定
- `roles` 角色 · `resources` 资源 · `strategies` 策略
- `user_roles` `role_strategies` `strategy_resources` 关联表
- `data_scopes` 数据权限
- `audit_logs` 审计日志（分区表 by 月）
- `dict_types` 字典分类 · `dict_items` 字典条目
- `system_settings` 系统设置
- `form_configs` 低代码字段配置 ⭐
- `workflows` 工作流 · `workflow_nodes` · `approval_tasks` · `approval_history`
- `notifications` 通知
- `async_jobs` 异步任务

### 主数据
- `org_units` 组织架构
- `regions` 区域
- `product_categories` 产品分类 · `products` 产品
- `dealers` 经销商 · `dealer_addresses` 地址 · `dealer_kpi_snapshots` 画像
- `hospitals` 医院/终端
- `warehouses` 仓库

### 合同与授权
- `contracts` 合同 · `contract_templates` 模板 · `contract_diff` 差异
- `contract_applications` 合同申请
- `contract_signatures` 签名 · `contract_attachments` 附件
- `authorizations` 授权 · `temp_authorizations` 临时授权

### 订单与销售
- `orders` **销售订单** · `order_lines` 明细
- `order_status_history` 状态历史 · `order_promotion_hits` 命中促销
- `purchase_orders` **采购订单** ⭐ · `purchase_order_lines` 明细 ⭐
- `sales_outs` 销售出库 · `sales_out_lines` · `sales_out_facts` 数据事实
- `distribution_shipments` 分销发货 · `distribution_lines`
- `sales_invoices` 销售发票 · `purchase_invoices` 采购发票
- `rma_orders` 退货单 · `rma_authorizations` 退货授权
- `price_lists` 价格表

### 库存
- `inventory` 库存主表
- `inventory_transactions` 库存事务日志（分区表 by 月）
- `receipts` 收货单 · `receipt_lines`
- `stock_moves` 调拨 · `stock_move_lines`
- `inventory_adjustments` 库存调整 · `adjustment_lines`
- `stocktakes` 盘点 · `stocktake_lines`
- `loans` 借货单 · `loan_lines`

### 促销与报表
- `promotions` 促销 · `promotion_rules` 规则 · `promotion_status_log` 状态日志

⭐ = 本次 V8 迁移新增

---

## 七、数据库还原方法（本地）

如果要在本地 Docker 起一个同款 PG 并导入服务器数据：

```powershell
# 1. 启动本地 Postgres 容器
docker run -d --name pg-dms-local -e POSTGRES_USER=dms -e POSTGRES_PASSWORD=dms123456 -e POSTGRES_DB=dms -p 15432:5432 postgres:14-alpine

# 2. 等待启动
Start-Sleep -Seconds 5

# 3. 导入完整备份（含 schema + 数据）
docker exec -i pg-dms-local psql -U dms -d dms < "d:\Workspace\TRAE\DMS\docs\05_数据库设计\schema_export\dms_full.sql"

# 4. 连接测试
docker exec -it pg-dms-local psql -U dms -d dms -c "SELECT COUNT(*) FROM orders;"
```

或使用 DBeaver / pgAdmin 连接后 File → Import SQL Script → 选 `dms_full.sql`。

---

## 八、常用命令速查

### 服务器操作
```bash
# 连接
ssh root@<YOUR_SERVER_IP>  # 密码：<YOUR_SSH_PASSWORD>

# 查看容器
docker ps
docker logs --tail 100 dms-backend
docker logs --tail 50 dms-postgres

# 磁盘检查
df -h /
du -sh /root/dms/

# 数据库
docker exec -it dms-postgres psql -U dms -d dms

# 清理磁盘
bash /root/clean-disk.sh    # 日常清理（Docker 缓存/日志）
bash /root/deep-clean.sh    # 深度清理（含无关目录）
```

### 本地部署新版本
```powershell
# 上传单个文件
& tools\pscp.exe -pw <YOUR_SSH_PASSWORD> -batch backend\src\...\XxxController.java root@<YOUR_SERVER_IP>:/root/dms/backend/src/...

# 构建 + 重启
& tools\plink.exe -ssh -pw <YOUR_SSH_PASSWORD> -batch root@<YOUR_SERVER_IP> "cd /root/dms/backend && docker build -t dms-backend:X.Y.Z . && docker rm -f dms-backend && docker run -d ..."

# 冒烟测试
& tools\plink.exe -ssh -pw <YOUR_SSH_PASSWORD> -batch root@<YOUR_SERVER_IP> "bash /root/dms/test-po-lowcode.sh"
```

---

## 九、当前已知问题

| # | 问题 | 状态 |
|---|---|---|
| 1 | 磁盘 40GB · 已用 56% | ✅ 演示环境足够 |
| 2 | `dms123456` 弱密码公网暴露 | ⚠️ 演示可接受，生产必改 |
| 3 | 前端表单还没读 form_configs 动态渲染 | ⏸️ 待开发（下个迭代） |
| 4 | 销售订单 `/ship` 出库发货接口未实现 | ⏸️ 待开发 |
| 5 | 附件/PDF/图片本机存储 | ⏸️ 演示够用，生产需接 OSS |

---

## 十、下次开发前建议做的事

1. **本地起 PostgreSQL**（用 dms_full.sql 导入），可完全离线开发
2. **配置 DBeaver 连接**（用上面的连接信息）方便查库
3. 决定下一批要做什么：
   - 前端动态表单渲染（真正体现低代码价值）
   - 销售出库/发货流程完善
   - 附件上传接 OSS
   - 或其他新需求

