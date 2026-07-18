# DMS 后端使用说明

## 一、环境依赖
- JDK 17（Temurin 或 Adoptium）
- Maven 3.9+ 或本目录下的 mvnw（Maven Wrapper）
- Docker Desktop（用于一键启动）

## 二、编译打包

### 方式 A：使用 Docker（推荐，无需本地装 Maven）
```bash
cd d:\Workspace\TRAE\DMS
docker compose up -d --build
```
Dockerfile 使用多阶段构建，会在容器内下载 Maven 依赖并打包，无需本地环境。

### 方式 B：本地 Maven
```bash
cd d:\Workspace\TRAE\DMS\backend
mvn clean package -DskipTests
```

### 方式 C：Maven Wrapper（需要先手工下载 wrapper）
```bash
cd d:\Workspace\TRAE\DMS\backend
# 首次使用：先用一次系统 mvn 生成 wrapper
mvn -N wrapper:wrapper
# 之后无需 Maven
./mvnw clean package -DskipTests   # Linux/Mac
.\mvnw.cmd clean package -DskipTests   # Windows
```

## 三、运行测试
```bash
cd d:\Workspace\TRAE\DMS\backend
mvn test
# 或
mvn -Dtest=DocNoGeneratorTest test
```
测试使用 H2 内存库（application-test.yml），无需真实 PostgreSQL。

## 四、启动完整环境
```bash
cd d:\Workspace\TRAE\DMS
docker compose up -d
# 等待约 60 秒完成初始化
docker compose logs -f backend

# 访问
# API Swagger：http://localhost:8080/swagger-ui.html
# MinIO：http://localhost:9001 (minioadmin/minioadmin)
# Mock：http://localhost:9090/__admin/mappings
```

## 五、默认账号
```
admin / Sh123456
```
首次登录会强制修改密码。

## 六、目录结构
```
backend/
├── pom.xml                          Maven 依赖
├── Dockerfile                       多阶段构建
├── src/main/java/com/dms/          Java 源码（14 个业务包）
│   ├── DmsApplication.java
│   ├── common/                     公共层（异常、分页、单据编号、租户上下文）
│   ├── security/                   Spring Security + JWT
│   ├── config/                     Web/Redis/OpenAPI 配置
│   ├── tenant/                     多租户管理
│   ├── user/                       用户管理
│   ├── rbac/                       四层 RBAC + 权限查询
│   ├── auth/                       登录/微信扫码/密码重置
│   ├── masterdata/                 产品/经销商/仓库/医院/区域
│   ├── contract/                   合同申请/审批/PDF
│   ├── authz/                      授权校验
│   ├── order/                      订单
│   ├── promotion/                  促销引擎（MOQ + FULL_REDUCTION）
│   ├── inventory/                  库存/收货/移库/调整/盘点
│   ├── sales/                      销售出库/分销/红冲
│   ├── rma/                        退换货
│   ├── invoice/                    发票
│   ├── report/                     报表 + 经销商画像
│   ├── home/                       工作台
│   └── notification/               消息通知
├── src/main/resources/
│   ├── application.yml
│   ├── application-test.yml
│   └── db/migration/               Flyway 迁移 V1~V7（含 Seed）
└── src/test/java/                  71 个测试方法
```

## 七、常见问题

### Q1: mvn 命令找不到
使用 Docker 方式 A，或先手工安装 Maven（https://maven.apache.org/）。

### Q2: 启动报数据库连接失败
确认 docker-compose 中 postgres 服务已就绪（`docker compose ps` 查看 status=healthy）。

### Q3: 微信扫码登录如何测试
默认 `WECHAT_APP_ID/SECRET` 为 mock 值，实际调用会走 Mock Server（http://localhost:9090）。测试账号可用已绑定 wechat_openid 的用户（seed 中 vendor02、dealer01）。

### Q4: 促销类型 GIFT/BUNDLE 报错
V1 只启用 MOQ 与 FULL_REDUCTION，创建 GIFT/BUNDLE 会返回 40003（详见 D-28 决策）。
