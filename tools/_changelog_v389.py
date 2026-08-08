from pathlib import Path
p=Path('CHANGELOG.md')
s=p.read_text(encoding='utf-8')
entry='''## v3.8.9 (2026-08-07) - 列表页规范全站收口 + 租户角色权限闭环

### 背景
v3.8.8 虽然接入了页面布局接口，但仍有三个缺口：`CrudView` 仍以静态列筛选为主，租户隐藏搜索字段不生效；销售订单等业务页的旧 `statusActions` 未与布局按钮桥接，折叠后会出现“按钮可见但无回调”；租户管理员缺少直接维护本租户角色菜单/按钮权限和页面搜索/按钮覆盖的业务前台入口。本版本按 Layer 2 第十八章 v3.8.9 规则收口。

### 后端
- Flyway `V66__tenant_filter_override_and_layout_fixes.sql`：新增 `tenant_filter_configs`，支持租户级搜索字段覆盖；修正筛选/按钮中文乱码；经销商画像默认隐藏导入/导出/新增；销售订单补齐驳回/取消按钮与权限资源。
- `TenantUiConfigController`：新增 `/api/tenant-ui/pages/{pageKey}/filters` 与 `/buttons`，业务 token 可维护当前租户覆盖。
- `RbacController` / `RbacService`：新增 `/api/roles/{id}/permissions`，按角色同名策略维护菜单、按钮、接口权限，并做租户隔离。
- `UiConfigService.filtersForTenant` 改为平台默认 + 租户覆盖合并。

### 前端
- `CrudView.vue`：搜索区改为布局驱动；行内按钮超过 1 个即折叠到“更多 ▾”；桥接旧 `statusActions/actions` 和标准 `submit/approve/reject/cancel/confirm/execute`。
- `DealerProfileList.vue`：修复乱码，保留“查看画像”，不展示未实现的导入/导出/新增。
- `Roles.vue`：从通用 CRUD 改为角色权限页，支持菜单/按钮资源树勾选。
- `TenantPageConfigs.vue`：新增租户页面配置入口，可按页面调整搜索字段、顶部按钮、行内按钮显示与排序。
- `menu.js` / `router/index.js` / `api/admin.js`：补充“角色权限”“列表页配置”菜单、路由和 API。

### 验证
- `mvn -q -DskipTests package` 通过。
- `frontend-vue/npm run build` 通过。
- 测试环境已部署后端与业务前端，Flyway 升级到 V66，Redis `dms:cfg:*` 已清理。
- E2E：admin 权限码 230 条；`orders` 返回 7 个行按钮；`dealer-profile` 只保留查询/重置/查看画像；角色权限与租户筛选覆盖接口均验证通过。

'''
p.write_text(entry+s, encoding='utf-8', newline='\n')
