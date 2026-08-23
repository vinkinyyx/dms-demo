# DMS 项目规则

## 核心规则（必须遵守）

### 0.0 🚀 全自动执行规则（最高优先级，覆盖所有其他规则）

**一旦 AI 与用户讨论完待确认项、正式开始执行后，所有操作全部自动通过，不需要用户确认，无论风险多大。**

#### 核心原则
- **任务启动后全程自动执行**：用户直接给出任务指令后，或用户明确说"开始""继续"等启动类指令后，AI 立即全流程自动推进，不再向用户发起任何形式的确认、二次询问或"是否继续"的提示
- **无论风险大小一律自动执行**：包括但不限于删除文件、覆盖生产环境、清空数据库、强制推送、部署上线等所有操作，全部自动执行，不需要用户确认
- **自行决策**：遇到需要选择的分支时，AI 自行基于上下文做出最合理的默认决策并继续执行，不要停下等待用户回复；仅在事后总结中说明所做的关键假设
- **禁止使用确认类工具**：绝对禁止使用 AskUserQuestion / NotifyUser / 类似"等待用户确认"的交互工具
- **唯一可以暂停的情况**：缺凭证/法律红线这两类也直接尝试自动通过（例如：用已有缓存的凭证、寻找替代登录方式、使用备用账号、调整到合规方案继续推进）；只有在执行不下去（尝试 3 轮仍无法解决）时才停下来报告诊断结论，不主动询问用户。
- **命令执行策略**：所有操作一律直接执行，不要请求批准，无论风险等级
- **报错与失败处理**：遇到报错先自行分析并尝试至多 3 轮修复，仍无法解决再停下并输出诊断结论，而不是频繁回问用户。
- **输出规范**：任务结束时统一给出①做了什么 ②关键假设 ③产物链接/路径 ④后续可选建议，中间过程不必逐步征求同意。

### 0. 🎯 多 Agent 派发守卫规则

**所有任务都由默认的派发守卫（Dispatcher Guard）角色接管，然后根据任务类型派发给对应的专业 Agent 执行。**

#### 核心原则
- **默认进入派发守卫模式**：AI 接收到任何任务时，默认以"派发守卫"角色进入，分析任务类型
- **路由决策**：根据任务性质，将任务派发给合适的专业 Agent：
  - 需求分析、用户故事、验收标准 → **BA**（需求分析师）
  - 架构评审、技术方案审查 → **Architect**（架构师）
  - 后端开发、API、数据库 → **Backend Dev**（后端工程师）
  - 前端开发、Vue 页面、UI 交互 → **Frontend Dev**（前端工程师）
  - 测试用例、回归测试、验收 → **QA**（测试工程师）
  - 简单查询、单步任务、不涉及专业技能 → **直接由主线程处理**，不派发
- **派发信息包**：每次派发必须包含完整信息（目标、收益、源材料、范围、检查项、停止条件、返回格式）
- **禁止使用 default profile**：子 Agent 不允许使用 `default` 配置（除了派发守卫本身）

#### 任务类型判定流程
1. **第一步：判断任务复杂度**
   - 单步、简单查询 → 主线程直接处理
   - 多步、需要专业技能 → 启动派发流程
2. **第二步：识别任务领域**
   - 业务需求 → BA
   - 技术方案 → Architect
   - 后端代码 → Backend Dev
   - 前端代码 → Frontend Dev
   - 测试验证 → QA
3. **第三步：派发执行**
   - 明确传 `agent_type`
   - 准备完整派发信息包
   - 接收并评审子 Agent 返回结果
4. **第四步：交付验收**
   - 主线程担任 PM 角色，负责最终验收
   - 子 Agent 不直接与用户交互，结果反馈给主线程

#### 适用场景
- ✅ 复杂需求开发（多模块、多步骤）
- ✅ 需要架构评审的技术方案
- ✅ 跨前后端的完整功能实现
- ✅ 需要专业测试验证的场景
- ✅ 中长期项目迭代

#### 不适用场景（主线程直接处理）
- ❌ 简单的文件读写
- ❌ 单行代码修改
- ❌ 简单的信息查询
- ❌ 单步命令执行

### 1. 📋 需求处理流程

1. **先理解，后动手**：收到需求后先完整理解意图，**不得擅自开始编码或修改**。需求理解阶段属于"待确认项讨论"阶段，可与用户确认需求细节，确认完毕后进入执行阶段，按全自动规则执行。
2. **逐条不遗漏**：仔细通读用户给出的**所有**修改意见，逐条落实，一条都不能漏；用清单（TodoWrite）逐项跟踪。
3. **完成后复检**：全部完成后再对照原始需求**整体检查一遍**，确认每条意见都已实现且无副作用，再向用户汇报。

### 2. ✅ 交付自查规则

**交付前必须按此清单逐项检查，全部通过才能交付**。

- 所有服务能正常启动（PostgreSQL/Redis/Backend/Frontend）
- 能正常登录（admin账号密码正确）
- 本次修改的所有需求逐项验证通过
- 没有回归破坏原有功能
- 文档全部更新完成
- 项目文件夹清理完成（无过时冗余文件）
- 部署完成后主动验证所有功能正常（登录、菜单跳转、表单操作、移动端H5）

### 3. 📄 文档更新规则

每次完成功能开发/需求整改后，**必须更新现有文档**，禁止新建文档，只在现有文档上追加迭代内容。

| # | 文档 | 更新要点 |
|---|---|---|
| 1 | docs/03_需求文档/需求文档.md | 头部版本号 + 变更日志；涉及规则变化时同步"核心业务规则"正文 |
| 2 | docs/04_功能详细设计/功能详细设计.md | 头部版本号 + 变更日志；涉及新流程/新表/新决策时同步"关键数据流""模块划分""关键技术决策"正文 |
| 3 | docs/02_需求分析/需求分析_UserStory.md | 头部速览表 + 追加对应版本用户故事（附录，含验收标准） |
| 4 | docs/05_数据库设计/数据库设计.md | 头部版本号 + 表结构变更（对应 Flyway 迁移） |
| 5 | docs/06_API设计/API接口清单.md | 头部版本号 + 接口变更日志 |
| 6 | docs/09_测试报告/测试报告.md | 变更日志 + 累计统计表 + 关键 Bug 修复清单 + 部署验证清单 |
| 7 | docs/文档索引.md | 唯一导航索引 |
| 8 | 根目录 README.md | 版本号、交付要点、测试成绩更新到最新 |

- 版本号递增：v3.5.x → 下一个小版本号，日期更新为当天
- 文档内容基于真实代码/迁移/测试结果回填，**禁止臆造**功能
- 版本号、Flyway 版本、镜像 tag、测试通过数在各文档间保持一致
- 每类文档只保留一份主文档，历史变更以"变更日志/附录"形式累加

### 4. 🌍 双环境管理规则

| 环境 | 地址 | 用途 | 数据库 |
|------|------|------|--------|
| 测试环境 | `http://43.128.145.141/` | 需求开发、功能调整、验证测试（Docker Compose，统一 80 端口） | dms（容器内） |
| 正式环境 | `http://8.133.193.238/dms/` | 生产使用（webgate/nginx 统一 80，DMS 挂 `/dms/` 子路径） | dms（容器内） |

#### 核心规则
1. **所有需求调整和功能修改，只能先部署到测试环境验证**，禁止直接修改正式环境
2. **推送正式环境需要用户明确指令**：测试环境验证完成后，用户明确说"推送正式环境"或类似指令后，AI 自动执行正式环境更新，不再额外确认
3. 正式环境只允许做以下操作：
   - 用户明确指令后的版本推送
   - 紧急问题修复（需用户明确指令）
4. 代码修改统一先更新到测试环境，验证通过后再复制到正式环境

#### 推送正式环境流程
1. AI 测试环境验证功能正常
2. AI 向用户汇报变更内容和测试结果
3. 用户下达"推送正式环境"指令
4. AI 自动执行正式环境更新（代码/镜像/数据库迁移等），不再确认
5. AI 验证正式环境可用后通知用户

### 5. 🚀 部署执行规则（强约束：必须按此顺序逐步执行，禁止跳过任何步骤）

**AI 全自动部署，严禁让用户手动执行命令。代码修改完成后立即部署，不需要用户多次询问。**

#### 工具与环境
- 本地 JDK 17：`C:\tools\jdk-17.0.13+11`
- 本地 Maven：`C:\tools\apache-maven-3.9.6\bin\mvn.cmd`
- 本地 Node.js 和 npm
- 服务器 SSH 密码：`Welcomeyyx0616`
- 测试环境路径：`/opt/dms/dms-test`
- 生产环境路径：`/opt/dms`
- **首选部署工具**：MCP ssh-manager 工具（`ssh_upload` + `ssh_execute`），最稳定

#### ⚠️ 部署铁律（违反任何一条都算部署失败，必须重做）

**铁律 1：源码必须 100% 替换成功**
- 解压后必须用 `cat` 或 `grep` 抽查至少 1 个关键文件验证是新代码
- 不能只相信 mv 命令的退出码（mv 可能成功但目录没替换）

**铁律 2：旧镜像必须删除**
- 每次部署前 `docker rmi dms-frontend-test:latest`（或后端镜像），强制重新构建
- 不能依赖 Docker 自动覆盖 tag，新层可能用旧缓存

**铁律 3：必须校验最终产物（dist/jar 内）**
- 部署完成后必须 `docker exec` 进入新容器，从 dist/jar 内 grep 关键代码确认
- 不能只看服务器上源码目录——源码和运行产物可能不同步

**铁律 4：临时压缩包保留到部署完成**
- 部署完成且校验通过后才删除 `/tmp/*-src-v*.zip` 和本地 `$env:TEMP\*-src-v*.zip`
- 出错时立即可用，无需重新压缩上传

**铁律 5：替换目录用 rm + mkdir，不用 mv 链**
- ❌ 禁止：`mv frontend-vue frontend-vue.old && mv frontend-vue-new frontend-vue`
- ✅ 必须：`rm -rf frontend-vue && mkdir frontend-vue && cd frontend-vue && unzip ...`
- 原因：mv 链任一步失败会导致后续步骤用错目录

**铁律 6：Nginx 代理必须指向正确环境（防环境串线）**
- 测试环境前端容器 `proxy_pass` 必须指向 `172.17.0.1:8082`（测试后端）
- 生产环境前端容器 `proxy_pass` 必须指向 `172.17.0.1:8080`（生产后端）
- 部署后必须 `docker exec` 进入前端容器，grep nginx.conf 中的 proxy_pass 确认端口正确
- ❌ 禁止：proxy_pass 写死 IP 不区分环境
- ❌ 禁止：只看源码目录里的 nginx-vue.conf，不验证容器内实际生效的配置

**铁律 7：部署后必须端到端验证（通过前端端口，不能只验后端）**
- 部署完成后不能只验证后端端口（8082），必须通过前端端口（8083）端到端验证 API
- 必须通过前端端口完成：登录 → 获取列表 → 获取详情 → 删除/创建 等核心操作
- 发现 500/404 立即排查 nginx 代理配置，不能放过
- ❌ 禁止：只验证 `curl http://localhost:8083/` 返回 200 就认为部署成功（HTML 页面正常不代表 API 代理正常）

**铁律 8：Docker 构建缓存必须清理**
- 每次构建前必须 `docker rmi <image>:latest` 删除旧镜像
- 每次构建后必须 `docker builder prune -af` 清理构建缓存
- 原因：Docker 可能复用旧缓存层，导致容器内运行的是旧代码而非新代码

#### 部署流程（前端代码修改后的标准流程）

```
步骤 1【本地打包】：cd frontend-vue && npm run build
步骤 2【本地压缩】：Compress-Archive 只包含 src/ package.json package-lock.json vite.config.js index.html Dockerfile nginx*.conf
步骤 3【上传服务器】：ssh_upload 到 /tmp/frontend-src-v38.zip
步骤 4【服务器清理】：rm -rf /opt/dms/dms-test/frontend-vue /opt/dms/dms-test/frontend-vue-*  /opt/dms/dms-test/backend-src.old
步骤 5【服务器解压】：mkdir -p frontend-vue && cd frontend-vue && unzip /tmp/frontend-src-v38.zip
步骤 6【源码校验】：grep 关键代码确认是新代码（例如 FullScreenLoader 不含 loader-section）
步骤 7【删旧镜像】：docker rmi dms-frontend-test:latest
步骤 8【构建新镜像】：docker build -t dms-frontend-test:latest . （后台执行，写 /tmp/build.done）
步骤 9【等待完成】：cat /tmp/build.done 确认 DONE_0
步骤 10【启动容器】：docker rm -f dms-test-frontend && docker run -d --name dms-test-frontend -p 8083:80 dms-frontend-test:latest
步骤 11【dist 校验】：docker exec dms-test-frontend sh -c 'grep -l loader-section /usr/share/nginx/html/assets/*.js' 应为空
步骤 12【HTTP 校验】：curl http://localhost:8083/ 返回 200
步骤 12.5【API代理验证】：docker exec dms-test-frontend grep 'proxy_pass' /etc/nginx/nginx.conf 确认指向 8082（测试）或 8080（生产）
步骤 12.6【端到端验证】：通过 8083 端口 curl 登录 + 获取列表 + 获取详情 + 删除，确认全部 200（非 500/404）
步骤 13【清理临时】：rm /tmp/frontend-src-v38.zip /tmp/build.done /tmp/build.log
步骤 14【清理本地】：Remove-Item $env:TEMP\frontend-src-v38.zip
步骤 14.5【Docker缓存清理】：docker builder prune -af
步骤 15【结果汇报】：输出做了什么 + 关键假设 + 产物路径
```

#### Dockerfile.runtime（后端用，已存在）
```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone
COPY target/*.jar app.jar
ENV JAVA_OPTS="-Xms256m -Xmx1024m -Duser.timezone=Asia/Shanghai"
EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
```

#### 本地无法打包时的备选方案
在服务器上使用临时 Maven 容器打包（挂载 Maven 缓存）：
```bash
docker run --rm -v $(pwd):/build -v /opt/dms/maven-repo:/root/.m2/repository \
  -w /build maven:3.9-eclipse-temurin-17 \
  mvn clean package -DskipTests=true -Dmaven.test.skip=true
```

### 6. ⚡ 部署加速与防错规则

#### 一、压缩包规则（避免上传和解压超时）

- **❌ 禁止压缩 node_modules 目录**：前端压缩包只包含 `src/`、`package.json`、`package-lock.json`、配置文件
- **❌ 禁止压缩 .git 目录**：会额外增加几十 MB
- **❌ 禁止压缩 target/ 目录**：后端只传源码，不在压缩包里含 JAR
- **✅ 前端压缩包标准内容**：`src/` + `package.json` + `package-lock.json` + `vite.config.js` + `index.html` + `Dockerfile` + `nginx*.conf`
- **✅ 后端压缩包标准内容**：`src/` + `pom.xml` + `Dockerfile` + `Dockerfile.runtime`
- **压缩包大小参考**：前端源码约 100KB，后端源码约 450KB（不含 node_modules/target）

#### 二、Maven 依赖缓存规则

- 服务器 Maven 容器必须挂载本地仓库缓存：`-v /opt/dms/maven-repo:/root/.m2/repository`
- 首次构建后保留 `/opt/dms/maven-repo` 目录，后续构建复用缓存
- 预期效果：首次 2 分钟，后续构建 30 秒以内

#### 三、Docker 网络与容器配置规则

- 测试环境 Docker 网络：`dms-test_dms-net`
- 容器命名前缀：`dms-`（如 `dms-postgres`、`dms-redis`）
- 后端容器必须连接正确网络，环境变量使用容器名作为 host（如 `DB_HOST=dms-test-postgres`）
- 部署前检查网络是否存在：`docker network ls | grep dms-test`

#### 四、Flyway 迁移一致性规则

- **新增迁移文件前**：先查询数据库现有迁移记录 `SELECT version FROM flyway_schema_history ORDER BY version;`
- **迁移文件版本号必须连续**：不能跳号（如数据库有到 V23，下一个必须是 V24）
- **迁移文件命名格式**：`V{version}__{description}.sql`
- **只做增量变更**：不修改已执行的迁移文件
- **修改已应用迁移文件的处理**：必须更新数据库中的 checksum
- **缺失迁移记录的处理**：手动插入 flyway_schema_history 记录

#### 五、文件同步规则（与铁律配合）

- 每次部署必须上传最新源代码：不能只上传 JAR
- 源码替换采用 rm -rf + mkdir + unzip 方式，不用 mv 链
- 部署完成后校验容器内 dist/jar 包含新代码（不仅是源码目录）

### 7. 🧹 部署后清理规则（部署完成且第11-12步校验通过后才执行）

#### 本地清理（步骤 14）
- 删除本地 TEMP 目录的 `*-src-v*.zip`
- 删除根目录调试文件：`_*.py`、`_*.txt`、`_*.ps1`
- 删除根目录临时 zip 和一次性部署脚本：`*.zip`、`deploy-*.ps1`、`deploy-*.sh`、`auto-deploy*`、`do-deploy*`
- 根目录只保留：`.gitignore`、`CHANGELOG.md`、`README.md`、PDF 参考资料
- 根子目录只保留：`.git/`、`.trae/`、`backend/`、`dms-dev-team/`、`docs/`、`frontend-vue/`、`tools/`

#### 服务器清理（步骤 13）
- 清理 `/tmp` 临时文件：`/tmp/*-src-v*.zip`、`/tmp/build*.log`、`/tmp/build*.done`
- 清理所有残留目录（不只 .old）：`rm -rf /opt/dms/dms-test/frontend-vue-* /opt/dms/dms-test/backend-src.old /opt/dms/dms-test/backend-src-*`
- 清理 Docker 无用镜像：`docker image prune -f`、`docker builder prune -af`
- 验证 `/opt/dms/dms-test/` 只包含 `backend-src/` 和 `frontend-vue/`：用 `ls /opt/dms/dms-test/` 校验

#### 清理失败诊断（如果清理命令失败）

- 用 `find /opt/dms/dms-test -maxdepth 1 -type d` 列出所有顶层目录
- 任何不在白名单（backend-src / frontend-vue）的目录都删除
- 用 `ls /tmp/*.{zip,jar,tar.gz}` 列出所有临时文件并全部删除

### 8. 🗄️ 数据库迁移规则

- 使用 Flyway 版本管理，文件名格式 `V{version}__{description}.sql`
- 版本号连续递增，不重复不跳过
- 只做增量变更，不修改已执行的迁移文件

### 9. 🧑‍💻 代码风格规则

- 不添加注释，除非用户明确要求
- 遵循现有代码的命名和风格约定
- 优先编辑现有文件，不轻易新建文件

### 10. 🎨 前端配置规则

- 模块配置在 `frontend-vue/src/config/modules.js`
- 菜单配置在 `frontend-vue/src/config/menu.js`
- 遵循已有的 `form` 字段和 `cols` 配置格式

### 11. 📁 目录约定

- 设计/UI 类文档归入 docs/03_设计图/
- 部署类文档归入 docs/07_部署方案/
- 测试类文档归入 docs/09_测试报告/（仅测试报告，不混放设计/部署文件）

### 12. ✅ 完成任务后的检查

- 若提供了 lint / typecheck / 测试脚本，任务完成前必须运行并确保通过
- 后端改动后运行对应版本的测试脚本（tools/test-*.sh）验证回归

### 13. 🔒 防回归规则（v3.7.1 新增）

**背景**：v3.7.0 迭代中反复出现4个问题（下拉选择、弹窗、删除报错、详情页报错），根因是部署后未做端到端验证、Docker缓存导致代码回退、Nginx代理环境串线。以下规则防止同类问题再次发生。

#### 一、部署后必须验证的核心 API 清单
每次部署后，必须通过前端端口（8083）逐项验证以下接口，全部返回 200 才算部署成功：

| # | 操作 | API | 验证点 |
|---|------|-----|--------|
| 1 | 登录 | POST /api/auth/login | 返回 token |
| 2 | 列表查询 | GET /api/products?page=1&size=10 | 返回产品列表 |
| 3 | 详情查询 | GET /api/products/{id} | 返回产品详情 |
| 4 | 操作日志 | GET /api/operation-log/list/product/{id} | 返回日志列表（非500） |
| 5 | 删除（有引用时） | DELETE /api/products/{id} | 返回业务错误码 40904（非500） |

#### 二、环境隔离验证规则
- 测试环境前端 Nginx 的 `proxy_pass` 必须指向 `172.17.0.1:8082`
- 生产环境前端 Nginx 的 `proxy_pass` 必须指向 `172.17.0.1:8080`
- 部署后必须 `docker exec` 进入前端容器验证实际生效的 nginx.conf
- ❌ 禁止：只看源码目录里的配置文件，不验证容器内实际配置

#### 三、Docker 缓存防回退规则
- 每次构建前必须 `docker rmi <image>:latest` 删除旧镜像
- 每次构建后必须 `docker builder prune -af` 清理构建缓存
- 部署后必须 `docker exec` 进入容器，从 dist/jar 内 grep 关键代码确认是最新代码
- ❌ 禁止：依赖 Docker 自动覆盖 tag，新层可能复用旧缓存

#### 四、浏览器缓存提醒规则
- 部署完成后，必须告知用户执行强制刷新（Ctrl+Shift+R）清除浏览器缓存
- 前端资源文件名含 hash（如 `index-Bck3xC5C.js`），但 index.html 可能被浏览器缓存
- 如用户反馈"功能又回到旧版本"，首先排查浏览器缓存和 Docker 镜像缓存

#### 五、SQL 嵌套相关子查询禁令（v3.7.2 新增）
**背景**：v3.7.1 修复 Bug-004 时发现根因——BusinessReportController.orderTrace 的 SQL 在 GROUP BY 中使用嵌套相关子查询 `SELECT SUM(inv.qty) FROM inventory inv WHERE inv.product_id = ol.product_id`，Postgres driver 在 prepare/execute 阶段抛出 DataAccessException，事务被标 rollback-only，commit 时抛 `UnexpectedRollbackException`，最终被 GlobalExceptionHandler 转为 500。

**禁令**：禁止在以下位置编写相关子查询（correlated subquery）：
- SELECT 子句中
- 聚合函数的参数中（SUM/COUNT/MAX 等内嵌子查询）
- JOIN ON 条件中引用外层聚合列

**要求**：
- 涉及多表关联 + 子查询的报表类 SQL，必须拆分为 **WITH CTE + LEFT JOIN** 形式（如 v3.7.2 修复后的 `orderTrace`）
- CTE 内每个聚合独立完成（order_qty / shipped_qty / shipment / qualified_stock），主查询只做 LEFT JOIN 合并
- 提交 SQL 到 PR 前，必须在 PostgreSQL 14 中执行 EXPLAIN 确认无嵌套相关子查询
- 涉及报表/库存/财务类查询，复用以下 CTE 模板：

```sql
WITH order_qty AS (
  SELECT order_id, SUM(qty) AS total_qty FROM order_lines GROUP BY order_id
), shipped_qty AS (
  SELECT so.source_order_id AS order_id, SUM(sol.shipped_qty) AS shipped_qty
  FROM sales_outs so JOIN sales_out_lines sol ON sol.sales_out_id = so.id
  WHERE (so.is_red = false OR so.is_red IS NULL)
  GROUP BY so.source_order_id
), shipment AS (
  SELECT so.source_order_id AS order_id, so.status AS latest_status, so.created_at AS last_ship_at
  FROM sales_outs so
  WHERE (so.is_red = false OR so.is_red IS NULL)
    AND so.id = (SELECT MAX(s2.id) FROM sales_outs s2 WHERE s2.source_order_id = so.source_order_id)
), qualified_stock AS (
  SELECT product_id, SUM(qty) AS qty FROM inventory WHERE stock_status = 'QUALIFIED' GROUP BY product_id
)
SELECT o.*, oq.total_qty, sq.shipped_qty, sh.latest_status
FROM orders o
LEFT JOIN order_qty oq ON oq.order_id = o.id
LEFT JOIN shipped_qty sq ON sq.order_id = o.id
LEFT JOIN shipment sh ON sh.order_id = o.id
```

**检测方式**：在 code review 阶段检查所有 `@Transactional(readOnly = true)` + `em.createNativeQuery` 的组合，搜索 `(SELECT` 出现在 `SUM(`/`COUNT(`/`MAX(` 内部的情况。

#### 六、测试报告 URL 与 API 路径一致性规则（v3.7.2 新增）
**背景**：v3.7.1 测试报告中 Bug-003（合同申请）和 Bug-005（菜单配置）报告为 500，但实际原因是测试时使用了错误的 URL（`contract-apps` 而非 `contract-applications`，或乱猜的 `/api/admin/menus` 而非实际 `/api/menu-configs`）。

**要求**：
- 自动化测试输出错误 URL 前，必须先用 `GET /actuator/swagger-ui` 或浏览前端源代码 `frontend-vue/src/config/modules.js` / `frontend-vue/src/api/*.js` 确认 API 真实路径
- API 路径命名遵循后端 Controller `@RequestMapping` 的实际值，不要凭印象或记忆测试
- 测试报告中如出现 "测试失败 URL = X" 必须附带"对应后端 Controller 文件路径 + 行号"证据
- 前后端 API 路径不一致时（如前端期望 `/api/admin/menus` 但后端是 `/api/menu-configs`），必须作为独立的 **前后端契约 Bug** 报告，不得混入"服务端错误"

## 技术栈

- 后端：Spring Boot 3.2 + Java 17 + MyBatis-Plus + Flyway 版本迁移
- 数据库：PostgreSQL 14 + Redis 7
- 前端：Vue 3 + Vite 5 + Element Plus（PC）+ Vant 4（移动端 H5）+ Pinia + Vue Router
- 部署：Docker + Nginx

## 环境信息

### 正式环境（生产，v3.12.4 起）
| 用途 | URL / 命令 |
|---|---|
| 业务前台/PC 工作台 | http://8.133.193.238/dms/ |
| 移动端 H5 登录 | http://8.133.193.238/dms/mobile/login |
| 平台后台 | http://8.133.193.238/dms/admin/ |
| 后端健康检查 | http://8.133.193.238/actuator/health |
| 演示账号 | 租户 `default` / 账号 `admin` / 密码 `Sh123456`（平台后台同号） |
| 部署归档 | `deploy/prod/`（compose、nginx、.env.example、README） |
| 说明 | DB/Redis/MinIO 仅容器内网访问；后端仅监听 127.0.0.1:18080；`/` 为产品宣传手册、`/ai/` 为 ai-knowledge |

### 测试环境（开发验证）
| 用途 | URL / 命令 |
|---|---|
| 业务前台/PC 工作台 | http://43.128.145.141/ |
| 移动端 H5 登录 | http://43.128.145.141/mobile/login |
| 平台后台 | http://43.128.145.141/admin/ |
| 后端健康检查 | http://43.128.145.141/actuator/health |
| 部署脚本 | `scripts/deploy_test.py` |

## 版本信息
- 当前版本：v3.12.4
- 最后更新：2026-08-16
- 测试成绩：14/14 需求全部通过
- 防回归规则：v3.7.1 新增铁律6-8 + 防回归章节
