from pathlib import Path
p=Path('.memory/layers/layer4-decisions.md')
s=p.read_text(encoding='utf-8')
entry='''

## D15: v3.8.9 列表页规范收口与租户角色权限闭环

| 字段 | 值 |
|------|-----|
| 日期 | 2026-08-07 |
| 状态 | 生效中 |
| 有效期 | 长期 |
| 原因 | v3.8.8 虽已把 CrudView 接入布局接口，但搜索字段仍以静态列配置为主、旧业务动作未桥接、租户管理员无法在业务前台直接维护角色菜单/按钮权限，导致“按钮折叠后无回调”“经销商画像入口可能丢失”“租户无法控制不同角色展示”等问题。 |
| 决策 | 1) CrudView 搜索区、工具栏、行按钮均由 `/api/ui/layout/{pageKey}` 驱动；2) 行内按钮超过 1 个即折叠，只平铺首个高频按钮；3) 桥接 `statusActions/actions` 与标准动作；4) 新增 `tenant_filter_configs`，保留租户级覆盖与平台级默认；5) 新增 `/api/roles/{id}/permissions`，通过角色同名策略维护资源；6) 新增业务前台“角色权限”和“列表页配置”。 |
| 唯一键 | 平台默认按钮：`(page_key, scope, button_key) WHERE tenant_id IS NULL`；租户按钮覆盖：`(tenant_id, page_key, scope, button_key) WHERE tenant_id IS NOT NULL`；租户筛选覆盖：`(tenant_id, page_key, filter_key)`。 |
| 关键文件 | `CrudView.vue` / `Roles.vue` / `TenantPageConfigs.vue` / `TenantUiConfigController.java` / `RbacController.java` / `V66__tenant_filter_override_and_layout_fixes.sql` |
| 关联规范 | Layer 2 §18 v3.8.9 / D13 / D14 |
'''
if '## D15:' not in s:
    s=s.rstrip()+entry+'\n'
p.write_text(s, encoding='utf-8', newline='\n')
