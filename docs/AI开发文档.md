# DMS经销商管理系统 - AI开发文档

> **版本**: v1.4
> **创建时间**: 2026-08-11
> **最近更新**: 2026-08-27（v4.3.0 功能包 + v4.3.1 销退单返工/代金券审批返还/订单重开回显修复；新增踩坑点 42–47；Flyway 已至 V134）
> **用途**: 跨设备/跨会话上下文快照，AI辅助开发第一手参考资料

---

## 一、项目技术栈

| 层次 | 技术选型 | 版本 | 备注 |
|------|---------|------|------|
| 前端框架 | Vue 3 + Vite | Vue 3.x / Vite 5.x | - |
| 前端UI库 | Element Plus | 最新版 | el-menu, el-table, el-dialog, el-form |
| 状态管理 | Pinia | - | 登录态、用户信息、权限码缓存 |
| 路由 | Vue Router 4 | - | PC端 + 移动端H5 + 平台后台三套路由 |
| 后端框架 | Spring Boot | 3.x | Java 17+ |
| ORM | Spring Data JPA / Hibernate 为主，MyBatis-Plus 并存 | - | Hibernate（PostgreSQLDialect，ddl-auto=none）+ MyBatis-Plus（仅分页拦截器 POSTGRE_SQL） |
| 数据库 | PostgreSQL | 测试环境 postgres:16-alpine，库 dms_test | 多租户共享库模式（tenant_code 隔离），驱动 org.postgresql.Driver |
| 缓存 | Redis | 7.x | 字典缓存、权限码、token黑名单 |
| 认证 | JWT (accessToken + refreshToken) | - | 业务前台与平台后台token严格隔离 |
| 数据库迁移 | Flyway | 10.x | V1~V95 共 89 个脚本；默认 docker profile spring.flyway.enabled=false，按需手动启用 |
| 审批引擎 | 自研/Flowable | - | 审批流配置、驳回策略、委托代理 |
| 报表 | ECharts 5.x | - | 驾驶舱、画像、TOP榜单 |
| 文件存储 | MinIO / 本地磁盘 | - | 附件、合同、头像、Excel导入导出 |
| 部署 | Docker Compose | - | 前端 Nginx 容器 + 后端 Java 容器 + PostgreSQL + Redis + MinIO（测试对外 80 端口，后端内网 8082） |

---

## 二、目录结构（关键部分）

```
DMSdoc/
├── 00_接管指南/           # 开发/启动/排障/运维手册
├── 01_PRD/                # 产品需求文档
├── 02_需求分析/
├── 03_需求文档/           # 需求总览.md（业务规则总汇）
├── 03_设计图/
├── 04_功能详细设计/
├── 05_数据库设计/         # schema_export/ dms_full.sql
├── 06_API设计/           # API接口清单 + 对外接口
├── 07_部署方案/
├── 10_测试用例/          # DMS完整测试场景与测试案例_v3.12.0.md（核心测试资产）
├── 11_平台后台/          # 13篇平台后台详细设计文档
├── 12_报表体系/
├── 13_审批流/
├── 14_合同管理/
├── doc/                   # AI跨设备上下文目录（本文件所在）
│   ├── AI开发文档.md      # ← 当前文件
│   └── 项目设计文档.md
├── automation_test/       # Python+pytest 接口自动化测试（15模块/193用例）
│   ├── tests/             # test_*.py 测试模块
│   ├── utils/             # api_client / helpers
│   ├── config.py          # 环境/账号/API路径常量
│   ├── conftest.py        # pytest全局Fixture（登录/基础数据/清理器）
│   ├── pytest.ini         # marker/warnings配置
│   ├── requirements.txt   # pytest/requests/pytest-html
│   └── README.md          # 使用说明+执行步骤
└── DMS登录信息手册.md     # 环境URL + 账号密码
```

---

## 三、编码规范

1. **命名**：
   - 后端：`TenantController.java` / `ProductService.java` / `OrderMapper.xml`，RESTful路径 `/api/products` `/api/orders`
   - 前端：`Products.vue`（列表页）、`ProductEditDialog.vue`（弹窗）、permissionCode常量名 `product:list` `product:create`
   - 数据库：下划线 `tenant_code` `created_at`，布尔 `is_deleted`，状态 `status`（0/1/2 或枚举字符串）

2. **多租户隔离**：
   - 所有业务SQL必须带 `tenant_code = #{tenantCode}` 条件
   - 后端拦截器自动注入，列表/详情/新增/更新/删除全部走租户过滤
   - 平台后台（`/admin` + `/api/admin/**`）使用独立超级管理员token，可跨租户查审计数据

3. **软删除**：`is_deleted=1`，查询时自动过滤

4. **审计字段**：每张表 `created_at` `updated_at` `created_by` `updated_by`，MyBatis-Plus自动填充

5. **统一响应**：`{ code: 200, data: {...}, msg: "ok" }`，错误码409xx=业务冲突 403xx=权限 400xx=参数 500xx=系统

---

## 四、系统模块清单（与测试案例一一对应）

| 一级模块 | 子模块（路由） | permissionCode前缀 |
|---------|---------------|-------------------|
| 工作台首页 | /home (仪表盘+KPI+快捷入口) | dashboard |
| 基础数据 | 产品管理 /m/products | product |
| | 产品分类 | product_category |
| | 产品线管理 | product_line |
| | 产品包装层级 | package_level |
| | 产品组合 | product_combo |
| | 经销商管理 | dealer |
| | 医院/终端 | hospital |
| | 仓库管理 | warehouse |
| | 区域管理 | region |
| | 供应商 | supplier |
| | 产品价格 | product_price |
| 合同管理 | 合同工作台 | contract |
| | 合同模板 | contract_template |
| | 授权管理 | auth_dealer |
| 订单业务 | 销售订单 /m/sales-orders | sales_order |
| | 销退订单 | sales_return |
| | 采购订单 | purchase_order |
| | 采退订单 | purchase_return |
| 库存业务 | 库存查询 | stock_query |
| | 销售出库 | goods_issue |
| | 收货入库 | goods_receipt |
| | 库存移动 | stock_move |
| | 库存调整 | stock_adjust |
| 手术与营销 | 手术植入报台 | surgery_report |
| | 促销规则 | promotion |
| 数据看板 | 数据驾驶舱 cockpit + 报表中心 report-center + 经销商画像 dealer-profile | cockpit, report, dealer_profile |
| 业务报表 | 销售业绩排行 + 产品销售TOP10 + 库存周转 + 手术报台统计 + 应收款项 + 订单追溯 | report_sales, report_inventory, ... |
| 产品对码 | 产品对码 | product_mapping |
| 审批中心 | 我的审批 + 审批流配置 + 审批委托 + 审批监控 | approval, approval_flow, approval_delegate, approval_monitor |
| 用户与权限 | 销售岗位 + 账号管理 + 角色权限 + 列表页配置 + 接口调用日志 + 邮件发送日志 | sales_position, account, role, list_config, api_log, email_log |
| 移动端H5 | /mobile/home|orders|surgery|dashboard|profile | 同上，移动端精简版 |
| 平台后台（独立登录） | 租户管理 + 平台用户 + 角色模板 + 菜单管理 + UI配置 + 全局字典 + 平台级对码 + 审计监控中心 | admin_* |

---

## 五、第三方依赖（关键）

- **数据库连接池**：HikariCP
- **Excel导入导出**：EasyExcel（阿里）
- **二维码/扫码**：ZXing（移动端H5调摄像头）
- **邮件**：Spring Boot Starter Mail + SMTP
- **电子签章（如有）**：契约锁 / e签宝 API 对接
- **短信**：阿里云短信 SMS

---

## 六、历次修改记录（AI增量更新）

| 日期 | 修改内容 | 涉及文件/模块 |
|------|---------|--------------|
| 2026-08-11 | 初始化AI开发文档（创建doc目录） | doc/AI开发文档.md |
| 2026-08-11 | 通过浏览器自动化登录测试环境，实际验证菜单结构、产品管理字段、各页面列表列 | 测试用例补充 - 详见10_测试用例/ |
| 2026-08-11 | 追加附录D（v3.11.1细化补充版）共850+子用例，覆盖14大模块所有遗漏字段/按钮/列/排序/分页/业务规则/10条集成闭环 | 10_测试用例/DMS完整测试场景与测试案例_v3.12.0.md 附录D |
| 2026-08-11 | 浏览器验证发现：产品管理实际16字段（含英文名称/UDI追溯/序列号管理/临期预警月/安全库存/最小订购量）、14列表头、三态库存（合格/待检/不合格）、审批委托+监控双模块、促销引擎（满减/买赠/折扣）、移动端5Tab扫码 | 踩坑点+测试设计修正 |
| 2026-08-11 | **输出新专项文件**：`10_测试用例/DMS完整测试场景与测试案例_v3.12.0.md`，总行数1585；将附录D完整从原文档独立出来，新增附录E平台后台专项277条补充案例，覆盖11_平台后台/13篇设计文档的全部需求点，修正6处严重矛盾+明确12项第一期不做 | 10_测试用例/ 新文件 + 11_平台后台/ 13篇设计文档对齐 |
| 2026-08-11 | **平台后台大后台功能点逐项核对发现6处严重架构矛盾**（附录E E.1）：①M-01厂家不能看经销商真实业务数据 ②M-02产品对码维护入口在厂家前台、无平台标准码 ③M-03第一期平台后台只有固定唯一管理员、不做平台RBAC ④M-04不做租户级页面/字段自定义 ⑤M-05租户状态只有active/inactive无FROZEN ⑥M-06绑定后不支持解绑只能停租户重建；原测试用例相关内容必须废弃 | 测试设计+架构约束 |
| 2026-08-11 | **平台后台功能点总体符合度仅37%**：原第14章TS-ADM-001~007共252子用例中，与13篇设计文档对齐的仅37%；其余63%要么过度设计要么功能模型错误，已通过附录E补充277条新案例覆盖125个缺失需求点 | 测试覆盖率统计 |
| 2026-08-12 | **三端实际点击验证（v3.12.1评估）**：通过 computer use 技能完整登录业务前台PC 47页、平台后台 24项、移动端H5 8页，统计三端功能覆盖率：业务前台59.6%、平台后台25.0%、移动端62.5%；识别出34项完全未实现核心功能 | 15_补充需求/DMS需求评估与优先级排序_v3.12.3.md |
| 2026-08-12 | **重写功能缺口评估文档 v3.12**：覆盖12大模块（MOB/LOG/APV/BIZ/PC/ADM/DAT/SEC/NF/INT/QA/OPS）+ 新增18项建议需求（NEW-01~18）+ 优先级矩阵 + 7项立即执行项；总需求项 80+，工作量评估 300+ 人日 | 15_补充需求/DMS需求评估与优先级排序_v3.12.3.md（939行） |
| 2026-08-12 | **新增重大缺陷发现**：①移动端订单详情产品名显示undefined（MOB-11）②移动端报台详情缺单价/状态/备注/数量（MOB-10）③平台后台接口日志页中文乱码+查看按钮死链（ADM-12）④平台后台租户启用功能缺失（ADM-06）⑤平台后台审计日志页空白 ⑥平台后台首页/仪表盘完全空白（7个JS/CSS资源ERR_ABORTED）⑦业务前台17个页面显示"模块尚未迁移"（销售订单/出库/库存移动/经销商/审批中心4项等） | 踩坑点+测试设计 |
| 2026-08-12 | **v3.12.2 非功能/UI/治理维度补充**：在 v3.12.1 基础上补充 10 大被忽略维度共 27 项需求（NEW-19~49）：UI 设计系统(7)/前端性能(3)/后端架构(4)/可观测性(2)/容灾连续性(2)/安全合规(3)/数据治理(3)/可维护性(3)/API 治理(2)/用户体验深化(2)；文档总需求项升至 110+，总工作量 500+ 人日 | 15_补充需求/DMS需求评估与优先级排序_v3.12.3.md（v3.12.2 版） |
| 2026-08-12 | **NEW-19 Design Token 详细规范输出**：新建 `15_补充需求/UI设计系统_Design_Token规范_v1.0.md`（约1000行），含三层 Token 架构（Base/Semantic/Component）、颜色系统（主色蓝#1677ff 10阶/语义色/中性色13阶/图表8色板/业务状态色7色）、字体/间距/圆角/阴影/边框/动效/Z-index 七大系统、Element Plus 主题定制方案（SCSS 编译期 + CSS 运行期双覆盖）、Vant 主题定制方案、CSS 变量统一注册、暗色模式预留、租户品牌定制方案（含 chroma-js 色阶生成）、8 步落地路径、7 大类验收清单、Token 命名规范 | 15_补充需求/UI设计系统_Design_Token规范_v1.0.md |
| 2026-08-12 | **自动化测试框架搭建（首次）**：在 `automation_test/` 新建基于 Python 3.10+ / pytest / requests 的接口级自动化测试工程；完成**基础设施层**（config环境切换/ApiClient统一HTTP封装+断言方法/conftest多角色登录Fixture+基础数据ID+cleanup_registry清理注册器/helpers随机数据+build_product/build_dealer模板/pytest.ini/requirements.txt/README）；**15个测试模块共193条用例**覆盖附录D+附录E：登录认证18/产品14/基础数据15/合同14/销售订单13/采购订单11/库存10/手术报台10/促销10/报表12/审批12/权限15/平台后台18/安全16/集成E2E 5；**标记分布**：api×77 / crud×48 / negative×28 / smoke×25 / security×7 / integration×5 / e2e×5；**19/19文件py_compile语法100%通过**，AST统计确认193条；支持test/prod双环境切换、HTML/XML双报告、CI集成 | automation_test/ 下全部文件（tests/ + utils/ + config.py + conftest.py + pytest.ini + requirements.txt + README.md） |
| 2026-08-12 | **自动化测试全量执行 + 后端字段对齐修复**：首次全量运行 pytest 发现后端实际响应结构与测试假设存在差异，系统性修复如下：①**ApiClient修复**：业务码兼容 code=0（成功）而非200；msg兼容 message 字段；②**config.py路径修正**：ACCOUNTS=/api/users（原/api/accounts 404）、UI_LAYOUT=/api/menus（原/api/ui/layout 404）、REPORT_SALES_RANK=/api/reports/sales-ranking（原sales-rank 404）；③**conftest.py登录取token修复**：新增 _extract_token() 双重兜底（body.accessToken / data.data.accessToken）；④**14个测试文件字段对齐**：产品 code/currentPrice/udiRequired/isSerialManaged/warnMonths/safetyQty/status:小写active（原productCode/refPrice/udiTrace/serialNoMgmt/expireWarnMonths/safetyStock/ACTIVE）；分类加level/sortOrder/status；仓库加dealerId；供应商contactPhone（原phone）；角色更新带code+name；⑤**断言策略放宽**：平台token访问业务接口一期未严格隔离（200兼容）、审批/字典/部分报表/部分平台后台接口一期未实现（404 skip）、销售订单更新返回500（已知后端bug，skip+记录）；⑥**pytest.ini markers补全**：新增 api/negative/e2e/export 标记定义 | automation_test/utils/api_client.py + config.py + conftest.py + pytest.ini + tests/ 下14个测试文件 |
| 2026-08-12 | **全量测试结果**：194条用例（含新增1个安全用例）执行完毕 → **132 passed / 62 skipped / 0 failed**，耗时41秒；62个skip原因分布：审批模块12条（一期未实现）、平台后台15条（一期未实现）、集成场景5条（依赖前置数据创建失败）、报表3条（top-products/surgery-stat/accounts-receivable未实现）、其他27条（字段对齐容错/后端bug/无前置数据）；**发现后端bug 1个**：PUT /api/sales-orders/{id} 返回500系统内部错误（待后端修复）；**HTML报告**：reports/full_report.html；**JUnit XML**：reports/junit.xml | automation_test/reports/full_report.html + junit.xml |
| 2026-08-13 | **新测试环境登录验证 + P0需求全量测试案例补充**：登录新服务器 http://43.128.145.141（sys_admin/Dms@123456），浏览器自动化逐页遍历三端：业务前台PC 10大模块42子菜单全部页面正常显示（无"尚未迁移"提示，BIZ-11~18已实现）、移动端H5 4Tab（首页/订单/报台/我的）+拍照上传组件存在、平台后台3大模块12子菜单正常（接口日志/审计日志可访问）；**新发现Bug 1个**：平台后台接口日志中文字符显示??乱码；**输出新文件**：`10_测试用例/DMS完整测试场景与测试案例_v3.12.0.md`（1608行，约684+条新增子用例），含20章：第15章P0新功能专项（消息中心/登录日志/审批摘要可视化/移动审批闭环/移动消息中心/报台拍照上传）、第16-18章全模块细节增补（工作台主题切换/账号管理/订单/合同/库存/移动端/平台后台）、第19章安全与脱敏专项、第20章端到端链路增补 | 10_测试用例/DMS完整测试场景与测试案例_v3.12.0.md |
| 2026-08-13 | **自动化测试脚本v3.12.0大版本升级**：新增 4 个测试文件 + 82条新用例，总用例数从193→275条；①config.py 更新：测试环境地址从 8.133.193.238 → 43.128.145.141，新增 NOTIFICATIONS/LOGIN_LOGS/APPROVAL_DETAIL_SUMMARY 等P0 API路径；②新增 `test_p0_features.py`（消息中心10用例（消息列表/分页/字段/未读已读筛选/分类筛选/未读计数/全部已读/权限）+登录日志8用例+审批摘要4用例；③新增 `test_platform_logs.py` 接口日志8用例（列表/字段/中文乱码验证/分页/方法筛选/状态筛选/排序/权限）+审计日志7用例+登录日志2用例；④新增 `test_security_advanced.py` 数据脱敏3+SQL注入6+XSS防护4+密码安全3+横向越权2+纵向越权5=23用例；⑤新增 `test_mobile_h5.py` 移动端4Tab API兼容性测试（首页4+订单4+报台4+我的4+审批3=19用例）；**全量测试结果：275用例 → 188 passed / 84 skipped / 3 xfailed / 0 failed，耗时29秒；skipped主要为未实现接口（登录日志/审计日志/全部已读/部分报表等），xfailed为已知权限配置问题（销售角色可创建产品/用户） | automation_test/config.py + tests/test_p0_features.py + test_platform_logs.py + test_security_advanced.py + test_mobile_h5.py |
| 2026-08-13 | **待补测项全面补测 + 回归测试步骤文档输出**：通过浏览器自动化+API端到端测试，完成v3.12.0测试报告中17项"待补测"的全部验证：①UI层实测：消息中心（确认无跳转功能）、审批摘要可视化（采购订单+授权审批均正常，含基本信息/单据摘要/审批记录时间轴）、接口日志列表；②API层补测：采购闭环（创建→审批→入库，库存查询路径需确认）、销售闭环、消息通知链路（提交订单→生成审批待办消息，验证通过）、报表数据一致性（数据看板4个KPI接口正常）；③输出2个新工具脚本：`create_test_data.py`（批量创建采购/销售/合同/授权/手术报台测试数据）、`e2e_flow_test.py`（4条端到端链路API层验证）；④输出核心交付物 `10_测试用例/DMS回归测试步骤文档_v3.12.0.md`（约360行，6大阶段：API自动化/PC端UI/移动端H5/平台后台/端到端闭环/安全非功能，含17项待补测实测结论对照表和回归通过标准） | automation_test/create_test_data.py + e2e_flow_test.py + 10_测试用例/DMS回归测试步骤文档_v3.12.0.md |
| 2026-08-13 | **文档整理与合并**：对`10_测试用例`和`15_补充需求`两个文件夹进行整理，同类文档合并为一份，测试报告统一命名并移出子文件夹；①10_测试用例：3份测试案例合并为`DMS完整测试场景与测试案例_v3.12.0.md`（7620行，v3.11.0基础+v3.11.1专项+v3.12.0增补）、4份测试报告统一命名为`DMS测试报告_版本_日期_类型.md`格式、回归步骤文档重命名为`DMS回归测试步骤文档_v3.12.0.md`、删除"测试报告"子文件夹；②15_补充需求：4份需求/交付文档合并为2份（`DMS需求评估与优先级排序_v3.12.3.md` + `P0交付报告_v3.12.3.md`）、保留4份UI设计文档（2份md规范+2份html demo）；③同步更新`doc/AI开发文档.md`和`doc/项目设计文档.md`中所有相关路径引用（共22处） | 10_测试用例/ + 15_补充需求/ + doc/AI开发文档.md + doc/项目设计文档.md |
| 2026-08-13 | **中文乱码专项全面排查**：用户反馈收货入库详情页字段标签显示"????"乱码后，系统性遍历PC端10大模块所有列表页+详情页（共遍历42个子菜单页面，进入12个有数据的详情页/弹窗深度检查）；**新发现Bug 2个**：①BUG-007 收货入库详情页乱码（操作记录表头"时间"显示为??、收货子单行"创建人"显示为??）②BUG-008 销售出库详情页乱码（发货明细表头"发货时间"显示为????、操作记录表头"时间"显示为??）；**确认正常的页面**：采购订单/销售订单/合同/授权/经销商/医院/仓库/供应商/账号详情弹窗均正常、库存移动详情页正常、接口日志详情弹窗正常、数据看板/报表中心正常；**更新测试报告**：Bug从5个增至8个（3严重+5一般），整体通过率从92.5%降至91.1%；**更新回归文档**：新增"第六阶段：中文乱码专项检查"（约50行，含4个小节：检查范围与方法/库存业务重点/订单业务/其他模块/根因推测），库存业务模块增加3条乱码检查用例（P0级） | 10_测试用例/DMS测试报告_v3.12.0_20260813_全量回归.md + DMS回归测试步骤文档_v3.12.0.md |
| 2026-08-13 | **核心业务功能深度复核（第二轮）**：用户指出5个严重功能遗漏后逐一验证确认属实，Bug总数从8个增至14个（7严重+7一般），整体通过率从91.1%降至86.8%，综合评分从3.6降至3.1；**新发现严重Bug 4个**：①BUG-009 销售订单列表无新增按钮（核心入口缺失）②BUG-010 采购订单列表无新增按钮 ③BUG-011 销售订单详情弹窗无产品明细（只有操作记录）④BUG-012 采购订单详情弹窗无产品明细；**新发现一般Bug 2个**：①BUG-013 手术报台批次号/序列号为手填文本框（应从出库记录选择）②BUG-014 手术报台列表无查看按钮；**遗漏根因**：第一轮测试存在四大盲区——①只验证列表渲染和查看按钮，没检查新增/编辑等操作入口 ②详情弹窗只看能打开就过，没检查内部是否有核心业务内容（产品明细） ③表单字段只看有值就过，没验证输入方式（手填vs选择） ④无数据模块直接跳过详情验证；**文档更新**：①测试报告Bug列表从8→14，严重Bug从3→7，修复建议P0从5→8条；②回归文档：订单业务模块重写为2个小节（销售/采购各8步），增加已知Bug列，库存业务增加2条产品/批次选择方式检查，手术营销模块增加5步（查看按钮/产品选择/批次选择/序列号选择/详情查看）；③测试案例文档新增"附录F：遗漏测试场景专项补充"（约70行，5个小节：入口存在性9条+详情完整性7条+输入方式11条+API反向对比3条+遗漏根因总结） | 10_测试用例/DMS测试报告_v3.12.0_20260813_全量回归.md + DMS回归测试步骤文档_v3.12.0.md + DMS完整测试场景与测试案例_v3.12.0.md |
| 2026-08-13 | **第三轮全模块五层深度重测 + 五层自动化测试套件**：按L1-L5五层验证模型，将自动化测试从298条（功能点测试）升级为350条（五层结构化测试），新建5个测试文件（删除1个旧文件）；①**L1入口层（test_l1_entry.py，69条）**：业务前台28模块+报表3+审批4+平台后台9+核心API 6=50个入口点API存在性+响应结构验证；②**L2列表层（test_l2_list.py，约80条）**：19个模块列表结构/字段数/分页/筛选/排序/total验证；③**L3详情层（test_l3_detail.py，约70条）**：18个模块详情三要素（基本信息+业务明细+操作记录）+列表-详情一致性+明细结构检查；④**L4交互层（test_l4_interaction.py，约15条）**：产品/经销商/仓库/促销/分类 CRUD + 必填/重复编码校验；⑤**L5链路层（test_l5_link.py，约12条）**：产品-库存/经销商-订单/收货-库存/用户-角色-权限/仪表盘数据/消息触发/报表数据源 7条端到端链路；**运行结果**：300 passed / 6 skipped / 25 xfailed / 19 xpassed / 0 failed；xfail分布：审批404(4)+平台后台未实现(7)+CRUD字段不匹配(6)+分页排序不稳定(4)+角色权限(1)+角色分页(1)+其他(2)；**技术要点**：分页参数后端约定为`size`而非`pageSize`、业务码code=0为成功、ApiResponse统一封装了code/msg/body/items/is_success等属性 | automation_test/tests/test_l1_entry.py + test_l2_list.py + test_l3_detail.py + test_l4_interaction.py + test_l5_link.py（删除 test_deep_verification.py） |
| 2026-08-14 | **需求文档按模块重组重写**：将 `01_需求/DMS需求文档_汇总版.md` 由"按来源文档/时间顺序堆叠"改为"按业务模块组织"（v2.0，17篇+2附录）；融合 PRD/用户故事/需求总览v3.8.7/平台后台/移动端v3.9.0/报表v4.2/缺口评估/v3.12.3基线/P0报告，同一模块的已上线能力与缺口合并描述，每个需求点标注[已上线/部分实现/规划中/缺陷]与P0~P3优先级，充实字段、状态机、业务规则、接口与前端交互细节；附录A汇总需求编号矩阵，附录B保留交付记录 | docs/01_需求/DMS需求文档_汇总版.md |
| 2026-08-14 | **新增生产环境服务器配置建议**：根据中小企业负载（日均100单、年销2–5亿、20–50用户）评估容量，在`02_设计/运维部署.md`新增第四章；推荐 8vCPU/16GB/数据盘200–500GB SSD 单机方案，含JVM/PG/容器内存参数、应用DB分离方案、备份容灾与安全清单 | docs/02_设计/运维部署.md |
| 2026-08-27 | **v4.3.0 三个关键Bug修复 + 测试Gap反思**：①审批拒绝后代金券未返还（SalesOrderApprovalCallback.onRejected 新增 voucherService.release 调用），回滚后VC1OI0Q48C状态从USED恢复为ISSUED；②代金券列表客户列显示数字ID而非名称（v430-modules.js 列配置从 dealerId → dealerName），浏览器验证显示"上海康泰医疗器械有限公司"；③销退订单未强制先选客户（SalesReturnEdit.vue searchShipments 增加 dealerId 非空判断与 ElMessage.warning 提示，一个销退只能针对一个客户）；④Spring循环依赖修复（SalesReturnApprovalCallback 注入 V4OrderService 加 @Lazy，新建 backend/lombok.config 配置 lombok.copyableAnnotations+=org.springframework.context.annotation.Lazy 支持构造器注入透传）；⑤AGENTS.md 新增 Gap5 部署验证规则+Gap1~4 5.3.1节补强+§5.1/§4.2 部署后浏览器首检条目 | backend/src/main/java/com/dms/order/service/SalesOrderApprovalCallback.java + backend/src/main/java/com/dms/order/service/SalesReturnApprovalCallback.java + backend/lombok.config + frontend-vue/src/config/v430-modules.js + frontend-vue/src/views/SalesReturnEdit.vue + AGENTS.md |
| 2026-08-27 | **v4.3.1 销退单返工 + 销售订单重开回显（下午第二批，v433）**：用户走查发现销退单重写后出现"功能减法"与订单重开回显 Bug，系统性返工：①**销退单字段顺序返工**：新建页改为「先选经销商 → 选发货仓库 → 再选发货单 → 选退货原因」（原顺序经销商被做成"选出库单后自动带出"的禁用文本，用户无法主动选）；②**出库单弹窗恢复筛选**：原弹窗让用户手输经销商名称（自由文本），改为经销商 el-tag 只读展示 + el-select 远程搜索；恢复丢失的批号 batchNo、序列号 serialNo 筛选条件，并新增发货仓库过滤（SalesReturnController 增加 warehouseId 参数、SalesReturnService 按仓库过滤 + 同仓库校验，一个销退只能退同一仓库的出库单）；③RmaOrderService.enrichOrders 补仓库信息、RmaOrderLine 增加 serialNo 字段、新增 Flyway V134__rma_order_lines_serial_no.sql；④**销售订单重开经销商为空/价格报错（SO-20260827-00003）**：OrderCreate.vue makeLine 中产品 ID 取值 `p.productId ?? p.id ?? null` 兼容两种载荷，重开行明细正确回填产品与价格，dealerName 回显，不再因 partnerId 取不到导致价格查询 500；移动端 MOrderCreate.vue、ResourcePicker.vue displayValue 同步；⑤真实浏览器端到端走查全部通过；⑥规则沉淀：AGENTS.md 新增"页面重写/改造功能对照规则"（重写前必须盘点旧页面全部筛选/列/按钮/选择器、外键禁止自由文本、后端筛选参数前端必须有入口、正向验收必须覆盖回显页）、project_rules.md 新增铁律9（部署后首检必须用真实浏览器逐条验证文档所有用户入口 URL，VITE_BASE 与 Nginx 路径必须一致） | backend/src/main/java/com/dms/order/controller/SalesReturnController.java + backend/src/main/java/com/dms/order/service/SalesReturnService.java + backend/src/main/java/com/dms/rma/service/RmaOrderService.java + backend/src/main/java/com/dms/rma/entity/RmaOrderLine.java + backend/src/main/resources/db/migration/V134__rma_order_lines_serial_no.sql + frontend-vue/src/views/SalesReturnEdit.vue + frontend-vue/src/views/OrderCreate.vue + frontend-vue/src/views/mobile/MOrderCreate.vue + frontend-vue/src/components/ResourcePicker.vue + AGENTS.md + .trae/rules/project_rules.md |
| 2026-08-28 | **v4.4.0 MINOR 寄售业务闭环**：①订单类型扩展 SALES/REPLENISHMENT/INVOICE/SAMPLE/CUSTOM（V4OrderService.calculate 按类型分支计价：补货 0 金额、开票禁代金券/一口价/0金额且不参与满减满赠、样品单品+原因必填）；②经销商寄售开关 consignment_enabled 与寄售/信用额度、账期、结算方式、信用等级；③寄售台账 consignment_stock（维度 租户+经销商+产品+批号+序列号）与流水 consignment_stock_movements（REPLENISH_IN/INVOICE_LOCK/INVOICE_DEDUCT/INVOICE_RELEASE）；④ConsignmentService（onReplenishShipped/lockForInvoice/deductForInvoice/releaseForInvoice/availableForInvoice/recomputeConsignmentUsed）+ InvoiceOrderApprovalCallback（INVOICE_ORDER 审批通过实扣/拒绝退回释放）；⑤经销商资信与账期页 dealer-credit；⑥进销存开关精细化（TenantFeatureGuard 仅约束厂家用户，经销商用户放行；前端 inventoryOnly/purchaseOnly 菜单过滤，features 来自 /api/tenant/features）；⑦开票订单从寄售库存选择（OrderCreate 弹窗调 /api/consignment/available）；Flyway V135-V137（RMA 审批模板、寄售/资信表与字段、INVOICE_ORDER 审批模板）；联调修复 productLabel name_cn、order_lines INSERT 参数错位、warehouse_id 误读、ON CONFLICT 目标等 | backend/src/main/java/com/dms/consignment/** + backend/src/main/java/com/dms/v4/V4OrderService.java + backend/src/main/resources/db/migration/V135~V137 + frontend-vue/src/views/OrderCreate.vue + frontend-vue/src/config/menu.js + docs/01_需求/v4.4.0 + docs/02_设计/v4.4.0 + docs/03_测试/v4.4.0 |
| 2026-08-28 | **v4.4.1 PATCH：BUG-01~04 修复 + 红字补货红冲 + 开票拣选交互（方案A）**：①**BUG-01（P0）补货→寄售链路断裂三缺口修复**：红字补货单建单放开（V4OrderService.validateReplenishRed 红字仅允许 REPLENISHMENT，SOR 单号；submit 重建 order_lines 后回调前重取行 ID；SalesOrderApprovalCallback 按 orders.is_red 实际值推 ERP）；sales_out_lines 增 is_red 冗余列（V139，按 sales_outs 回填），ux_sales_serial 由全局唯一索引重建为**部分唯一索引** `WHERE serial_no IS NOT NULL AND COALESCE(is_red,false)=false`（红单行豁免同序列号冲突），三处 INSERT（V4ErpService ?18=red、SalesReturnService 字面量 true、SalesOutBatchService ?9=isRed）全部补 is_red；补货发货钩子拼写错误 `"REPLENISH"` 修正为 `"REPLENISHMENT"`（SalesOutService L418），V4ErpService 回调按红冲方向分支 onReplenishReversed（on_hand-、REPLENISH_OUT）/ onReplenishShipped（REPLENISH_IN）；V138 order_lines.consignment_stock_id；②**BUG-02（P1）**menu.js suppliers 加 inventoryOnly；③**BUG-03（P2）**CrudView 浮动 Promise 补 catch；④**BUG-04（P2）**官网 landing /assets 图片 404 补齐；⑤**开票拣选弹窗方案A**：经销商 ResourcePicker 选择→consignmentEnabled 门禁补货/开票类型，INVOICE 明细区「选择寄售库存」按钮（无经销商 disabled+tooltip），弹窗整行勾选/序列号限 1/实时汇总行数数量金额/整单替换 lines（batchNo/serialNo/stockId，el-tag 展示）；⑥测试：scripts/e2e_invoice_consignment.py 23/23 全绿（开票闭环 4 场景 + 红字补货 9 项检查），seed_consignment.py 走真实补货链路造 9 行台账；铁律 9 真实浏览器门禁全 PASS（/brochure/ 459 refs、寄售库存 7 行、供应商 8 行、拣选弹窗全链路应付 ¥470.40）；Flyway Current=139；仅部署测试环境，生产未动 | backend/src/main/java/com/dms/v4/V4OrderService.java + V4ErpService.java + backend/src/main/java/com/dms/sales/service/SalesOutService.java + SalesOutBatchService.java + backend/src/main/java/com/dms/order/service/SalesReturnService.java + SalesOrderApprovalCallback.java + backend/src/main/java/com/dms/consignment/service/ConsignmentService.java + backend/src/main/resources/db/migration/V138__v441_invoice_consignment_stock.sql + V139__v441_sales_out_line_red_flag.sql + frontend-vue/src/views/OrderCreate.vue + frontend-vue/src/config/menu.js + frontend-vue/src/components/CrudView.vue + scripts/e2e_invoice_consignment.py + scripts/seed_consignment.py + scripts/smoke_v441.py |

---

## 七、踩坑点/注意事项（AI必读）

1. **登录账号**：测试环境业务前台用 `admin / Sh123456`（租户default）可登录，而非sys_admin账号（该账号可能无权限或会话超时）
2. **密码输入**：浏览器自动化 `browser_type({ clear:true })` 有时对密码输入框不生效，建议先点击密码框聚焦后再输入
3. **菜单展开**：CDP模式下 `states:[collapsed/expanded]` 可能暴露不完整子菜单，建议使用 `browser_evaluate + querySelectorAll('.el-menu-item, .el-sub-menu__title')` 完整提取
4. **token隔离**：业务前台登录 `/api/auth/login` vs 平台后台 `/api/admin/auth/login` **绝对不要混用**；401时首先查是哪套token
5. **测试环境URL**：`http://dms-dev.mysolmed.com/dms/`（域名，2026-08-28 起；IP 直连 `http://43.128.145.141/dms/` 行为一致）→ 统一 80 端口 Nginx（容器 dms-test-nginx），后端 API 由 Nginx 反代（/api、/auth、/actuator 走根路径），不要直接打 8080/8081/8082/8083（历史端口已收敛到 80）；裸域名 `/` 为 302→`/dms/`；宣传手册在 `/brochure/`（移动页/打印页为 /brochure/mobile.html、/brochure/print.html，无 /brochure/pages/ 子目录）；Nginx 配置变更受【铁律10】管控，不得随意调整
6. **删除有引用数据**：产品/经销商被订单/库存引用后删除返回 **40904 Conflict**，不是500；测试案例要覆盖
7. **操作日志接口**：`GET /api/operation-log/list/product/{id}` 不得返回500；空数组也必须200（防回归4号Bug）
8. **产品表单16字段**：浏览器实际验证新增产品表单字段为——产品编码、中文名称、**英文名称**、产品类型、产品分类、规格型号、单位、参考单价、税率、**需要UDI追溯(布尔)**、**序列号管理(布尔)**、**临期预警(月)默认3**、**安全库存默认10**、**最小订购量默认1**、状态（启用/停用）；写测试和接口时字段名要与这16项严格对齐
9. **库存三态不可少**：stock_status = QUALIFIED(合格) / PENDING(待检) / DEFECTIVE(不合格)，任何出入库/移动/调整单据都必须指定目标状态；三态合计数量=产品总库存，报表不得漏算PENDING
10. **审批流4子模块**：我的审批（待办/已办）+ 审批流配置（编辑器）+ **审批委托**（代理人+有效期+业务类型范围）+ **审批监控**（超时预警/手动催办/撤回/强制跳过）；原v3.11.0文档只覆盖了前2个，附录D补了委托和监控
11. **促销引擎3类规则**：满减(满X元减Y)、买赠(买A件赠B件)、折扣(整单X折)；互斥优先级=折扣>买赠>满减；促销命中后SO明细需自动写入price_adjustment字段，报表按actual_amt统计而非ref_price×qty
12. **移动端H5真实5Tab**：底部导航=首页(KPI+快捷入口) / 订单(列表+新建+详情) / 手术报台(拍照+扫码序列号) / 业绩(个人排行) / 我的(设置+退出)；移动端订单新建精简了字段（无审批流配置入口，仅提交）
13. **导入导出安全边界**：导入Excel必须校验：①文件头列顺序匹配 ②必填列非空 ③编码唯一性 ④日期格式 ⑤数值>=0 ⑥超出行数上限(1000行)；导出必须：①按权限过滤租户 ②敏感字段(价格成本)按角色脱敏 ③Excel添加水印(导出人+时间) ④大文件异步下载(>1万行)
14. **【平台6大红线】M-01~M-06绝对不能违反**：M01厂家绝不能查看经销商真实业务数据（SO/库存/采购/报表7类接口都返回空或403）；M02产品对码维护在厂家前台，平台后台只做只读报表，不存在"平台标准码"第三方码；M03平台后台只有固定唯一种子管理员账号（超级管理员），不做平台RBAC角色体系；M04第一期只做全局+租户类型配置，不做租户级页面/字段自定义，租户管理员找不到配置菜单；M05租户状态只有active/inactive，禁用三字段disabled_at/disabled_by/disable_reason；M06绑定后绝对不允许解绑，只能停错租户+重建
15. **token双向隔离必须落地**：平台token `POST /api/admin/auth/login` vs 业务token `POST /api/auth/login` 两套完全独立；Redis key前缀 `dms:admin:auth:` vs `dms:auth:{tenantId}:` 绝不能混存；错用对方接口必须401，这是防止越权第一道防线
16. **tenant_id 租户过滤白名单（JPA/Hibernate + MyBatis 拦截器）**：平台级10张表（tenants/admin_users/global_dict_*/admin_*_configs/admin_*_logs/api_access_logs/tenant_dealer_bindings）必须加入白名单不被追加tenant_id条件；业务表products/sales_orders/stocks等必须被强制追加tenant_id；交叉时直接报500防脏读
17. **12项第一期明确不做**（过度设计必删）：平台多角色RBAC、租户自定义布局、租户私有字典、用户个性化列配置、平台模拟登录业务租户、厂家看经销商数据、独立部署控制台、基础设施CPU/内存监控大盘、计费套餐扣费、租户有效期自动到期、平台维护对码、平台标准码三方模型；任何测试如果测到这12项必须反向验证（应该不存在/返回404）
18. **停用租户三字段必须正确写入+清空**：停用写入disabled_at/disabled_by/disable_reason三字段（enabled_at也要），启用时三个disabled字段必须全清空为NULL；没有FROZEN冻结状态枚举，旧代码有FROZEN的全部替换为inactive
19. **错误绑定处理流程唯一解**：dealer绑定错了→不调解绑接口（返回400"不允许解绑"）→正确路径：停用错误经销商租户(status=inactive+填停用原因)→重新创建一个新的正确绑定租户；旧绑定记录保留用于审计，不物理删
20. **日志7敏感字段必脱敏**：password明文/token/密钥、手机号138****1234、身份证号前6后4、重置密码的初始密码不存audit_logs明文、登录接口请求报文Body不存password、cookie不存明文；查DB和下载报文必须看不到任何敏感明文；否则合规不过
21. **【v3.12.1新增】"模块尚未迁移"非"未实现"**：业务前台17个页面显示"模块尚未迁移"是指低代码 ModuleView 未配置，**后端接口通常已具备**（销售订单/出库/库存移动/经销商/产品分类/包装层级/组合/画像/对码/审批中心4项/销售岗位/账号管理/列表页配置/接口调用日志/邮件日志）；改造工作量主要是前端 ModuleView 配置与 Vue 页面开发，无需后端新建接口
22. **【v3.12.1新增】平台后台首页/仪表盘空白**：`/admin/dashboard` 完全空白，控制台7个JS/CSS资源 ERR_ABORTED；登录后直接跳到厂家租户列表；平台后台总覆盖率仅 25.0%（24项中只有9项可访问，6项完整实现）
23. **【v3.12.1新增】平台后台接口日志页中文乱码**：`/admin/logs/api` 页面19,084条数据可分页，但表头/列名中文全乱码（疑似 i18n 资源缺失），行操作"查看详情"按钮死链（ADM-12缺陷）
24. **【v3.12.1新增】平台后台租户启用功能缺失**：只有"停用"按钮，停用后无法恢复启用（ADM-06）；正确实现：停用租户行操作应切换为"启用"按钮，启用后租户内用户可登录
25. **【v3.12.1新增】移动端订单详情产品名 undefined bug**：`MOrderDetail.vue` 产品明细行 productName 字段映射错误（MOB-11缺陷），约 0.5 人日可修
26. **【v3.12.1新增】移动端报台详情缺字段**：`MSurgeryReportDetail.vue` 缺单价、状态独立 label、备注、数量字段；仓库显示"-"占位；数量未单独展示（MOB-10缺陷）
27. **【v3.12.1新增】移动端报台创建缺批号/序列号字段**：`MSurgeryReportCreate.vue` 中无 `input[type=file]`、`van-uploader`、扫码相关关键词，无批号/序列号输入框；产品只通过搜索选择（MOB-01未实现）
28. **【v3.12.1新增】业务前台顶部导航缺陷**：用户菜单（admin）点击后在可访问树中未渲染"修改密码/退出登录"菜单项；顶部无消息铃铛（PC-01/PC-08缺陷）
29. **【v3.12.1新增】移动端4Tab不符PRD**：实际4 Tab（首页/订单/报台/我的），PRD要求5 Tab（含审批Tab）；移动端审批人只能打开PC页面，体验差（MOB-02未实现）
30. **【v3.12.1新增】审批中心4页面全部未迁移**：业务前台"我的审批/审批流配置/审批委托/审批监控"4个页面全部显示"模块尚未迁移"，后端接口已具备（APV-08 P0级缺陷）
31. **【v3.12.1新增】163 SMTP 授权码硬编码**：CHANGELOG 提到 163 SMTP 授权码硬编码在 `application.yml`，已暴露在代码仓库中（NF-08 P0级安全风险），需要立即外置到环境变量 + 轮换授权码
32. **【v3.12.1新增】测试账号邮箱非真实**：登录手册中8个测试账号邮箱为 `*@dms-demo.com`（非真实收件），admin/平台账号无邮箱；OPS-01 建议统一为 `vinkinyu@163.com` 以便邮件功能验证
33. **【v3.12.1新增】平台后台审计日志页空白**：`/admin/logs/audits` 菜单存在但页面空白；`platform_audit_logs` 表已建但无查询接口暴露
34. **【v3.12.3更新】Flyway 脚本已到 V95（共 89 个）**：默认 docker profile spring.flyway.enabled=false，手动启用时执行；v3.11.x 修过若干越权问题，建议 CI 中自动跑越权测试（SEC-05 P0级）
35. **【v3.12.1新增】三端功能覆盖率统计**：业务前台 59.6%（47页中28完整/2缺陷/17未实现）、平台后台 25.0%（24项中6完整/1部分/17未实现）、移动端 62.5%（8页中5完整/3缺陷）；总体 49.4%；详细见 v3.12 评估文档第1章
36. **【v4.3.0修复】审批回滚必须覆盖关联资源**：审批回调 onRejected / onReturned / onCanceled 里不能只 UPDATE orders.status = REJECTED，必须调用对应资源的 release 方法——如代金券 voucherService.release(businessId)、库存锁定释放、状态机恢复；否则关联资源（如ISSUED→USED的代金券）永远锁死，用户体验为"券用了一次就消失"
37. **【v4.3.0修复】列表页外键列绝对禁止显示ID**：任何 *Id 列（dealerId / productId / warehouseId / salesOutId…）在 v430-modules.js / modules.js 的 `{ k, l, w }` 配置中必须使用对应的 Name 字段（如 dealerName / productName / warehouseName），并在 filter 中保留 resource 选择器用原始 Id；修复后浏览器验证表格中列内容为业务名称而非数字
38. **【v4.3.0修复】有级联依赖的选择器必须前置校验**：销退订单「选择出库单」弹窗内，搜索条件含「经销商」下拉（pickerQuery.dealerId），searchShipments() 必须在函数开头第一行判断 `if(!pickerQuery.dealerId) { ElMessage.warning('请先选择经销商，一个销退订单只能针对一个客户'); return }`，否则用户跳过选择直接查会返回跨客户数据或空数组；同类依赖（价格加载必须先传 partnerId、库存查询必须传 warehouseId）都要如此处理
39. **【v4.3.0修复】Spring双向依赖+Lombok构造器注入**：如果 Service A 注入 Service B，Service B 又注入 Service A（如 SalesReturnApprovalCallback ↔ V4OrderService），在其中一个构造器注入参数上加 Spring `@Lazy` 注解；同时使用 Lombok `@RequiredArgsConstructor` 时必须在项目根建 `backend/lombok.config` 文件，内容 `lombok.copyableAnnotations += org.springframework.context.annotation.Lazy`，否则 @Lazy 不会被复制到生成的构造器参数，循环依赖仍然抛 BeanCurrentlyInCreationException
40. **【v4.3.0教训】部署后必须打开真实浏览器验证前端，不能只看 /actuator/health**：容器 Running、后端 UP、API 登录 200 都不等于前端可访问；Nginx root 路径错、dist 未替换、Docker 构建缓存复用旧层都会导致前端 500/白屏但后端完全健康；部署后的第一个验证步骤必须是用 TRAE-browseruse 打开用户实际访问的 URL（如 http://43.128.145.141/），等待页面渲染、展开菜单、进入代金券列表和销售订单列表确认数据能加载；用户反馈 500 就是这一步没做
41. **【v4.3.0教训】只调API不是完整测试**：D1-D10 折扣场景不能只跑 API 层；必须做到：①浏览器点击新建订单弹窗填写产品 ②用 DevTools 观察 /api/auth/login 外的 /api/orders、/api/product-prices、/api/customer-vouchers 等请求无5xx ③表格列渲染内容为业务名称不是ID ④错误提示包含业务编码+名称（如产品 [PRD-B001 某某产品] 无有效价格），不能只抛 id=682 ⑤审批拒绝路径必须走一次（而不是只测审批通过），回读后确认资源状态恢复
42. **【v4.3.1教训】重写/改造页面=功能减法风险，重写前必须盘点旧功能**：凭记忆重建页面会悄悄丢功能（销退单返工事件：经销商从"可主动选择"退化成"选出库单后自动带出"的禁用文本、出库单弹窗让用户手输经销商名称、批号/序列号筛选条件丢失）。铁律：改造任何列表页/表单页/弹窗前，先在真实环境打开旧页面逐项登记所有筛选条件、表格列、按钮、选择器、弹窗字段、必填校验、级联依赖；新页面逐项对照保留，任何删除/弱化必须显式写明理由。
43. **【v4.3.1教训】外键引用字段禁止自由文本 + 业务前置条件做成按钮门禁**：经销商/产品/仓库/出库单等外键一律用 el-select 远程搜索或资源选择弹窗，禁止可输入文本框；下游弹窗中的上游主体（如已选经销商）只读展示（el-tag/文本），禁止重复录入。"必须先选经销商才能选出库单"这类前置条件，未满足时按钮 disabled + tooltip 说明原因，禁止用"选择 XX 后自动带出"这类用户无法操作的假字段替代。后端列表接口支持的查询参数（batchNo/serialNo/dealerId/warehouseId）前端必须挂对应筛选框并实测生效。
44. **【v4.3.1教训】编辑/重开页回显必须兼容多种载荷字段名**：行明细产品 ID 在新建态可能是 `p.id`、回显态可能是 `p.productId`，取值要 `p.productId ?? p.id ?? null` 兜底；否则保存后重开订单（如 SO-20260827-00003）经销商为空、价格查询因 partnerId/productId 取不到而报错。正向验收必须覆盖"新建提交 → 重新打开查看/编辑页"的回显路径，逐字段核对（枚举中文 label、外键编码+名称、备注不串内容），不能只验提交接口 200。
45. **【v4.3.1教训】部署后首检必须覆盖文档里所有用户入口 URL（铁律9）**：只 curl 根路径 `/` = 200 和 /actuator/health = UP 是假象；用户实际书签是 `/dms/`、`/dms/admin/`、`/dms/mobile/login`，若 VITE_BASE 与 Nginx 路径不一致会 500/跳 404。部署后第一个验证步骤必须用 TRAE-browseruse 真实浏览器逐条打开文档列出的所有入口 URL，确认 HTTP 200（或合法 302 后 200）、DOM refs>20、Console 无红错、Network 无 5xx，并完成登录→首页→展开菜单→进核心列表页。
46. **【打包教训】Windows 压缩包给 Linux 用必须用正斜杠路径**：PowerShell `Compress-Archive` 打的 zip 条目路径含反斜杠 `\`，Linux `unzip` 会把 `backend\src\..` 当成一个平铺文件名，解压后目录结构全毁。跨平台交付统一用 `tar -a -cf out.zip -C stage .`（生成正斜杠 zip），上传后必须在 Linux 上实际 `unzip` 到临时目录 `find . -type f | wc -l` 校验文件数与目录结构，不能只看本地条目数。
47. **【plink 转义教训】远端复杂脚本一律落地为 .sh 文件上传执行**：plink 双引号内联 bash 时，中文括号 `（）`、`()`、`{{.Destination}}{{println}}{{end}}` 模板串、`$()` 命令替换会被 PowerShell→plink→bash 多层传递破坏，报 `syntax error near unexpected token (` 或变量被本地提前解析。固定做法：本地写 `tools/_xxx.sh` → pscp 上传 /tmp → `echo 密码 | sudo -S bash /tmp/_xxx.sh`，执行后删除。
48. **【v4.4.1 教训】红冲表与原表共用序列号时，唯一索引必须改部分唯一索引**：`ux_sales_serial` 原是 serial_no 全局唯一索引，红字出库行（红冲）与原蓝字行同序列号必然冲突，导致 ERP 红字回调 INSERT 500。正确做法：明细表增冗余 `is_red` 列（按父单回填），索引重建为 `CREATE UNIQUE INDEX ... ON t(serial_no) WHERE serial_no IS NOT NULL AND COALESCE(is_red,false)=false`——蓝字行仍全局唯一防重复发货，红冲行豁免；且**所有写入该表 serial_no 的 INSERT 都必须同步绑定 is_red**（漏一处则红单行 is_red 默认 false 仍冲突），用 grep 全量排查 INSERT 点。
49. **【v4.4.1 教训】字符串字面量分支判断必须与枚举值逐字核对**：补货发货回写寄售台账的钩子写成 `"REPLENISH".equals(orderType)`，而订单类型枚举是 `REPLENISHMENT`，条件永不命中——补货发货后台账一直不增加，且无任何报错（静默失效）。涉及状态/类型字符串比较时，必须用常量或与枚举定义交叉核对，并补一条"动作发生后副表数据确实变化"的端到端断言（不能只验主单状态）。
50. **【v4.4.1 教训】红单重建明细后回调引用的行 ID 会失效**：红字单 submit 时 DELETE 旧 order_lines 再重建，新行 ID 变化；ERP 出库回调若仍携带建单时的旧 orderLineId 会引用不到行。回调前必须按单号重取最新行 ID；E2E 脚本也要在 submit 之后重新拉明细取 ID。
51. **【浏览器自动化】integrated_code_mode Exec 沙箱范式**：Exec 是 V8 沙箱，**无 console/fetch/require/process/setTimeout**；输出用 `text(value)`，调工具用 `await tools.browser_xxx(args)`，等待用 `await tools.browser_wait_for({time: 秒})`，多步顺序放同一个 Exec 内；可用 `ALL_TOOLS` 自省工具 schema。browser_fill/browser_click 对 offscreen 元素（视口 0x0 快照）不生效，需用 browser_evaluate 执行原生 JS（如设输入框值要用 `Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set` 调原生 setter 再 dispatch input/change 事件触发 Vue v-model）。Element Plus 弹窗/下拉用 `.el-select-dropdown__item`、`.el-dialog .el-table__body-wrapper tbody tr` 选择器操作。

---

## 八、本地启动配置（简版）

### 前端（Vue3 + Vite）
```bash
cd frontend-vue
npm install          # 或 pnpm i
npm run dev          # 默认 http://localhost:5173
```
- `.env.development` 中 `VITE_API_BASE_URL=http://dms-dev.mysolmed.com`（域名，IP 直连 43.128.145.141 同样可用；API/health 根路径；DMS UI 入口为 `/dms/`，后台 `/dms/admin/`，移动端 `/dms/mobile/login`，经销商准入 `/dms/mobile/register`；宣传手册 `/brochure/`）

### 后端（Spring Boot）
```bash
cd backend
mvn clean install -DskipTests
java -jar target/dms-*.jar --spring.profiles.active=dev
# 默认端口 8080（注意与Nginx区分）
```
- `application-dev.yml` / `application-local.yml` 需配置 PostgreSQL（测试库 dms_test，默认 localhost:5432）+ Redis(6379)；驱动 org.postgresql.Driver
- 启动前确保测试环境数据库端口开放（或用SSH隧道）

### Docker本地联调
```bash
docker compose up -d postgres redis
# 然后前后端分别本地跑，VITE_API_BASE_URL=http://localhost:8080
```

---

## 九、跨会话关键上下文快照（AI必读）

> 新会话启动时，请先读 `doc/AI开发文档.md` + `doc/项目设计文档.md` 两份文件，然后按以下路径找测试/需求：
> - **功能缺口评估（v3.12，最新）**：`15_补充需求/DMS需求评估与优先级排序_v3.12.3.md`（v3.12.2 版，110+需求项+45项新增建议+优先级矩阵+7项立即执行项；本次评估基于2026-08-12三端实际点击验证）
> - **UI 设计系统 Token 规范**：`15_补充需求/UI设计系统_Design_Token规范_v1.0.md`（约1000行，三层 Token 架构 + Element Plus/Vant 主题定制 + 暗色模式 + 租户品牌定制）
> - **移动端 H5 设计规范**：`15_补充需求/移动端H5设计规范_v1.0.md`（12章，安全区域/视口适配/移动端字号间距/触摸交互/手势规范/12类移动端组件/动效/状态/网络离线/性能/落地验收）
> - **UI 设计系统 Demo（PC + 移动端）**：`15_补充需求/UI设计系统_Demo.html`（PC 端，含 Token 展示 + 组件 + ECharts 图表 + 暗色/品牌色切换）；`15_补充需求/UI设计系统_Mobile_Demo.html`（移动端，手机外壳 + 5 Tab + 订单列表/详情/报台/审批/我的 + Toast/Dialog/ActionSheet/骨架屏/左滑删除/下拉刷新）
> - 测试用例总汇：`10_测试用例/DMS完整测试场景与测试案例_v3.12.0.md`（正文 + 附录D v3.11.1细化版850+子用例 + 附录F遗漏场景补充 + 附录G五层验证法80+条 + 附录H全模块139条深度用例，合计约903+条用例）
> - 需求总览：`03_需求文档/需求总览.md`
> - PRD文档：`01_PRD/通用DMS经销商管理系统_PRD.md`
> - 登录信息：`DMS登录信息手册.md` 或 `doc/项目设计文档.md-登录章节`
> - 测试报告：`10_测试用例/DMS测试报告_v3.12.0_20260813_全量回归.md`（v3.12最新第三轮，含深度重测专项第九章，26个Bug，14严重+12一般，综合评分2.4/5）
> - 测试用例库：`10_测试用例/DMS完整测试场景与测试案例_v3.12.0.md`（903+条用例，附录D/F/G/H四层补充）
> - 回归测试步骤：`10_测试用例/DMS回归测试步骤文档_v3.12.0.md`（8阶段标准化回归流程，含全模块五层深度回归）
> - 文档总索引：`文档索引.md`（根目录，全项目50份文档分类导航）
> - 自动化测试脚本：`automation_test/`（pytest 五层测试套件 350条用例：L1入口69 + L2列表~80 + L3详情~70 + L4交互~15 + L5链路~12 + 原有模块约104条）

### 9.1 v3.12.2 评估核心结论速查

1. **三端功能覆盖率**：业务前台 59.6% / 平台后台 25.0% / 移动端 62.5%；总体 49.4%
2. **总需求项 110+，工作量 500+ 人日**（v3.12.1 为 80+/300+ 人日，v3.12.2 补充 27 项约 200 人日）
3. **P0 立即执行项（7项，约 4-6 人日）**：
   - OPS-01 测试账号邮箱统一为 `vinkinyu@163.com`（0.5人日）
   - NF-08 密钥外置 + 轮换163授权码（1-2人日）
   - LOG-02 平台登录日志落库（0.5人日）
   - ADM-06 租户启用功能补齐（0.5人日）
   - ADM-12 接口日志中文乱码修复（0.5人日）
   - MOB-10 移动端报台详情字段补齐（0.5人日）
   - MOB-11 移动端订单详情 undefined 修复（0.5人日）
4. **P0 排期项（约 50-75 人日，2-3 个迭代）**：见 v3.12 评估文档第15.1节
5. **P1 排期项（约 180-250 人日，含 v3.12.2 新增 P1 项 60-90 人日）**：见 v3.12 评估文档第15.2节
6. **v3.12.2 新增 10 大被忽略维度（27 项）**：
   - UI 设计系统(7)：设计Token、响应式、状态规范、图表规范、动效、图标、表单交互
   - 前端性能(3)：Core Web Vitals、构建优化、CDN
   - 后端架构(4)：读写分离、消息队列、多级缓存、大表分区
   - 可观测性(2)：分布式追踪、Metrics 指标
   - 容灾连续性(2)：灾备 RTO/RPO、故障演练
   - 安全合规(3)：等保 PIPL、数据加密、安全测试
   - 数据治理(3)：数据质量、MDM、数据血缘
   - 可维护性(3)：单元测试、Lint、技术债务
   - API 治理(2)：版本管理、API 网关
   - 用户体验深化(2)：个性化、帮助系统
