# DMS 发布说明 v3.12.5

> 发布日期：2026-08-16
> 基线提交：2602bc0（codex/p2-delivery）
> 生产环境：http://8.133.193.238/dms/
> 测试环境：http://43.128.145.141/
> 上一版本：v3.12.4

## 一、版本概述

v3.12.5 是在 v3.12.4 基础上的前端热修复版本，聚焦「导入导出任务」页面的两个线上问题：分页参数报错导致页面不可用，以及全系统时间列直接展示原始 ISO 时间戳（含 T 与时区后缀）和页面多余字符。本版本后端无逻辑变更，仅前端重新构建发布。

## 二、变更清单

### 修复
- **导入导出任务分页 400**：`AsyncTasks.vue` 的 `reload()` 中 `const params = { page, size }` 误把 Vue `ref` 对象放入 query，被 axios 序列化为 `page=[object Object]&size=[object Object]`，后端 `@RequestParam int page` 无法转换报「参数类型错误: page」。改为 `page: page.value, size: size.value`。（提交 f24fabe）
- **时间列展示原始 ISO 时间戳**：后端实体使用 `OffsetDateTime`，全局 Jackson `date-format` 仅对 `java.util.Date` 生效，接口返回 `2026-08-13T22:43:25.524906+08:00`。统一在前端用 `formatDateTime` / `formatDate` 格式化为 `yyyy-MM-dd HH:mm:ss` / `yyyy-MM-dd`，覆盖：
  - 导入导出任务：提交时间、完成时间（并清除模板中误写入的字面量 `` `r`n `` 导致的页面乱码）。
  - 日志中心：API 日志、登录日志、操作日志；登录日志页。
  - 报表订阅：上次运行时间。
  - 库存盘点：上传时间。
  - 序列号追溯：时间。
  - 平台后台：创建时间；通用详情抽屉操作记录时间。
  - 医院/产品详情：下单时间、生产日期、有效期、手术日、下单日。
  （提交 2602bc0）

### 数据（仅测试环境）
- 导入导出任务测试数据由 1 条补充到 13 条，覆盖全部/导出/导入/报表四个页签及等待中、处理中、成功、失败四种状态，含导入进度（成功/失败/总数）和失败原因。该数据只写入测试库，不影响生产。

## 三、影响范围与风险

- 变更均为前端展示层，不涉及数据库结构与后端接口契约。
- 后端制品与 v3.12.4 完全一致（SHA256 相同），本次可仅更新前端静态资源；为保持基线完整，仍随版本附带后端 jar。
- 风险低：时间格式化函数对空值返回 `-`，对非法值回退原值，不影响数据。

## 四、测试结论

- 测试环境接口验证：登录后 `/api/async-tasks?page=1&size=20` 及 `taskType=EXPORT/IMPORT/REPORT` 均返回 200，共 13 条数据；参数为正确的数值而非 `[object Object]`。
- 前端生产构建（`VITE_BASE=/dms/`）通过，资源路径正确指向 `/dms/assets/`。
- 平台后台构建（`VITE_BASE=/dms/admin/`）通过。

## 五、发布制品

| 文件 | 说明 |
|---|---|
| dms-backend-v3.12.5.jar | 后端可执行 jar（与 v3.12.4 同内容） |
| dms-frontend-vue-v3.12.5.tar.gz | 业务前台 + 移动端 H5（/dms/ 子路径） |
| dms-admin-vue-v3.12.5.tar.gz | 平台后台（/dms/admin/ 子路径） |
| DMS发布基线_v3.12.5_20260816.md | 交付基线 |
| SHA256SUMS.txt | 制品校验和 |

## 六、部署方式（生产）

1. 备份 `/opt/dms/prod/frontend`。
2. 业务前端：清空 `/opt/dms/prod/frontend`（保留 `admin` 子目录），解压 `dms-frontend-vue-v3.12.5.tar.gz` 到该目录。
3. 平台后台：清空 `/opt/dms/prod/frontend/admin`，解压 `dms-admin-vue-v3.12.5.tar.gz` 到该目录。
4. `chown -R root:root /opt/dms/prod/frontend`；nginx 容器（webgate）为静态挂载，无需重启。
5. 后端本次无需替换/重启。
6. 浏览器强刷（Ctrl+F5）验证。