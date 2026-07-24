# DMS v3.6.x 部署指南

## 当前修复内容

本次修复解决了以下问题：

### 1. 移动端登录问题
- **问题**: 移动端登录页默认密码错误
- **修复**: `frontend-vue/src/views/mobile/MLogin.vue` - 将默认密码改为 `Sh123456`
- **问题**: 移动端登录后跳转的页面不正确
- **修复**: `frontend-vue/src/router/index.js` - 添加完整的移动端路由配置

### 2. 完整的操作日志记录系统
- **新增**: `backend/src/main/java/com/dms/auth/service/LoginLogService.java` - 登录日志服务
- **集成**: `backend/src/main/java/com/dms/auth/controller/AuthController.java` - 记录登录成功/失败
- **新增**: `backend/src/main/resources/logback-spring.xml` - 日志文件配置
- **新增**: `backend/src/main/resources/db/migration/V26__create_user_login_logs_table.sql` - 登录日志表

## Log 文件位置

根据配置，日志文件将生成在以下位置：

- **普通应用日志**: `logs/dms-backend.log`
- **审计日志**: `logs/audit.log`
- **操作日志**: `logs/operation.log`
- **登录日志**: `logs/login.log`

日志文件在 Docker 容器内的路径是 `/app/logs/`，可以通过以下命令查看：
```bash
docker exec -it dms-test-backend ls -la /app/logs/
docker exec -it dms-test-backend tail -100 /app/logs/login.log
```

## 部署步骤

### 步骤 1: 连接到服务器
```bash
ssh root@8.133.193.238
# 密码: Welcomeyyx0616
```

### 步骤 2: 拉取最新代码
```bash
cd /opt/dms
git pull origin main
```

### 步骤 3: 部署后端测试环境
```bash
bash deploy-test-backend.sh
```

### 步骤 4: 部署前端测试环境
```bash
bash deploy-test-frontend.sh
```

### 步骤 5: 验证部署
访问以下地址验证功能：
- 业务工作台: http://8.133.193.238:8083/
- 移动端登录: http://8.133.193.238:8083/mobile/login
- 后端 API: http://8.133.193.238:8082/

## 验证清单

部署完成后请验证以下内容：

- [ ] **移动端登录默认密码**: 登录页显示的默认密码是否为 `Sh123456`
- [ ] **移动端登录后跳转**: 登录后是否跳转到 `/mobile/home` 并显示正确页面
- [ ] **后端日志文件**: 容器内 `/app/logs/` 目录下是否有 `login.log/audit.log/operation.log/dms-backend.log`
- [ ] **数据库日志表**: 数据库中是否存在 `user_login_logs/operation_log/audit_logs` 表
- [ ] **登录日志记录**: 登录操作是否被正确记录到数据库和日志文件

## 将容器日志文件复制到服务器

```bash
# 1. 连接到服务器
ssh root@8.133.193.238
# 密码: Welcomeyyx0616

# 2. 创建服务器上的日志目录
cd /opt/dms
mkdir -p logs-backup

# 3. 复制所有日志文件从容器到服务器
docker cp dms-test-backend:/app/logs/. /opt/dms/logs-backup/

# 4. 查看复制过来的日志文件
ls -la /opt/dms/logs-backup/
```

## 将日志文件从服务器传到本地

在本地 Windows PowerShell 上执行：

```powershell
# 1. 创建本地目录
mkdir -p C:\Users\vinkin.yx.yu\文件\05_其他\DMS\logs-backup

# 2. 使用 scp 从服务器下载日志文件到本地
scp -r root@8.133.193.238:/opt/dms/logs-backup/* C:\Users\vinkin.yx.yu\文件\05_其他\DMS\logs-backup\
# 密码: Welcomeyyx0616
```

## 检查命令

### 检查数据库表
```bash
docker exec -it dms-postgres-test psql -U dms -d dms_test -c '\dt'
```

### 检查登录日志表
```bash
docker exec -it dms-postgres-test psql -U dms -d dms_test -c 'SELECT * FROM user_login_logs ORDER BY at_time DESC LIMIT 10;'
```

### 检查后端容器日志
```bash
docker-compose -f docker-compose.test.yml logs --tail=100 dms-test-backend
```

### 检查日志文件
```bash
docker exec -it dms-test-backend ls -la /app/logs/
docker exec -it dms-test-backend tail -100 /app/logs/login.log
```

## 快速部署脚本

本地可以运行以下脚本查看部署说明：
```powershell
.\quick-deploy.ps1
```

## 推送正式环境（可选）

测试环境验证通过后，可以推送正式环境：
```bash
cd /opt/dms
docker-compose build backend
docker-compose up -d
```

正式环境地址：
- http://8.133.193.238:8081/
