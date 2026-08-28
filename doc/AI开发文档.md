# AI 开发文档（快照指针）

> 本文件为跨会话快速加载入口。**本项目文档主目录为 `docs/`**，完整 AI 上下文快照维护在：
>
> **👉 [../docs/AI开发文档.md](../docs/AI开发文档.md)**（技术栈、目录结构、编码规范、模块信息、第三方依赖、历次修改记录、踩坑点、本地启动配置）

## 当前版本速览（2026-08-28）

- 当前版本：**v4.4.2**（测试环境已部署验证；v4.4.0 寄售业务闭环 → v4.4.1 红冲修复与开票拣选 → **v4.4.2 全站 MySolMed 品牌 logo + 测试环境域名 dms-dev.mysolmed.com**；**生产环境未动，仍为 v3.12.4，推送待用户明确指令**）
- Flyway：**V121–V139**（V138 = order_lines.consignment_stock_id；V139 = sales_out_lines.is_red + ux_sales_serial 部分唯一索引；Current=139 已验证）
- 技术栈：Spring Boot 3.2 + Java 17 + JPA/Hibernate + Flyway + PostgreSQL 14 + Redis 7 + MinIO；Vue 3 + Vite 5 + Element Plus（PC）+ Vant 4（H5）+ Pinia；Docker Compose（backend/nginx/postgres/redis/minio 5 容器）
- 测试环境：**http://dms-dev.mysolmed.com**（域名 → 43.128.145.141，裸域名 302→`/dms/`；IP 直连同样可用；ubuntu / Welcomeyyx0616；浏览器登录 sys_admin / Dms@123456，平台后台 admin / Sh123456）
- 生产环境：http://8.133.193.238/dms/

## 今日（2026-08-28）变更要点

- **v4.4.2（品牌与域名）**：①全站替换 MySolMed 品牌 logo（藏青 #0B2545「m」标 + 青色圆点）——frontend-vue 与 admin-vue 的 `DmsLogo.vue` 改为 `<img>` 方案（`src/assets/brand/logo-mark.png` / `logo-mark-white.png`，inverse 白标用于深色侧边栏/登录页），两项目 index.html 增加 favicon-16/32/48 + apple-touch-icon、theme-color 改 #0B2545、标题改「MySolMed DMS …」；覆盖 PC 登录页/工作台首页/侧边栏、移动 H5 登录页、平台后台登录页与布局共 7 个使用点。②测试环境启用域名 `dms-dev.mysolmed.com`（DNS 已解析到 43.128.145.141；nginx `server_name` 早已含该域名）；根路径 `/` 由"返回宣传手册 landing"改为 **302 → `/dms/`**（测试环境直达 DMS），宣传手册保留在 `/brochure/`；后端 `APP_BASE_URL` 改为 `http://dms-dev.mysolmed.com/dms`（审批邮件链接）。③**nginx 变更管控规则落档**（见 AGENTS.md）：禁随意调整，改动须备份→最小 diff→`nginx -t`→reload/重启→容器内 `nginx -T` 取证；注意 **bind-mount 下 `sed -i` 换 inode 后必须 restart 容器**（reload 不生效，本次实测踩坑）。
- 验证：铁律 9 真实浏览器全过——裸域名 → 302 → `/dms/home` 工作台；PC 首页 2 logo、移动 H5 登录页 1 logo、后台 1 logo 均 `naturalWidth=512`；`/api/auth/login` 200；全入口 curl 200；Console 无 error。

- **v4.4.0（R1–R7，MINOR 寄售业务闭环）**：寄售补货/开票/红冲订单类型与寄售开关、寄售台账 consignment_stock + movements 五类流水（REPLENISH_IN/INVOICE_LOCK/INVOICE_DEDUCT/INVOICE_RELEASE/REPLENISH_OUT）、经销商资信模块 dealer_credit_profiles、进销存报表精细化、资源选择器；Flyway V135–V137（含 RMA/INVOICE_ORDER 审批模板）。
- **v4.4.1（PATCH，BUG-01~04 + 红字补货 + 拣选交互方案 A）**：①BUG-01 补货→寄售入库链路三缺口修复（红字补货建单 SOR/validateReplenishRed、ux_sales_serial 改部分唯一索引豁免红字行、SalesOutService 钩子拼写 `"REPLENISH"`→`"REPLENISHMENT"`）；②BUG-02 供应商门户菜单 inventoryOnly 补全；③BUG-03 CrudView 浮动 Promise 补 catch；④BUG-04 官网 landing 图片 404 修复；⑤开票寄售库存拣选弹窗（OrderCreate.vue：经销商 ResourcePicker 门禁、整行勾选、序列号限 1、实时汇总、整单替换、el-tag 回显）；Flyway V138–V139。
- 验证：E2E scripts/e2e_invoice_consignment.py **23/23 全绿**（开票闭环 4 场景 + 红字补货 9 项检查）；铁律 9 真实浏览器门禁全 PASS（/、/brochure/、/dms/、登录→工作台、寄售台账列表、H5 供应商、订单新增→拣选弹窗全链路，弹窗实测勾选 B2608-B2 应付 ¥470.40）；测试库存 seed_consignment.py 9 行台账。
- 最新踩坑点：第 48–51 条（红冲共用序列号须改部分唯一索引且所有 INSERT 绑定 is_red、字符串字面量分支须与枚举逐字核对、红单重建行后回调须重取行 ID、integrated_code_mode Exec V8 沙箱范式）。

## 新会话加载顺序

1. 本文件 → `docs/AI开发文档.md`（完整上下文）
2. `AGENTS.md` + `.trae/rules/project_rules.md`（铁律与部署规则）
3. `docs/项目设计文档.md`（架构与版本变更记录）
4. `docs/文档索引.md`（全部文档导航）
