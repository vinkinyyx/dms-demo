import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/home',
    children: [
      { path: 'home', name: 'Home', component: () => import('@/views/Home.vue'), meta: { title: '工作台首页' } },
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '数据看板' } },
      { path: 'products/:id', name: 'ProductDetail', component: () => import('@/views/product/ProductDetail.vue'), meta: { title: '产品详情' } },
      { path: 'orders/:id', name: 'OrderDetail', component: () => import('@/views/order/OrderDetail.vue'), meta: { title: '订单详情' } },
      { path: 'hospitals/:id', name: 'HospitalDetail', component: () => import('@/views/hospital/HospitalDetail.vue'), meta: { title: '医院详情' } },
      { path: 'reports', name: 'Reports', component: () => import('@/views/Reports.vue'), meta: { title: '报表中心' } },
      { path: 'contracts', name: 'ContractWorkspace', component: () => import('@/views/contract/ContractWorkspace.vue'), meta: { title: '合同工作台' } },
      { path: 'contracts/new', name: 'ContractCreate', component: () => import('@/views/contract/ContractEdit.vue'), meta: { title: '新建合同' } },
      { path: 'contracts/templates', name: 'ContractTemplates', component: () => import('@/views/contract/ContractTemplateList.vue'), meta: { title: '合同模板' } },
      { path: 'contracts/templates/new', name: 'ContractTemplateCreate', component: () => import('@/views/contract/ContractTemplateEdit.vue'), meta: { title: '新建合同模板' } },
      { path: 'contracts/templates/:id', name: 'ContractTemplateEdit', component: () => import('@/views/contract/ContractTemplateEdit.vue'), meta: { title: '编辑合同模板' } },
      { path: 'contracts/:id', name: 'ContractDetail', component: () => import('@/views/contract/ContractDetail.vue'), meta: { title: '合同详情' } },
      { path: 'contracts/:id/edit', name: 'ContractEdit', component: () => import('@/views/contract/ContractEdit.vue'), meta: { title: '编辑合同' } },
      { path: 'dealers/profile', name: 'DealerProfileList', component: () => import('@/views/DealerProfileList.vue'), meta: { title: '经销商画像' } },
      { path: 'dealers/:id/profile', name: 'DealerProfile', component: () => import('@/views/DealerProfile.vue'), meta: { title: '经销商 360 画像' } },
      { path: 'order-create/sales', name: 'SalesOrderCreate', component: () => import('@/views/OrderCreate.vue'), meta: { title: '新建销售订单' } },
      { path: 'order-create/purchase', name: 'PurchaseOrderCreate', component: () => import('@/views/OrderCreate.vue'), meta: { title: '新建采购订单' } },
      { path: 'positions', name: 'Positions', component: () => import('@/views/Positions.vue'), meta: { title: '销售岗位' } },
      { path: 'roles-manage', name: 'RolesManage', component: () => import('@/views/Roles.vue'), meta: { title: '角色权限' } },
      { path: 'tenant-page-configs', name: 'TenantPageConfigs', component: () => import('@/views/TenantPageConfigs.vue'), meta: { title: '列表页配置' } },
      { path: 'receipt-edit/:id', name: 'ReceiptEdit', component: () => import('@/views/ReceiptEdit.vue'), meta: { title: '收货入库' } },
      { path: 'stock-move-edit/:id', name: 'StockMoveEdit', component: () => import('@/views/StockMoveEdit.vue'), meta: { title: '库存移动' } },
      { path: 'sales-out-edit/:id', name: 'SalesOutEdit', component: () => import('@/views/SalesOutEdit.vue'), meta: { title: '发货出库' } },
      { path: 'sales-return-edit/:id', name: 'SalesReturnEdit', component: () => import('@/views/SalesReturnEdit.vue'), meta: { title: '销退订单' } },
      { path: 'purchase-return-edit/:id', name: 'PurchaseReturnEdit', component: () => import('@/views/PurchaseReturnEdit.vue'), meta: { title: '采退订单' } },
      { path: 'm/:key', name: 'Module', component: () => import('@/views/ModuleView.vue'), meta: { title: '业务模块' } },
      { path: 'api-call-logs', name: 'ApiCallLog', component: () => import('@/views/ApiCallLog.vue'), meta: { title: '接口调用日志' } },
      { path: 'product-mappings', name: 'ProductMappings', component: () => import('@/views/ProductMappings.vue'), meta: { title: '产品对码' } },
      { path: 'approval/todo', name: 'ApprovalTodo', component: () => import('@/views/approval/TodoCenter.vue'), meta: { title: '审批中心' } },
      { path: 'approval/templates', name: 'ApprovalTemplates', component: () => import('@/views/approval/ApprovalTemplates.vue'), meta: { title: '审批流配置' } },
      { path: 'approval/delegations', name: 'ApprovalDelegations', component: () => import('@/views/approval/Delegations.vue'), meta: { title: '审批委托' } },
      { path: 'approval/admin', name: 'ApprovalAdmin', component: () => import('@/views/approval/ApprovalAdmin.vue'), meta: { title: '审批监控' } },
      { path: 'email-logs', name: 'EmailLogs', component: () => import('@/views/EmailLogs.vue'), meta: { title: '邮件发送日志' } },
      { path: 'notifications', name: 'Notifications', component: () => import('@/views/Notifications.vue'), meta: { title: '消息中心' } },
      { path: 'login-logs', name: 'LoginLogs', component: () => import('@/views/LoginLogs.vue'), meta: { title: '登录日志' } },
      { path: 'approvals/mine', name: 'MyApprovals', component: () => import('@/views/approval/TodoCenter.vue'), meta: { title: '我的审批' } }
    ]
  },
  { path: '/mobile/login', name: 'MLogin', component: () => import('@/views/mobile/MLogin.vue'), meta: { public: true, mobile: true } },
  {
    path: '/mobile',
    component: () => import('@/views/mobile/MLayout.vue'),
    redirect: '/mobile/home',
    meta: { mobile: true },
    children: [
      { path: 'home', name: 'MHome', component: () => import('@/views/mobile/MHome.vue'), meta: { mobile: true, title: '首页' } },
      { path: 'orders', name: 'MOrders', component: () => import('@/views/mobile/MOrders.vue'), meta: { mobile: true, title: '销售订单' } },
      { path: 'orders/:id', name: 'MOrderDetail', component: () => import('@/views/mobile/MOrderDetail.vue'), meta: { mobile: true, title: '订单详情' } },
      { path: 'orders/create', name: 'MOrderCreate', component: () => import('@/views/mobile/MOrderCreate.vue'), meta: { mobile: true, title: '下销售订单' } },
      { path: 'surgery-reports', name: 'MSurgeryReports', component: () => import('@/views/mobile/MSurgeryReports.vue'), meta: { mobile: true, title: '手术报台' } },
      { path: 'surgery-reports/:id', name: 'MSurgeryReportDetail', component: () => import('@/views/mobile/MSurgeryReportDetail.vue'), meta: { mobile: true, title: '报台详情' } },
      { path: 'surgery-reports/create', name: 'MSurgeryReportCreate', component: () => import('@/views/mobile/MSurgeryReportCreate.vue'), meta: { mobile: true, title: '手术植入报台' } },
      { path: 'dashboard', name: 'MDashboard', component: () => import('@/views/mobile/MDashboard.vue'), meta: { mobile: true, title: '我的业绩' } },
      { path: 'approvals', name: 'MApprovals', component: () => import('@/views/mobile/MApprovals.vue'), meta: { mobile: true, title: '移动审批' } },
      { path: 'approvals/:id', name: 'MApprovalDetail', component: () => import('@/views/mobile/MApprovalDetail.vue'), meta: { mobile: true, title: '审批详情' } },
      { path: 'messages', name: 'MMessages', component: () => import('@/views/mobile/MMessages.vue'), meta: { mobile: true, title: '消息中心' } },
      { path: 'profile', name: 'MProfile', component: () => import('@/views/mobile/MProfile.vue'), meta: { mobile: true, title: '我的' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/home' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = getToken()
  if (to.meta.public) {
    next()
  } else if (!token) {
    next(to.meta.mobile ? '/mobile/login' : '/login')
  } else {
    next()
  }
})

export default router
