# AI 开发文档（快照指针）

> 本文件为跨会话快速加载入口。**本项目文档主目录为 `docs/`**，完整 AI 上下文快照维护在：
>
> **👉 [../docs/AI开发文档.md](../docs/AI开发文档.md)**（技术栈、目录结构、编码规范、模块信息、第三方依赖、历次修改记录、踩坑点、本地启动配置）

## 当前版本速览（2026-08-27）

- 当前版本：**v4.3.1**（测试环境镜像 v433；上午 v4.3.0 MINOR 功能包 + 下午 v4.3.1 PATCH 走查修复）
- Flyway：**V121–V134**（V134 = rma_order_lines.serial_no）
- 技术栈：Spring Boot 3.2 + Java 17 + JPA/Hibernate + Flyway + PostgreSQL 14 + Redis 7 + MinIO；Vue 3 + Vite 5 + Element Plus（PC）+ Vant 4（H5）+ Pinia；Docker Compose（backend/nginx/postgres/redis/minio 5 容器）
- 测试环境：http://43.128.145.141/dms/ （ubuntu / Welcomeyyx0616）
- 生产环境：http://8.133.193.238/dms/

## 今日（2026-08-27）变更要点

- **v4.3.0（R1–R9）**：V4 计价引擎（合同价→全局折扣→促销→标准价）、代金券、客户自助注册、产品/客户全局折扣、合同价格、销退多出库、促销增强、订单价格快照、审批回调扩展；Flyway V121–V133。
- **v4.3.1（4 BUG 修复）**：①代金券审批拒绝不返还（Critical，release 回 ISSUED）；②销退新建页返工（经销商→仓库→发货单→退货原因、按钮门禁、批号/序列号/仓库筛选恢复、同仓库校验）；③销退行 serialNo（V134）；④销售订单重开回显（`productId ?? id` 兼容）。
- 规则沉淀：AGENTS.md「页面重写/改造功能对照规则」、.trae/rules/project_rules.md「铁律 9：部署后文档 URL 必检」。
- 最新踩坑点：第 42–47 条（页面重写功能减法、外键禁自由文本、回显字段兼容、铁律 9、Windows 打包须用 `tar -a -cf` 正斜杠、plink 复杂脚本落地 .sh）。

## 新会话加载顺序

1. 本文件 → `docs/AI开发文档.md`（完整上下文）
2. `AGENTS.md` + `.trae/rules/project_rules.md`（铁律与部署规则）
3. `docs/项目设计文档.md`（架构与版本变更记录）
4. `docs/文档索引.md`（全部文档导航）
