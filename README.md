# 通用 DMS 经销商管理系统 — 项目文档索引

> 版本：**V3.0**（2026-07-18）
> 说明：本目录汇集 DMS 项目从需求梳理 → 架构设计 → 数据库 → API → 部署 → **生产运行**的全部产出。本 README 是入口。
> ⚠️ 查看 [CHANGELOG.md](./CHANGELOG.md) 了解全部版本历史 | [DMS 环境信息](./docs/DMS环境信息.md) 查看部署与访问详情

## 🚀 快速访问（阿里云已部署）

| 用途 | URL / 命令 |
|---|---|
| 业务工作台 | http://<YOUR_SERVER_IP>/ |
| 后台管理 | http://<YOUR_SERVER_IP>/admin.html |
| 大屏销售下单 | http://<YOUR_SERVER_IP>/order-create.html?mode=sales |
| 大屏采购下单 | http://<YOUR_SERVER_IP>/order-create.html?mode=purchase |
| Swagger API | http://<YOUR_SERVER_IP>/swagger-ui.html |
| 移动端 H5 | http://<YOUR_SERVER_IP>/mobile/login.html |
| **演示账号** | 租户 `default` / 账号 `admin` / 密码 `Sh123456` |
| 数据库直连（可选） | `jdbc:postgresql://<YOUR_SERVER_IP>:5432/dms` · 用户 `dms` / 密码 `dms123456` |

## 📦 版本状态

| 里程碑 | 版本 | 说明 |
|---|---|---|
| 需求梳理 | v1.0 | PRD + 用户故事 + 41 项决策 |
| 全需求补齐 | v2.0 | P0-P3 · 38 项功能一次交付 |
| **业务升级** | **v3.0** | 采购销售拆分 + 状态驱动按钮 + 中文详情 + 库存联动 + 低代码字段配置 |

---


## 一、项目背景一句话

面向 **医疗器械** 为核心（快消/零售可复用）的**经销商全生命周期管理平台**，覆盖 **合同 / 进销存 / 促销 / 报表画像** 四大主域，采用**多租户** 架构（V1 本地部署 → 未来阿里云 SaaS），支持 PC + H5 移动端。

## 二、V1 关键决策速览（合并 D-01~D-41）

### 首轮决策（D-01~D-23）
| # | 决策项 | 决议 |
|---|---|---|
| D-01 | MVP 范围 | PRD 全量模块一次性上线 |
| D-02 | 主行业 | 医疗器械（强合规） |
| D-03 | 外部集成 | 全部 Mock/桩 |
| D-04 | 移动端 | 全量 H5 适配 |
| D-05 | 二级经销商 | 一级必入，二级可选 |
| D-06 | 价格 | 随物料主数据维护 |
| D-07 | 授权终止 | 无窗口期，即封锁 |
| D-10 | 返利 | 3–5 分段预置公式 |
| D-11/18 | 部署/多租户 | 本地→阿里云；V1 就启用 tenant_id |
| D-13 | 交付 | Docker Compose 一键启动 |
| D-14 | 数据库 | PostgreSQL 14+ |
| D-15 | KPI 阈值 | 默认（库存/临期/审批时长）|
| D-16 | 金额 | 含税 |
| D-17 | 编号 | 默认前缀 + YYYYMMDD + 序号 |
| D-19 | 主数据初始化 | 双通道 + 厂商审核 |
| D-20 | 敏感字段 | 仅密码 bcrypt 加密 |
| D-21 | 审批代理 | 交给外部 OA |
| D-22 | 主数据同步 | 仅手工触发 |
| D-23 | 测试数据 | 默认生成全量 Seed |

### 二轮决策变更（D-24~D-41）⭐
| # | 决策项 | 决议 |
|---|---|---|
| D-24 | 团队/工期 | **15+ 人 / 3-4 个月** |
| D-25 | 品牌视觉 | 使用组件库默认主题（Element Plus / Vant） |
| D-26 | 交付环境 | **仅本地部署（Docker Compose）** |
| D-27 | 默认超管 | 固定 admin / Sh123456 |
| **D-28** | **促销降级** | **V1 只做满减 + 起订量**（删除满赠、组合销售） |
| D-29 | UDI | 可开关，V1 不真实上报监管 |
| D-30 | 电子签章 | Mock 按 e签宝 API 契约 |
| D-31 | ERP | 通用 REST，不绑定厂商 |
| **D-32** | **删 SSO** | V1 仅账号密码登录 |
| **D-33** | **通知渠道** | 站内 + 企微/飞书 Webhook（无邮件短信） |
| D-34 | 报表 | 固定 10 类 + T+1 |
| D-35 | 权限 | RBAC + 行级（不做字段级） |
| **D-36** | **H5 登录** | 微信扫码 + 首次绑定 DMS 账号 |
| D-37 | 多语言 | 中文 + 预留 i18n |
| D-38 | 主题 | 亮色 + 租户可改主色 |
| D-39 | 审计 | Excel 导出 + 3 年 + MinIO 冷存 |
| D-40 | 性能 | PRD 默认（500 并发 / 50 TPS） |
| D-41 | 交付方式 | 代码 + 培训 + 手册（不做灰度试点） |

## 三、文档目录

```
DMS/
├── README.md                                   ← 本索引
├── CHANGELOG.md                                ← 全部版本历史
├── docker-compose.yml                          ← 本地开发用
├── docker-compose.aliyun.yml                   ← 阿里云生产版（等同服务器 /root/dms/docker-compose.yml）
├── backend/                                    ← Spring Boot 后端源码
│   ├── src/main/java/com/dms/                  ← 87 个 Java 文件
│   ├── src/main/resources/db/migration/        ← V1-V8 Flyway 迁移
│   ├── Dockerfile / Dockerfile.aliyun
│   └── pom.xml
├── frontend/                                   ← 前端静态文件
│   ├── index.html workspace.html admin.html order-create.html home.html
│   ├── dms-lib.js dms.css nginx.conf
│   └── mobile/                                 ← 移动端 H5 7 张页面
├── mocks/                                      ← 外部系统 Mock JSON（CA/ERP/WeChat/WeCom/Feishu）
├── tools/                                      ← 部署脚本 + Maven + PuTTY
│   ├── plink.exe pscp.exe                      ← SSH 客户端
│   ├── maven/                                  ← 本地 Maven 3.9
│   ├── deploy-*.sh                             ← 部署脚本（4 个）
│   ├── test-*.sh                               ← 冒烟测试脚本（10+ 个）
│   ├── clean-disk.sh deep-clean.sh             ← 磁盘清理
│   └── fix-admin*.sh                           ← 管理员密码修复
└── docs/
    ├── DMS环境信息.md                          ⭐ 环境与访问详情（v3.0 新增）
    ├── 01_PRD/                                产品需求文档（原始）
    ├── 02_需求分析/                            94 条 V1 用户故事 + 41 项决策
    ├── 03_设计图/                              UI 高保真图 7 张
    ├── 04_功能详细设计/                        16 大模块的输入/校验/状态/输出/异常
    ├── 05_数据库设计/
    │   ├── 数据库设计_Part1.md                 前 34 张表
    │   ├── 数据库设计_Part2.md                 后 32 张表 + 索引/字典
    │   └── schema_export/                     ⭐ 阿里云 PG 完整导出（v3.0 新增）
    │       ├── dms_schema.sql                  纯 Schema（157 KB · 66 张表）
    │       ├── dms_data.sql                    纯数据（2.2 MB · seed + 演示数据）
    │       └── dms_full.sql                    Schema + 数据（一键 psql 还原）
    ├── 06_API设计/                             20 组 REST 接口 + 示例
    ├── 07_部署方案/
    │   └── 部署方案_DockerCompose与Seed.md
    ├── 08_补充线框图/                          29 张缺失页面线框图
    └── 09_测试报告/                            全部交付报告
        ├── 采购销售拆分+低代码交付报告_v3.0.md  ⭐ 本次交付
        ├── 全需求补齐交付报告_v2.0.md           P0-P3 · 38 项功能
        ├── 阿里云部署报告.md
        ├── UI优化交付报告_v1.1.md
        ├── UI设计规范_v1.1.md
        ├── 测试用例与运行报告.md
        └── 交付最终报告.md
```

## 四、阅读路径推荐

| 角色 | 阅读顺序 |
|---|---|
| **产品经理 / 需求方** | 01 PRD → 02 需求分析（决策 & 用户故事）→ 08 补充线框图 |
| **UI/UX 设计师** | 03 设计图 → 08 补充线框图 → 02 需求分析 |
| **系统架构师** | 02 需求分析 → 04 功能详细设计 → 05 数据库设计 → 06 API 设计 → 07 部署方案 |
| **后端工程师** | 04 功能详细设计 → 05 数据库设计 → 06 API 设计 |
| **前端工程师** | 03 设计图 + 08 补充线框图 → 06 API 设计 |
| **DevOps** | 07 部署方案 |
| **测试工程师** | 02 需求分析（验收标准）→ 04 功能详细设计（异常场景）|

## 五、当前完成度

- ✅ 需求梳理与用户故事拆解（100 条）
- ✅ 关键决策收敛（23 项 D-xx）
- ✅ 补充设计图线框描述（29 张）
- ✅ 功能详细设计（16 模块）
- ✅ 数据库设计（69 张表 + 索引 + 字典）
- ✅ REST API 接口清单（20 组）
- ✅ 部署方案（Docker Compose + Seed）
- ⬜ 高保真设计图（08 补充部分待 UI 设计师完成）
- ⬜ OpenAPI YAML 拆分（06 目录下待生成）
- ⬜ Flyway 迁移脚本 SQL 落地（05 表结构已定义，可从 md 提取为 .sql）
- ⬜ Seed 生成器代码（07 已定义规模，待实现 Python/Node 脚本）
- ⬜ 前后端工程脚手架
- ⬜ Docker 镜像构建

## 六、后续步骤（建议顺序）

1. **UI 设计师** 依据 `08_补充线框图` 产出高保真 Figma；
2. **架构师** 复核 `04/05/06` 后拉通团队；
3. **后端** 建工程骨架：`api-gateway + services + job-worker`（Java 或 Node）；
4. **DBA** 把 `05_数据库设计` 中的建表 SQL 拆到 `db/migrations/*.sql` 交给 Flyway；
5. **前端** 建 `web-pc`（PC）与 `web-h5`（H5）双工程；
6. **DevOps** 按 `07_部署方案` 完成 Docker 镜像、Compose、Nginx 与 Seed 生成器；
7. **QA** 依据 `02_需求分析.验收标准` 编写测试用例；
8. **业务方 / PM** 走查一轮，签字确认后开工。

## 七、术语速查

| 术语 | 含义 |
|---|---|
| DMS | Dealer Management System |
| DCMS | 合同管理子系统 |
| RS/DP | 报表 / Dealer Profile |
| LP/T1 | 一级经销商 |
| T2/LS | 二级经销商 |
| Sales-In | 厂商 → 经销商出货 |
| Sales-Out | 经销商 → 终端销售（动销）|
| IMS | Integrated Market Sales |
| UDI | 医疗器械唯一标识（批号+序列号）|
| RMA | 退货授权 |
| A2A | Agent-to-Agent 一级向下游分销 |

---

如有疑问：先看 `02_需求分析/需求分析_UserStory.md` 的《零、关键决策记录》，多数问题已在里面回答；未答复的进入评审例会讨论。

---

## 快速启动

本项目已提供一键启动的 Docker Compose 编排，包含 PostgreSQL / Redis / MinIO / Mock Server / Backend 五个服务。

```bash
# 1. 进入项目根目录
cd d:\Workspace\TRAE\DMS

# 2. 先在 backend/ 下构建可执行 jar（首次或后端代码变更后执行）
cd backend
mvn clean package -DskipTests
cd ..

# 3. 一键启动所有服务
docker compose up -d

# 4. 查看服务状态
docker compose ps

# 5. 访问以下入口
# - Swagger UI : http://localhost:8080/swagger-ui.html
# - 健康检查   : http://localhost:8080/actuator/health
# - MinIO 控制台: http://localhost:9001  （账号 minioadmin / minioadmin）
# - Mock Server : http://localhost:9090/__admin/mappings

# 6. 默认登录账号
#    用户名 : admin
#    密码   : Sh123456
```

### Flyway 迁移与 Seed 数据

- V1~V6 建表 SQL + V7 种子数据在 backend 启动时自动执行；
- Seed 只在环境变量 `SEED_ENABLED=true` 时执行 V7 版本迁移；
- 全量演示数据规模：1 租户 / 20 组织 / 20 用户 / 8 角色 / 200 产品 / 100 医院 / 50 经销商 / 500 订单 / 2000 订单行 / 5000 库存流水 等。

### 常见操作

```bash
# 停止全部服务
docker compose down

# 停止并清空数据卷（会重置数据库、MinIO 数据）
docker compose down -v

# 单独重建 backend
docker compose build backend && docker compose up -d backend

# 查看日志
docker compose logs -f backend
```

—— END ——

