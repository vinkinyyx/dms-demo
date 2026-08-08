from pathlib import Path
p=Path('frontend-vue/src/router/index.js')
s=p.read_text(encoding='utf-8')
s=s.replace("      { path: 'roles-manage', name: 'RolesManage', component: () => import('@/views/Roles.vue'), meta: { title: '角色权限' } },", "      { path: 'roles-manage', name: 'RolesManage', component: () => import('@/views/Roles.vue'), meta: { title: '角色权限' } },\n      { path: 'tenant-page-configs', name: 'TenantPageConfigs', component: () => import('@/views/TenantPageConfigs.vue'), meta: { title: '列表页配置' } },")
p.write_text(s, encoding='utf-8', newline='\n')
p=Path('frontend-vue/src/config/menu.js')
s=p.read_text(encoding='utf-8')
s=s.replace("      { key: 'roles-manage', route: '/roles-manage', icon: 'Avatar', label: '角色权限', permissionCode: 'role:view' }", "      { key: 'roles-manage', route: '/roles-manage', icon: 'Avatar', label: '角色权限', permissionCode: 'role:view' },\n      { key: 'tenant-page-configs', route: '/tenant-page-configs', icon: 'Setting', label: '列表页配置', permissionCode: 'tenant_ui_config:view' }")
p.write_text(s, encoding='utf-8', newline='\n')
