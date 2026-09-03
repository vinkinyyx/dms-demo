import { createRouter, createWebHistory } from 'vue-router'

function isTokenValid(token) {
  if (!token) return false
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return !payload.exp || payload.exp * 1000 > Date.now()
  } catch (e) {
    return false
  }
}

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/auth/Login.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('@/layout/AdminLayout.vue'),
    children: [
      { path: '', name: 'Overview', component: () => import('@/views/dashboard/Overview.vue'), meta: { title: '首页总览' } },
      { path: 'tenants/manufacturers', name: 'ManufacturerTenants', component: () => import('@/views/tenant/ManufacturerTenants.vue'), meta: { title: '厂家租户' } },
      { path: 'tenants/dealers', name: 'DealerTenants', component: () => import('@/views/tenant/DealerTenants.vue'), meta: { title: '经销商租户' } },
      { path: 'tenant-admins', name: 'TenantAdmins', component: () => import('@/views/tenant/TenantAdmins.vue'), meta: { title: '租户管理员' } },
      { path: 'role-templates', name: 'RoleTemplates', component: () => import('@/views/config/RoleTemplates.vue'), meta: { title: '角色模板' } },
      { path: 'menus', name: 'Menus', component: () => import('@/views/config/Menus.vue'), meta: { title: '平台菜单' } },
      { path: 'ui-configs', name: 'UiConfigs', component: () => import('@/views/config/UiConfigs.vue'), meta: { title: '页面配置' } },
      { path: 'notify-settings', name: 'NotifySettings', component: () => import('@/views/config/NotifySettings.vue'), meta: { title: '通知设置' } },
      { path: 'dicts', name: 'Dicts', component: () => import('@/views/dict/Dicts.vue'), meta: { title: '全局字典' } },
      { path: 'logs/api', name: 'ApiLogs', component: () => import('@/views/log/ApiLogs.vue'), meta: { title: '接口日志' } },
      { path: 'logs/audits', name: 'AuditLogs', component: () => import('@/views/log/AuditLogs.vue'), meta: { title: '审计日志' } },
      { path: 'reports', name: 'ReportsOverview', component: () => import('@/views/reports/Overview.vue'), meta: { title: '报表总览' } }
    ]
  }
]

const router = createRouter({ history: createWebHistory(import.meta.env.BASE_URL), routes })

router.beforeEach((to) => {
  const token = localStorage.getItem('admin_access_token')
  if (!isTokenValid(token)) {
    localStorage.removeItem('admin_access_token')
    if (!to.meta.public) return { path: '/login' }
    return true
  }
  if (to.path === '/login' && token) return { path: '/' }
  return true
})

export default router