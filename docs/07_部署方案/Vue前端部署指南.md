# DMS Vue 前端部署指南

## 部署信息

- **项目路径**: `c:\Users\vinkin.yx.yu\文件\05_其他\DMS\frontend-vue`
- **服务器**: `root@8.133.193.238`
- **部署路径**: `/opt/dms`
- **访问端口**: `8081`
- **访问地址**: `http://8.133.193.238:8081/`

## 部署步骤

### 方法一：手动部署（推荐）

#### 步骤 1：压缩前端项目

在本地执行：

```powershell
# 进入项目根目录
cd "c:\Users\vinkin.yx.yu\文件\05_其他\DMS"

# 压缩 frontend-vue 目录
Compress-Archive -Path "frontend-vue\*" -DestinationPath "frontend-vue-deploy.zip" -Force
```

#### 步骤 2：上传到服务器

使用您熟悉的SSH工具（如FileZilla、WinSCP、或命令行scp）上传文件：

```bash
# Linux/Mac
scp frontend-vue-deploy.zip root@8.133.193.238:/opt/dms/

# Windows PowerShell (使用 pscp)
& tools\pscp.exe -pw <密码> frontend-vue-deploy.zip root@8.133.193.238:/opt/dms/
```

#### 步骤 3：连接服务器并部署

SSH连接到服务器：

```bash
ssh root@8.133.193.238
```

在服务器上执行：

```bash
# 1. 创建部署目录
mkdir -p /opt/dms/frontend-vue

# 2. 解压文件
cd /opt/dms/frontend-vue
unzip -o /opt/dms/frontend-vue-deploy.zip

# 3. 构建 Docker 镜像
docker build -t dms-frontend-vue:latest .

# 4. 停止并删除旧容器（如果存在）
docker rm -f dms-frontend-vue 2>/dev/null || true

# 5. 启动新容器
docker run -d \
  --name dms-frontend-vue \
  --restart unless-stopped \
  -p 8081:80 \
  -m 128m \
  dms-frontend-vue:latest

# 6. 验证容器状态
docker ps | grep dms-frontend-vue
```

#### 步骤 4：验证部署

在浏览器中访问：http://8.133.193.238:8081/

### 方法二：使用部署脚本

#### 准备工作

已准备好以下文件：
- `frontend-vue-temp.zip` - 前端项目压缩包
- `tools/deploy-vue-manual.sh` - 服务器端部署脚本

#### 执行部署

```powershell
# 1. 上传压缩包
& tools\pscp.exe -pw <密码> frontend-vue-temp.zip root@8.133.193.238:/opt/dms/frontend-vue.zip

# 2. 上传部署脚本
& tools\pscp.exe -pw <密码> tools\deploy-vue-manual.sh root@8.133.193.238:/opt/dms/

# 3. 执行部署脚本
& tools\plink.exe -ssh -pw <密码> root@8.133.193.238 "bash /opt/dms/deploy-vue-manual.sh"
```

## Dockerfile 说明

项目已包含 `Dockerfile`，使用多阶段构建：

```dockerfile
# 阶段1: 构建
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm install --registry=https://registry.npmmirror.com
COPY . .
RUN npm run build

# 阶段2: 生产镜像
FROM nginx:1.25-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx-vue.conf /etc/nginx/nginx.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

## Nginx 配置说明

`nginx-vue.conf` 配置要点：

1. 监听 80 端口（容器内）
2. API 请求代理到 `8.133.193.238:8080`
3. 支持 Vue Router history 模式

## 常见问题

### 1. 端口冲突

如果 8081 端口被占用，修改启动命令：

```bash
docker run -d --name dms-frontend-vue -p <新端口>:80 ...
```

### 2. 镜像构建失败

检查磁盘空间：

```bash
df -h /
```

清理Docker缓存：

```bash
docker system prune -af
```

### 3. 容器无法访问

检查防火墙：

```bash
# Ubuntu
ufw allow 8081

# 或使用 iptables
iptables -A INPUT -p tcp --dport 8081 -j ACCEPT
```

## 验证清单

- [ ] Docker 镜像构建成功
- [ ] 容器正在运行
- [ ] 访问 http://8.133.193.238:8081/ 返回页面
- [ ] 登录功能正常
- [ ] API 调用正常

## 回滚方案

如果新版本出现问题：

```bash
# 停止容器
docker stop dms-frontend-vue

# 重新构建旧版本镜像
docker build -t dms-frontend-vue:<旧版本> .

# 启动旧版本
docker run -d --name dms-frontend-vue -p 8081:80 dms-frontend-vue:<旧版本>
```