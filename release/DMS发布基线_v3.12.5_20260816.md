# DMS 发布基线 v3.12.5

> 生成日期：2026-08-16
> 基线提交：2602bc05225fe9c1b3130f0b70af083a11c0bc9e（分支 codex/p2-delivery）
> 生产环境：http://8.133.193.238/dms/
> 测试环境：http://43.128.145.141/
> 技术栈：Spring Boot 3 + Java 17 + PostgreSQL/JPA + Redis + MinIO；前端 Vue3 + Element Plus（PC/平台）+ Vant（移动 H5）

## 1. 制品基线

| 制品 | 来源 | 备注 |
|---|---|---|
| dms-backend-v3.12.5.jar | backend/target/dms-backend.jar | 与 v3.12.4 字节一致，无后端变更 |
| dms-frontend-vue-v3.12.5.tar.gz | frontend-vue/dist（VITE_BASE=/dms/） | 业务前台 + 移动端 H5 |
| dms-admin-vue-v3.12.5.tar.gz | admin-vue/dist（VITE_BASE=/dms/admin/） | 平台后台 |

校验和见 `SHA256SUMS.txt`。

## 2. 代码基线（本版本涉及提交）

- 2602bc0 fix: 统一前端时间列格式化，避免展示原始 ISO 时间戳(含T/+08:00)及页面乱码
- f24fabe fix: 导入导出任务分页参数传 ref 对象导致 400 参数类型错误
- （基线之上继承 v3.12.4 全部提交：3edf576 数据/4fc8019/1b3032e/18a8af3 等）

## 3. 配置基线（生产 8.133.193.238）

- 部署目录：`/opt/dms/prod/`（docker-compose.yml、.env、backend/app.jar、frontend/、nginx-dms.conf）。
- 容器：dms-prod-backend（127.0.0.1:18080→8080）、dms-prod-postgres、dms-prod-redis、dms-prod-minio；nginx 容器 webgate 托管静态资源并反代。
- 访问路径：业务系统 `/dms/`，平台后台 `/dms/admin/`，API `/api/`、`/auth/`。
- JWT：access 8 小时、refresh 7 天；登录限流 60 次/分/IP。
- 前端构建：业务前台 `VITE_BASE=/dms/`，平台后台 `VITE_BASE=/dms/admin/`。

## 4. 修复内容

- 导入导出任务点击不再报「参数类型错误: page」。
- 全系统时间列统一显示为 `yyyy-MM-dd HH:mm:ss`（日期列为 `yyyy-MM-dd`），不再出现 `T`、`+08:00`、微秒后缀及页面多余 `` `r`n `` 字符。

## 5. 测试基线

- 测试环境导入导出接口：全部/导出/导入/报表四个页签均 200，共 13 条演示数据。
- 生产构建通过，资源路径正确；发布后进行冒烟（登录、导入导出页、时间显示、各页签切换）。

## 6. 回滚

恢复 `/opt/dms/backups/` 中部署前的 frontend 备份（保留 admin），或重新解压上一版本前端包即可；后端本次未变更，无需回滚。