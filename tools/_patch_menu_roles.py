from pathlib import Path
p=Path('frontend-vue/src/router/index.js')
s=p.read_text(encoding='utf-8')
s=s.replace("      { path: 'positions', name: 'Positions', component: () => import('@/views/Positions.vue'), meta: { title: '销售岗位' } },", "      { path: 'positions', name: 'Positions', component: () => import('@/views/Positions.vue'), meta: { title: '销售岗位' } },\n      { path: 'roles-manage', name: 'RolesManage', component: () => import('@/views/Roles.vue'), meta: { title: '角色权限' } },")
p.write_text(s, encoding='utf-8', newline='\n')
p=Path('frontend-vue/src/config/menu.js')
s=p.read_text(encoding='utf-8')
s=s.replace("      { key: 'roles', icon: 'Avatar', label: '角色管理', permissionCode: 'role:view' }", "      { key: 'roles-manage', route: '/roles-manage', icon: 'Avatar', label: '角色权限', permissionCode: 'role:view' }")
p.write_text(s, encoding='utf-8', newline='\n')
