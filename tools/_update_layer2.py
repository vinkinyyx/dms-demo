from pathlib import Path
p=Path('.memory/layers/layer2-conventions.md')
s=p.read_text(encoding='utf-8')
start=s.index('## 十八、列表页布局规范')
end=s.index('\n---', start)
section=r'''## 十八、列表页布局规范（List Page Layout Spec）

> 适用范围：DMS 全部业务列表页，PC 端优先；移动端按同一权限与信息架构原则适配。
> 目标：搜索区、查询区、工具栏、行内操作统一整齐；按钮受权限控制；平台管理员给默认值，租户管理员可覆盖。
> 版本：v3.8.9（2026-08-07）。

### 18.1 五条铁律
1. **每页必须先搜后列**：每个列表页必须有搜索区，至少保留关键词或页面最必要筛选字段；`查询` 按钮始终展示，`重置` 按钮始终跟随。
2. **顶部按钮必须成组对齐**：从左到右固定为搜索字段 → 查询 → 重置 → spacer → 导入/导出/新增/业务按钮；按 `sortOrder` 排列，禁止散落。
3. **行内按钮超过 1 个自动折叠**：始终展示最高频按钮（通常为详情/查看），其余收进 `更多 ▾`；危险操作放下拉项内并保持红色。
4. **所有按钮必须受权限控制**：菜单、顶部按钮、行内按钮都必须有 `permissionCode`，由 `v-has` 或后端下发结果过滤；无权限不渲染、不占位。
5. **平台默认与租户覆盖分层**：平台管理员维护默认规则，租户管理员只能覆盖当前租户；覆盖优先级为租户覆盖 > 平台默认。

### 18.2 搜索区
| 项 | 规则 |
|----|------|
| 字段来源 | 业务模块统一使用 `/api/ui/layout/{pageKey}` 返回的 `filters`；独立页可使用 `ListPageLayout.vue`。 |
| 必要字段 | 每页至少 1 个可搜索字段，默认第一优先级为 `keyword`；状态/日期/业务对象按页面需要配置。 |
| 租户可调 | 租户管理员可在前台“列表页配置”中显示/隐藏、排序搜索字段；不得新增系统不认识的字段。 |
| 控件类型 | `input`、`select`、`date`、`date-range`；`select` 优先绑定字典或列筛选选项。 |
| 重置 | 清空布局筛选、旧列筛选和关键词，页码回到 1。 |

### 18.3 顶部工具栏
| 项 | 规则 |
|----|------|
| 必含按钮 | `search`（查询）、`reset`（重置）始终展示，不允许租户隐藏。 |
| 可选按钮 | `import`（导入）、`export`（导出）、`create`（新增）只在页面确实支持时出现；例如经销商画像不展示导入/导出/新增。 |
| 排序 | `search=10`、`reset=20`、`import=30`、`export=40`、`create=90`，业务扩展按钮使用 50-89。 |
| 权限 | 每个可选按钮必须配置 `permissionCode`；查询/重置不要求额外权限码。 |
| 布局 | 使用同一 flex 工具栏，查询按钮紧跟筛选字段，业务按钮在 spacer 右侧。 |

### 18.4 行内操作
| 项 | 规则 |
|----|------|
| 折叠规则 | 可见行按钮数 `<=1` 时平铺；`>1` 时只平铺第 1 个，其余进入 `更多 ▾`。 |
| 高频按钮 | 第 1 个按钮必须是查看/详情/打开等低风险动作；经销商画像固定为“查看画像”。 |
| 状态动作 | 提交/审批/驳回/取消/确认等按行状态过滤；不可用状态不进入当前行可见列表。 |
| 危险操作 | 删除、驳回等危险动作放入下拉项，使用 danger 样式并二次确认。 |
| 回调 | `CrudView.vue` 必须桥接旧 `statusActions/actions` 与标准 `submit/approve/reject/cancel/confirm/execute`，禁止出现死按钮。 |

### 18.5 表格与分页
| 维度 | 规则 |
|------|------|
| 表格样式 | `border stripe size="small"`，操作列 `fixed="right"`。 |
| 操作列宽度 | 1 个按钮约 96px；折叠状态约 180px。 |
| 分页 | `page-sizes=[20,50,100]`，右对齐；页码变化和筛选变化都回到第 1 页。 |
| 空状态 | 统一 `el-empty`，不得出现错乱留白。 |

### 18.6 权限与租户覆盖模型
```text
平台管理员（admin-vue）
  ├─ platform_filter_configs      平台默认搜索字段
  ├─ platform_page_configs        平台默认列配置
  └─ platform_button_configs      平台默认按钮（tenant_id IS NULL）

租户管理员（frontend-vue）
  ├─ tenant_filter_configs        当前租户搜索字段覆盖（tenant_id = 当前租户）
  ├─ platform_button_configs      当前租户按钮覆盖（tenant_id = 当前租户）
  └─ roles/strategies/resources   当前角色菜单、按钮、接口权限

最终下发：/api/ui/layout/{pageKey}
  filters = tenant_filter_configs 覆盖 platform_filter_configs
  toolbar/rowButtons = tenant_id 非空记录覆盖 tenant_id IS NULL 默认记录
  前端再按 permissionCode 做 v-has 过滤
```

- 平台默认按钮唯一键：`(page_key, scope, button_key) WHERE tenant_id IS NULL`。
- 租户按钮覆盖唯一键：`(tenant_id, page_key, scope, button_key) WHERE tenant_id IS NOT NULL`。
- 租户筛选覆盖唯一键：`(tenant_id, page_key, filter_key)`。
- 菜单可见性由 `frontend-vue/src/config/menu.js` 的 `permissionCode` 控制。
- 角色权限通过角色同名策略维护：`roles -> role_strategies -> strategy_resources -> resources`。

### 18.7 实现位置
| 层 | 路径 |
|----|------|
| 后端聚合 API | `PlatformPageLayoutController`，挂 `/api/ui/layout/{pageKey}`，业务前台 token 可访问。 |
| 租户筛选覆盖 API | `TenantUiConfigController`，挂 `/api/tenant-ui/pages/{pageKey}/filters`。 |
| 租户按钮覆盖 API | `TenantUiConfigController`，挂 `/api/tenant-ui/pages/{pageKey}/buttons`。 |
| 角色权限 API | `RbacController`，挂 `/api/roles/{id}/permissions`。 |
| 平台维护 API | `AdminButtonConfigController` / `AdminUiConfigController`，挂 `/api/admin/**`，仅平台 admin token。 |
| 业务列表入口 | `frontend-vue/src/components/CrudView.vue`，16 个业务模块共用。 |
| 独立列表组件 | `frontend-vue/src/components/ListPageLayout.vue`，用于 DealerProfileList、ApiCallLog、ProductMappings 等独立页。 |
| 布局 composable | `frontend-vue/src/composables/usePageLayout.js`，5 分钟内存缓存。 |
| 权限指令 | `frontend-vue/src/directives/has.js`。 |
| 租户页面 | `frontend-vue/src/views/TenantPageConfigs.vue`，菜单“列表页配置”。 |
| 角色权限页 | `frontend-vue/src/views/Roles.vue`，菜单“角色权限”。 |

### 18.8 上线自检清单
- [ ] 每页有至少 1 个搜索字段，查询/重置固定展示。
- [ ] 顶部按钮按 `查询 → 重置 → 导入/导出/新增/业务` 排列，无错位。
- [ ] 行内按钮超过 1 个时出现“更多 ▾”，第 1 个按钮可进入详情/查看。
- [ ] 经销商画像保留“查看画像”，且不展示未实现的导入/导出/新增。
- [ ] 销售订单的详情、编辑、提交、审批、驳回、取消、删除均可点击并有真实回调。
- [ ] 租户管理员在“列表页配置”隐藏搜索字段/按钮后，当前租户立即生效。
- [ ] 租户管理员在“角色权限”中勾选菜单/按钮后，对应用户重新登录生效。
- [ ] 平台默认和租户覆盖唯一键不冲突；Redis `dms:cfg:*` 缓存已清理。
'''
s=s[:start]+section+s[end:]
p.write_text(s, encoding='utf-8', newline='\n')
