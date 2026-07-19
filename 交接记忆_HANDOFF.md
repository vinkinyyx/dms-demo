# DMS 交接记忆（换机继续开发用）

> **本文件用途**：换电脑后，把整个 `DMS` 项目文件夹拷到新机，打开后先读本文件即可快速恢复上下文。
> **最后更新**：2026-07-19 · **当前版本 v3.4.15** · **镜像 dms-backend:3.4.15** · **Flyway 到 V18**
> ⚠️ 本文件含服务器凭据，属敏感信息，请勿提交到公开仓库 / 外传。

---

## 0. 一句话现状

一个多租户 DMS（经销商管理系统），Spring Boot 3 + Java 17 + PostgreSQL 14 + Redis 7 + 原生 JS 前端 + Nginx，**已部署在阿里云并正常运行**。开发方式是"本地改代码 → pscp 上传服务器 → 服务器 docker build → docker run → 跑 test-*.sh 测试"。当前迭代到 **v3.4.15**，8 条需求全部完成、测试全绿。

---

## 1. 服务器与访问（关键）

| 项目 | 值 |
|---|---|
| 服务器 IP | `<SERVER_IP>` |
| SSH 用户 | `root` |
| SSH 密码 | `<SSH_PASSWORD>` |
| 前端入口 | http://<SERVER_IP>/ |
| 后端健康检查 | http://<SERVER_IP>:8080/actuator/health |
| SSH hostkey | `<SSH_HOSTKEY>` |

**演示账号**：租户代码 `default` · 账号 `admin` · 密码 `Sh123456`

**数据库**（容器内网名 `dms-postgres`）：db=`dms` user=`dms` pwd=`<DB_PASSWORD>`，端口 5432。

### 容器结构（docker network: dms-net）
```
dms-nginx     nginx:1.25      :80    静态前端 + /api 反代到 8080
dms-backend   dms-backend:3.4.15  :8080  Spring Boot 3 + Java 17
dms-postgres  postgres:14     :5432  业务库（卷 dms_pgdata）
dms-redis     redis:7         :6379  缓存/Session/审批Token（内网）
```

---

## 2. 新电脑首次准备（按顺序做）

1. 安装 **JDK 17**、**Maven 3.9+**（本地编译/IDE 用）、**Node.js**（仅用于前端 JS 语法校验）、**Docker Desktop**（可选，本地起库用）。
2. `tools/plink.exe`、`tools/pscp.exe`（PuTTY 客户端）已随项目携带，**Windows 直接用**；换 mac/Linux 则改用系统自带 `ssh`/`scp`。
3. 打开 TRAE，工作目录设为项目根 `DMS/`。
4. 读 `.trae/rules/project_rules.md`（**每次需求都要遵守的流程铁律**）。
5. 需要离线开发时，可在本地起 PG 并导入 `docs/05_数据库设计/schema_export/dms_schema.sql`（见 §6）。

---

## 3. 标准部署流程（本次会话验证过，照抄即可）

> 所有命令在**项目根目录**的 PowerShell 里跑；`plink/pscp` 在 `tools/` 下。

### 3.1 上传改动的后端源码
```powershell
cd tools
.\pscp.exe -batch -r -hostkey "<SSH_HOSTKEY>" -pw <SSH_PASSWORD> ..\backend\src\main\java\com\dms root@<SERVER_IP>:/root/dms/backend/src/main/java/com/
# 单个文件示例：
.\pscp.exe -batch -hostkey "..." -pw <SSH_PASSWORD> ..\backend\src\main\java\com\dms\xxx\XxxController.java root@<SERVER_IP>:/root/dms/backend/src/main/java/com/dms/xxx/
# 新增 Flyway 迁移：
.\pscp.exe -batch -hostkey "..." -pw <SSH_PASSWORD> ..\backend\src\main\resources\db\migration\V19__xxx.sql root@<SERVER_IP>:/root/dms/backend/src/main/resources/db/migration/
```

### 3.2 上传前端（Nginx 直接读，无需 build）
```powershell
.\pscp.exe -batch -hostkey "..." -pw <SSH_PASSWORD> ..\frontend\workspace.html ..\frontend\admin.html root@<SERVER_IP>:/root/dms/frontend/
```
> 前端改完刷新浏览器（Ctrl+F5）即可生效。

### 3.3 构建镜像（含去 BOM，避免 Java 编译报错）
```powershell
.\plink.exe -ssh -batch -hostkey "..." -pw <SSH_PASSWORD> root@<SERVER_IP> "cd /root/dms/backend && find src -name '*.java' -exec sed -i '1s/^\xEF\xBB\xBF//' {} + && docker build -t dms-backend:3.4.16 . > /root/dms/build.log 2>&1; echo DONE=$?; grep -E 'BUILD SUCCESS|BUILD FAILURE' /root/dms/build.log"
```
> **坑1**：Windows 编辑的 Java 文件可能带 UTF-8 BOM，导致 `javac` 报错，所以 build 前先 `sed` 去 BOM。
> build 耗时约 1.5–2 分钟。

### 3.4 重启容器（会自动跑新的 Flyway 迁移）
```powershell
.\plink.exe -ssh -batch -hostkey "..." -pw <SSH_PASSWORD> root@<SERVER_IP> "docker stop dms-backend && docker rm dms-backend && docker run -d --name dms-backend --network dms-net -p 8080:8080 -e SPRING_PROFILES_ACTIVE=docker -e DB_HOST=dms-postgres -e DB_PORT=5432 -e DB_NAME=dms -e DB_USER=dms -e DB_PASSWORD=<DB_PASSWORD> -e SPRING_DATA_REDIS_HOST=dms-redis -e SPRING_DATA_REDIS_PORT=6379 -e TZ=Asia/Shanghai -e JAVA_OPTS='-Xms256m -Xmx512m -XX:+UseG1GC -Duser.timezone=Asia/Shanghai' --restart unless-stopped dms-backend:3.4.16 && sleep 38 && curl -s -o /dev/null -w 'HEALTH=%{http_code}\n' http://localhost:8080/actuator/health"
```
> **坑2**：后端启动约 38–45 秒，太早 curl 会 HEALTH=000，多等即可。
> 期望结果：`HEALTH=200`。

### 3.5 跑测试（回归 + 当前版本）
```powershell
.\plink.exe -ssh -batch -hostkey "..." -pw <SSH_PASSWORD> root@<SERVER_IP> "cd /root/dms && bash test-v34-full.sh 2>&1 | tail -3 && bash test-v3415-extra.sh 2>&1 | tail -3"
```
> 测试脚本上传后要先 `sed -i 's/\r$//'` 去掉 Windows 换行再跑。

---

## 4. 技术栈与代码结构速览

- **后端**：`backend/src/main/java/com/dms/`，按业务域分包（auth/authz/contract/order/sales/inventory/masterdata/promotion/rbac/report/system/...）。多数列表/详情接口用 **EntityManager 原生 SQL**（不是纯 JPA），改列表字段时注意直接改 SQL。
- **Flyway 迁移**：`backend/src/main/resources/db/migration/V1..V18`。**已应用的迁移不能改**（校验和冲突），新改动一律加新版本号。
- **前端**：纯静态 + 原生 JS，无构建。核心是 `frontend/workspace.html`（业务工作台，~180KB，单文件含全部菜单/表单/列表/下单/库存移动逻辑）、`admin.html`（后台管理）、`dms-lib.js`（DMS.api/toast/fmt 等共享库）。
- **多租户**：`TenantContext` + `TenantInterceptor`，几乎所有查询带 `tenant_id`。演示租户 id=`11111111-1111-1111-1111-111111111111`。
- **鉴权**：JWT（`security/JwtFilter`）。

### 前端几个关键约定（改前端必看）
- 菜单硬编码在 workspace.html 的 `MENU_GROUPS`；v3.4.15 起支持后台 `menu_configs` 覆盖分组（`loadMenuOverrides` 启动加载）。
- 多标签页系统：`_tabs` + `_contentSnapshots`；表单页签用 `filter.__mode`，自定义页（下单/库存移动）用 `filter.__custom`。
- 列表列定义里 `k` 指向字段名；显示名称而非 ID 时，需**后端返回 xxxName 字段**（前端无字典翻译能力）。

---

## 5. 已完成迭代 & 关键坑（避免重复踩）

| 版本 | 主要内容 |
|---|---|
| v3.4.12/13/14 | 单据号序列、执行明细留痕、操作日志、时区统一、编码可改、表单控件化、列表后端筛选(SpecUtil)、部分收货可取消、库存调整只读、出库禁手输 |
| **v3.4.15（最新）** | ①未保存单据切页签恢复 ②全站 ID→名称 ③授权补 GET/{id} ④价格空日期修复 ⑤库存移动仓库下拉过滤软删除 ⑥库存移动三种选择方式 ⑦销售授权 check 认分类 ⑧仓库测试数据精简 ⑨后台菜单管理 |

**技术坑清单**：
1. **`?::DATE` 会被 Hibernate 6 误解析**为命名参数报 "Ordinal parameter label was not an integer"→ 一律用 `CAST(? AS DATE)`。
2. **空字符串日期**传给 date 列会 `''::DATE` 报错 → 用 `blankToNull()` 转 null。
3. **lookup 下拉走原生 SQL 绕过实体 @SQLRestriction**，默认不过滤软删除 → 需显式加 `deleted_at IS NULL`（已修 warehouses/dealers/hospitals/categories/products）。
4. **列表只显示 ID** 的根因是后端 SQL 没 JOIN 名称；已批量补 dealerName/warehouseName/partnerName 等。
5. Windows BOM / CRLF 问题：Java build 前去 BOM，shell 脚本跑前去 CRLF。
6. 授权 `category_ids`/`terminal_ids` 是**逗号分隔字符串**存储。

---

## 6. 本地起库（离线开发，可选）

```powershell
docker run -d --name pg-dms-local -e POSTGRES_USER=dms -e POSTGRES_PASSWORD=<DB_PASSWORD> -e POSTGRES_DB=dms -p 15432:5432 postgres:14-alpine
Start-Sleep -Seconds 6
docker exec -i pg-dms-local psql -U dms -d dms < "docs\05_数据库设计\schema_export\dms_schema.sql"
```
> 注意：schema_export 里当前只有 `dms_schema.sql`（结构）。要连真实数据，建议从服务器现导：
> `docker exec dms-postgres pg_dump -U dms -d dms > dump.sql`（在服务器上），再 pscp 拉回本地。

---

## 7. 文档必更清单（每次迭代都要同步，铁律见 project_rules.md）

改完需求后**必须**同步这 7 份（就地合并，不新建文件），且版本号/Flyway/镜像tag/测试数保持一致：
1. `docs/03_需求文档/需求文档.md`
2. `docs/04_功能详细设计/功能详细设计.md`
3. `docs/02_需求分析/需求分析_UserStory.md`（附录追加用户故事）
4. `docs/05_数据库设计/数据库设计.md`
5. `docs/06_API设计/API接口清单.md`
6. `docs/09_测试报告/测试报告.md`
7. `docs/README.md`

---

## 8. 待办 / 下一步候选

- [ ] （可选）统一"分类授权"在 lookup 下拉与 check 校验的有效期口径（valid_from/valid_to 的 NULL 兜底略有差异）。
- [ ] 采购单/采退单表单的供应商选择器当前 resource 疑似指向 dealers，应确认是否改 suppliers（本轮未动，属发现项）。
- [ ] 前端表单尚未读 `form_configs` 做真正的动态渲染（低代码价值待释放）。
- [ ] 附件/PDF 目前本机存储，生产需接 OSS。
- [ ] 弱密码 `<DB_PASSWORD>` / 公网暴露，仅演示可接受，生产必改。

---

## 9. 每次开工前建议

1. 先 `curl http://<SERVER_IP>:8080/actuator/health` 确认服务在线。
2. 读本文件 §3 回忆部署命令；读 `.trae/rules/project_rules.md` 回忆流程铁律。
3. 收到新需求→**先理解、有疑必问、逐条清单跟踪、完成后整体复检**（这是硬性规则）。
4. 参考最近一次会话的做法：先并行只读调查根因（可用 search 子代理），与用户确认歧义点，再动手。

---

## 10. 打包前的清理记录（2026-07-19）

已删除 `tools/` 下 49 个**一次性调试/修复脚本**（使命已完成，不再需要）：
- `dbg-*.sql` / `dbg-*.sh` / `dbg3415.sh`（各种临时排查）
- `fix-*.sql` / `fix-*.sh`（V15/V17 Flyway 修复、租户id修复、密码修复、前缀修复等）
- `del-v15.sql` / `register-v15.sql` / `v15_step1.sql` / `v15_step2.sql`（V15 迁移手工干预）
- `d-orders.sql` / `d-products.sql` / `d-tables.sql`（临时删数据）
- `chk-v17.sql` / `check-*.sql` / `check-dict.sh`（临时检查）
- `debug-inv.sh` / `debug-orders.sh`
- `seed-serial-inventory.sql` / `seed-stock-status.sql` / `seed-stock-status.sh`（一次性造数据，已并入 Flyway seed）

**保留的可复用资产**：`plink.exe`/`pscp.exe`、`deploy-*.sh`、`clean-disk.sh`/`deep-clean.sh`、`pull-images.sh`、`daemon.json`、全部 `test-v*.sh` 版本化回归脚本、`e2e-test.sh`/`ui-e2e-test.sh`。

根目录整洁，无遗留 zip/日志。新增交接文档 2 份：
- `交接记忆_HANDOFF.md`（本文件）
- `docs/需求提示词存档.md`（历次需求原始提示词）
