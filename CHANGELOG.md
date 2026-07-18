# 变更日志（CHANGELOG）

> 记录 DMS 项目文档演进过程。日期倒序。

---

## 2026-07-18 — v3.0 采购销售拆分 + 低代码

### 背景
用户反馈 5 个问题需要一次性交付：状态驱动按钮、中文详情视图、采购销售拆分、库存联动、低代码字段配置。

### 交付内容
| 模块 | 说明 |
|---|---|
| **数据库 V8** | 新增 `purchase_orders`、`purchase_order_lines`、`form_configs` 表；主表加 `extra JSONB` 列 |
| **后端 5 个新 Controller** | `PurchaseOrderController`、`OrderMetaController`、`FormConfigController`、`DictCrudController`、`InventorySummaryController` |
| **前端 3 处升级** | `workspace.html`：菜单拆分 + 状态驱动按钮 + 中文详情视图；`admin.html`：新增字典维护和字段配置页；`order-create.html`：销售/采购双模式 |
| **测试** | 14/14 冒烟测试全部通过 |
| **文档** | 新增 [采购销售拆分+低代码交付报告_v3.0.md](docs/09_测试报告/采购销售拆分%2B低代码交付报告_v3.0.md) 和 [DMS环境信息.md](docs/DMS环境信息.md) |
| **数据库快照** | 导出至 `docs/05_数据库设计/schema_export/`（157 KB schema + 2.2 MB 数据） |

### 服务器版本
- 后端镜像：`dms-backend:2.0.2`
- 4 个容器全部 Up · 磁盘清理释放 12 GB

---

## 2026-07-18 — 全需求补齐 v2.0（P0-P3 · 38 项功能）

### 交付批次
| 批次 | 优先级 | 项数 | 测试 |
|---|---|---|---|
| 批次 1 | P0 阻断项 | 6 | 15/15 ✅ |
| 批次 2 | P1 用户体验 | 10 | 10/10 ✅ |
| 批次 3 | P2 管理能力 | 10 | 10/10 ✅ |
| 批次 4 | P3 完整化 | 12 | 12/12 ✅ |

覆盖：合同 PDF/签章/ERP归档、UDI追溯、批量导入导出、综合看板、待办列表、邮件Token审批、超时提醒、缓存监视、集成 Mock、促销审批、返利引擎、借货单、Excel 导出、微信登录 + 7 张移动端 H5 页面。

---

## 2026-07-18 — V1 决策变更（D-24 ~ D-41）

### 背景
在正式开发前，业务方对第二轮 18 个开发前问题进行了确认，产出 18 项新决策 D-24 ~ D-41，需求分析师、设计师、架构师同步修订全部核心文档。

### 关键决策摘要

| # | 决策 | 结论 |
|---|---|---|
| D-24 | 团队/工期 | 15+ 人 / 3-4 个月 |
| D-25 | 品牌视觉 | 使用组件库默认主题（Element Plus / Vant），Logo 用文字样式 |
| D-26 | 交付环境 | 仅本地部署（Docker Compose） |
| D-27 | 默认超管 | 固定 admin / Sh123456 |
| **D-28** | **促销降级** | **V1 只做满减 + 起订量**，删除满赠与组合销售 |
| D-29 | UDI | 可开关，V1 不真实上报监管 |
| D-30 | 电子签章 | Mock 契约按 e签宝 API |
| D-31 | ERP | 通用 REST，不绑定厂商 |
| **D-32** | **删除 SSO** | V1 仅账号密码登录 |
| **D-33** | **通知渠道** | 站内 + 企微/飞书 Webhook，删除邮件短信 |
| D-34 | 报表 | 固定 10 类 + T+1 物化视图 |
| D-35 | 权限 | 四层 RBAC + 行级（不做字段级） |
| **D-36** | **H5 登录** | 微信扫码 + 首次绑定 DMS 账号 |
| D-37 | 多语言 | 中文 + 预留 i18n |
| D-38 | 主题 | 亮色 + 租户可改主色 |
| D-39 | 审计 | Excel 导出 + 3 年 + MinIO 冷存 |
| D-40 | 性能 | PRD 默认（500 并发 / 50 TPS） |
| D-41 | 交付方式 | 代码 + 培训 + 手册（不做灰度试点） |

### 文档修订清单

#### 📄 [需求分析_UserStory.md](d:/Workspace/TRAE/DMS/docs/02_需求分析/需求分析_UserStory.md)
- 新增决策记录 D-24 ~ D-41 到「零、关键决策记录」表
- 打删除线：US-LOGIN-06 (SSO)、US-B-Promo-03 (满赠)、US-B-Promo-05 (组合销售)、US-E-04 (SSO 集成)
- 重写：US-M-01 (H5 登录改为微信扫码)
- 更新：US-E-01 邮件/短信 → 企微/飞书 Webhook
- 优先级汇总：100 → 94 条

#### 📄 [高保真UI设计说明书.md](d:/Workspace/TRAE/DMS/docs/03_设计图/高保真UI设计说明书.md)
- 新增「V1 决策变更提示」章节到文档开头
- 声明 V1 采用 Element Plus / Vant 默认主题，本设计说明作为长期参考
- 标注促销页面简化（W-13/W-14）、H5 登录改造（W-24）

#### 📄 [功能详细设计.md](d:/Workspace/TRAE/DMS/docs/04_功能详细设计/功能详细设计.md)
- 版本 V1.0 → V1.1
- 新增章节头「V1 决策变更记录」表
- FDD-1 登录：删除 SSO，新增 1.5 微信扫码登录
- FDD-2 工作台：消息通道改为站内+企微/飞书
- FDD-10 促销：type 枚举缩减为 {MOQ, FULL_REDUCTION}，删除 GIFT/BUNDLE 相关流程
- FDD-13 审计：Excel 导出 + 冷归档 MinIO
- FDD-14 外部接口：删 SSO/邮件/短信；新增微信 & 企微/飞书 Webhook；CA 按 e签宝契约
- FDD-15 H5：US-M-01 微信扫码登录流程

#### 📄 [架构评审纪要与技术方案.md](d:/Workspace/TRAE/DMS/docs/04_功能详细设计/架构评审纪要与技术方案.md)
- 新增章节头「V1 决策变更 ADR 补丁」
- 更新 ADR-02（促销）/ ADR-07（Mock 契约）/ ADR-08（鉴权移除 SSO 加微信）/ ADR-10（前端主题）
- 新增 ADR-11：通知渠道（站内+企微/飞书）
- 新增 ADR-12：交付范围（仅本地 Docker Compose）
- 里程碑调整为 3.5 个月（M5 上云移出 V1）

#### 📄 [数据库设计_Part1.md](d:/Workspace/TRAE/DMS/docs/05_数据库设计/数据库设计_Part1.md)
- users 表新增 `wechat_openid`、`wechat_unionid`、`wechat_bound_at`、`sso_service_id`（预留）
- 新增 unique 部分索引 `ux_users_wechat_openid`
- tenants 表新增 `attrs JSONB` 字段，约定 `primary_color` 存放位置
- user_login_logs.login_type 注释：V1 仅 PASSWORD / WECHAT / REMEMBER

#### 📄 [数据库设计_Part2.md](d:/Workspace/TRAE/DMS/docs/05_数据库设计/数据库设计_Part2.md)
- promotions.promo_type 注释：V1 仅 MOQ / FULL_REDUCTION，GIFT/BUNDLE 保留枚举位供 V2 扩展
- 新增 CHECK 约束 `ck_promo_type_v1`
- notifications.channel 注释：V1 仅 INAPP / WECHAT_BOT / FEISHU_BOT
- 数据字典 PROMO_TYPE 括注 V1 启用范围

#### 📄 [API接口清单.md](d:/Workspace/TRAE/DMS/docs/06_API设计/API接口清单.md)
- 新增文档头「V1 决策变更概要」
- 删除 `/auth/sso/verify`
- 新增微信登录 4 接口：`/auth/wechat/qrcode`、`/callback`、`/bind`、`/unbind`
- 删除 `/integrations/mail/send`、`/integrations/sms/send`
- 新增 `/integrations/wechat-bot/push`、`/integrations/feishu-bot/push`
- CA 说明按 e签宝；ERP 通用 REST
- 促销 API 声明 promo_type ∈ {MOQ, FULL_REDUCTION}
- 订单响应删除 gifts 字段
- 新增微信回调示例

#### 📄 [部署方案_DockerCompose与Seed.md](d:/Workspace/TRAE/DMS/docs/07_部署方案/部署方案_DockerCompose与Seed.md)
- 新增「V1 交付范围声明」引用块
- 拓扑总览：Mock 移除 SMS/邮件，新增企微/飞书 Webhook + 微信扫码
- docker-compose：api-gateway 新增 WECHAT_APP_ID/SECRET；mock-server 挂载 mocks/wechat
- Seed 数据：users 2 个绑定 wechat_openid；promotions 全为 MOQ/FULL_REDUCTION
- 第九章标注为参考文档（不属 V1 交付）
- 新增第十章「V1 决策变更影响」

---

## 2026-07-17 — 项目初始化 & 首轮决策 D-01 ~ D-23

- PRD 全量模块一次性上线
- 主行业：医疗器械
- 外部集成全 Mock
- 移动端全量 H5 适配
- 多租户 V1 就启用（tenant_id）
- 数据库 PostgreSQL 14+
- 部署形态 Docker Compose 一键启动
- Seed 全量测试数据

---

## 后续待办（M0 需求冻结前）

- [ ] 客户端提供正式 Logo（或确认沿用文字 Logo）
- [ ] 客户端提供企微/飞书 Webhook URL（用于 Mock 契约对齐）
- [ ] 客户端提供微信开放平台 AppID/AppSecret（可先用 Mock）
- [ ] 生产环境部署 checklist 待写入操作手册
- [ ] Seed 数据量最终评审确认

—— END ——
