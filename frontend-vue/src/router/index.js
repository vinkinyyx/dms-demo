import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getToken, getPermissions } from '@/utils/auth'
import { shouldUseMobile } from '@/utils/device'
import { useUserStore } from '@/store/user'
import { MENU_GROUPS } from '@/config/menu'

// 路由 key -> 所需权限码（来源 menu.js 的 permissionCode）
const ROUTE_PERMISSION_MAP = (() => {
  const m = {}
  MENU_GROUPS.forEach(g => (g.items || []).forEach(it => {
    if (it.permissionCode) m[it.key] = it.permissionCode
  }))
  return m
})()

function buildPermissionSet() {
  const set = new Set()
  try {
    const store = useUserStore()
    ;(store.permissions || []).forEach(p => set.add(p))
    ;((store.user && store.user.permissions) || []).forEach(p => set.add(p))
  } catch (e) { /* pinia 未就绪 */ }
  getPermissions().forEach(p => set.add(p))
  return set
}

const routes = [
  { path: '/error/:code', name: 'ErrorPage', component: () => import('@/views/ErrorPage.vue'), meta: { public: true } },
  { path: '/403', redirect: '/error/403' },
  { path: '/404', redirect: '/error/404' },
  { path: '/500', redirect: '/error/500' },
  { path: '/print/:type/:id', name: 'PrintView', component: () => import('@/views/PrintView.vue'), meta: { title: '单据打印' } },
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/home',
    children: [
      { path: 'home', name: 'Home', component: () => import('@/views/Home.vue'), meta: { title: '工作台首页' } },
      { path: 'consignment-stock', name: 'ConsignmentStock', component: () => import('@/views/ConsignmentStock.vue'), meta: { title: '寄售库存' } },
      { path: 'dealer-credit', name: 'DealerCredit', component: () => import('@/views/DealerCredit.vue'), meta: { title: '经销商资信与账期' } },
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '数据看板' } },
      { path: 'products/:id', name: 'ProductDetail', component: () => import('@/views/ResourceDetail.vue'), props: { moduleKey: 'products', title: '产品详情' }, meta: { title: '产品详情' } },
      { path: 'product-bundles/:id', name: 'BundleDetail', component: () => import('@/views/ResourceDetail.vue'), props: { moduleKey: 'product-bundles', title: 'BOM详情' }, meta: { title: 'BOM详情' } },
      { path: 'product-prices/:id', name: 'ProductPriceDetail', component: () => import('@/views/ResourceDetail.vue'), props: { moduleKey: 'product-prices', title: '价格详情' }, meta: { title: '价格详情' } },
      { path: 'promotions/:id', name: 'PromotionDetail', component: () => import('@/views/ResourceDetail.vue'), props: { moduleKey: 'promotions', title: '促销详情' }, meta: { title: '促销详情' } },
      { path: 'categories/:id', name: 'CategoryDetail', component: () => import('@/views/ResourceDetail.vue'), props: { moduleKey: 'categories', title: '产品分类详情' }, meta: { title: '产品分类详情' } },
      { path: 'dealers/:id', name: 'DealerDetail', component: () => import('@/views/ResourceDetail.vue'), props: { moduleKey: 'dealers', title: '经销商详情' }, meta: { title: '经销商详情' } },
      { path: 'warehouses/:id', name: 'WarehouseDetail', component: () => import('@/views/ResourceDetail.vue'), props: { moduleKey: 'warehouses', title: '仓库详情' }, meta: { title: '仓库详情' } },
      { path: 'regions/:id', name: 'RegionDetail', component: () => import('@/views/ResourceDetail.vue'), props: { moduleKey: 'regions', title: '区域详情' }, meta: { title: '区域详情' } },
      { path: 'suppliers/:id', name: 'SupplierDetail', component: () => import('@/views/ResourceDetail.vue'), props: { moduleKey: 'suppliers', title: '供应商详情' }, meta: { title: '供应商详情' } },
      { path: 'authorizations/:id', name: 'AuthorizationDetail', component: () => import('@/views/ResourceDetail.vue'), props: { moduleKey: 'authorizations', title: '授权详情' }, meta: { title: '授权详情' } },
      { path: 'purchase-orders/:id', name: 'PurchaseOrderDetail', component: () => import('@/views/ResourceDetail.vue'), props: { moduleKey: 'purchase-orders', title: '采购订单详情' }, meta: { title: '采购订单详情' } },
      { path: 'receipts/:id', name: 'ReceiptDetail', component: () => import('@/views/ResourceDetail.vue'), props: { moduleKey: 'receipts', title: '收货入库详情' }, meta: { title: '收货入库详情' } },
      { path: 'stock-moves/:id', name: 'StockMoveDetail', component: () => import('@/views/ResourceDetail.vue'), props: { moduleKey: 'stock-moves', title: '库存移动详情' }, meta: { title: '库存移动详情' } },
      { path: 'inventory-adjustments/:id', name: 'InventoryAdjustmentDetail', component: () => import('@/views/ResourceDetail.vue'), props: { moduleKey: 'inventory-adjustments', title: '库存调整详情' }, meta: { title: '库存调整详情' } },
      { path: 'surgery-reports/:id', name: 'SurgeryReportDetail', component: () => import('@/views/ResourceDetail.vue'), props: { moduleKey: 'surgery-reports', title: '手术报台详情' }, meta: { title: '手术报台详情' } },
      { path: 'product-lines/:id', name: 'ProductLineDetail', component: () => import('@/views/ResourceDetail.vue'), props: { moduleKey: 'product-lines', title: '产品层次详情' }, meta: { title: '产品层次详情' } },
      { path: 'orders/:id', name: 'OrderDetail', component: () => import('@/views/order/OrderDetail.vue'), meta: { title: '订单详情' } },
      { path: 'hospitals/:id', name: 'HospitalDetail', component: () => import('@/views/ResourceDetail.vue'), props: { moduleKey: 'hospitals', title: '医院详情' }, meta: { title: '医院详情' } },
      { path: 'reports', name: 'Reports', component: () => import('@/views/Reports.vue'), meta: { title: '报表中心' } },
      { path: 'contracts', name: 'ContractWorkspace', component: () => import('@/views/contract/ContractWorkspace.vue'), meta: { title: '合同工作台' } },
      { path: 'contracts/new', name: 'ContractCreate', component: () => import('@/views/contract/ContractEdit.vue'), meta: { noCache: true, title: '新建合同' } },
      { path: 'contracts/templates', name: 'ContractTemplates', component: () => import('@/views/contract/ContractTemplateList.vue'), meta: { title: '合同模板' } },
      { path: 'contracts/templates/new', name: 'ContractTemplateCreate', component: () => import('@/views/contract/ContractTemplateEdit.vue'), meta: { noCache: true, title: '新建合同模板' } },
      { path: 'contracts/templates/:id', name: 'ContractTemplateEdit', component: () => import('@/views/contract/ContractTemplateEdit.vue'), meta: { noCache: true, title: '编辑合同模板' } },
      { path: 'contracts/:id', name: 'ContractDetail', component: () => import('@/views/contract/ContractDetail.vue'), meta: { title: '合同详情' } },
      { path: 'contracts/:id/edit', name: 'ContractEdit', component: () => import('@/views/contract/ContractEdit.vue'), meta: { noCache: true, title: '编辑合同' } },
      { path: 'dealers/profile', name: 'DealerProfileList', component: () => import('@/views/DealerProfileList.vue'), meta: { title: '经销商画像' } },
      { path: 'dealers/:id/profile', name: 'DealerProfile', component: () => import('@/views/DealerProfile.vue'), meta: { title: '经销商 360 画像' } },
      { path: 'order-create/sales/:id?', name: 'SalesOrderCreate', component: () => import('@/views/OrderCreate.vue'), meta: { noCache: true, title: '销售订单编辑' }, props: true },
      { path: 'order-create/purchase', name: 'PurchaseOrderCreate', component: () => import('@/views/OrderCreate.vue'), meta: { noCache: true, title: '新建采购订单' } },
      { path: 'positions', name: 'Positions', component: () => import('@/views/Positions.vue'), meta: { title: '销售岗位', permission: 'position:view' } },
      { path: 'roles-manage', name: 'RolesManage', component: () => import('@/views/Roles.vue'), meta: { title: '角色权限' } },
      { path: 'tenant-page-configs', name: 'TenantPageConfigs', component: () => import('@/views/TenantPageConfigs.vue'), meta: { title: '列表页配置', permission: 'tenant_ui_config:view' } },
      { path: 'receipt-edit/:id', name: 'ReceiptEdit', component: () => import('@/views/ReceiptEdit.vue'), meta: { noCache: true, title: '收货入库' } },
      { path: 'stock-move-edit/:id', name: 'StockMoveEdit', component: () => import('@/views/StockMoveEdit.vue'), meta: { noCache: true, title: '库存移动' } },
      { path: 'sales-out-edit/:id', name: 'SalesOutEdit', component: () => import('@/views/SalesOutEdit.vue'), meta: { noCache: true, title: '发货出库' } },
      { path: 'sales-return-edit', name: 'SalesReturnCreate', component: () => import('@/views/SalesReturnEdit.vue'), meta: { noCache: true, title: '新建销退' } },
      { path: 'sales-return-edit/:id', name: 'SalesReturnEdit', component: () => import('@/views/SalesReturnEdit.vue'), meta: { noCache: true, title: '销退订单' }, props: true },
      { path: 'purchase-return-edit/:id', name: 'PurchaseReturnEdit', component: () => import('@/views/PurchaseReturnEdit.vue'), meta: { noCache: true, title: '采退订单' } },
      { path: 'm/:key', name: 'Module', component: () => import('@/views/ModuleView.vue'), meta: { title: '业务模块' } },
      { path: 'api-call-logs', name: 'ApiCallLog', component: () => import('@/views/ApiCallLog.vue'), meta: { title: '接口调用日志' } },
      { path: 'customer-vouchers', name: 'CustomerVouchers', component: () => import('@/views/voucher/VoucherManage.vue'), meta: { title: '代金券管理' } },
      { path: 'product-mappings', name: 'ProductMappings', component: () => import('@/views/ProductMappings.vue'), meta: { title: '产品对码' } },
      { path: 'approval/todo', name: 'ApprovalTodo', component: () => import('@/views/approval/TodoCenter.vue'), meta: { title: '审批中心' } },
      { path: 'approval/templates', name: 'ApprovalTemplates', component: () => import('@/views/approval/ApprovalTemplates.vue'), meta: { title: '审批流配置', permission: 'approval:manage' } },
      { path: 'approval/delegations', name: 'ApprovalDelegations', component: () => import('@/views/approval/Delegations.vue'), meta: { title: '审批委托', permission: 'approval:manage' } },
      { path: 'approval/admin', name: 'ApprovalAdmin', component: () => import('@/views/approval/ApprovalAdmin.vue'), meta: { title: '审批监控', permission: 'approval:admin' } },
      { path: 'email-logs', name: 'EmailLogs', component: () => import('@/views/EmailLogs.vue'), meta: { title: '邮件发送日志' } },
      { path: 'notifications', name: 'Notifications', component: () => import('@/views/Notifications.vue'), meta: { title: '消息中心' } },
      { path: 'report-subscriptions', name: 'ReportSubscriptions', component: () => import('@/views/ReportSubscriptions.vue'), meta: { title: '报表订阅' } },
      { path: 'stocktakes', name: 'Stocktakes', component: () => import('@/views/Stocktakes.vue'), meta: { title: '库存盘点' } },
      { path: 'expiry-alerts', name: 'ExpiryAlerts', component: () => import('@/views/ExpiryAlerts.vue'), meta: { title: '效期预警' } },
      { path: 'async-tasks', name: 'AsyncTasks', component: () => import('@/views/AsyncTasks.vue'), meta: { title: '导入导出任务' } },
      { path: 'traceability', name: 'Traceability', component: () => import('@/views/Traceability.vue'), meta: { title: '序列号追溯' } },
      { path: 'log-center', name: 'LogCenter', component: () => import('@/views/LogCenter.vue'), meta: { title: '日志中心' } },
      { path: 'login-logs', name: 'LoginLogs', component: () => import('@/views/LoginLogs.vue'), meta: { title: '登录日志' } },
      { path: 'profile', name: 'Profile', component: () => import('@/views/Profile.vue'), meta: { title: '个人资料' } },
      { path: 'approvals/mine', name: 'MyApprovals', component: () => import('@/views/approval/TodoCenter.vue'), meta: { title: '我的审批' } }
    ]
  },
  { path: '/mobile/login', name: 'MLogin', component: () => import('@/views/mobile/MLogin.vue'), meta: { public: true, mobile: true } },
  { path: '/mobile/register', name: 'MRegister', component: () => import('@/views/mobile/MCustomerRegister.vue'), meta: { public: true, mobile: true, title: '客户注册' } },
  { path: '/m/register', redirect: '/mobile/register' },
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
      { path: 'smart-order', name: 'MSmartOrder', component: () => import('@/views/mobile/MSmartOrder.vue'), meta: { mobile: true, title: '智能下单' } },
      { path: 'surgery-reports', name: 'MSurgeryReports', component: () => import('@/views/mobile/MSurgeryReports.vue'), meta: { mobile: true, title: '手术报台' } },
      { path: 'surgery-reports/:id', name: 'MSurgeryReportDetail', component: () => import('@/views/mobile/MSurgeryReportDetail.vue'), meta: { mobile: true, title: '报台详情' } },
      { path: 'surgery-reports/create', name: 'MSurgeryReportCreate', component: () => import('@/views/mobile/MSurgeryReportCreate.vue'), meta: { mobile: true, title: '手术植入报台' } },
      { path: 'dashboard', name: 'MDashboard', component: () => import('@/views/mobile/MDashboard.vue'), meta: { mobile: true, title: '我的业绩' } },
      { path: 'approvals', name: 'MApprovals', component: () => import('@/views/mobile/MApprovals.vue'), meta: { mobile: true, title: '移动审批' } },
      { path: 'approvals/:id', name: 'MApprovalDetail', component: () => import('@/views/mobile/MApprovalDetail.vue'), meta: { mobile: true, title: '审批详情' } },
      { path: 'messages', name: 'MMessages', component: () => import('@/views/mobile/MMessages.vue'), meta: { mobile: true, title: '消息中心' } },
      { path: 'scan-receive', name: 'MReceiveScan', component: () => import('@/views/mobile/MReceiveScan.vue'), meta: { mobile: true, title: '扫码收货' } },
      { path: 'scan-inventory', name: 'MInventoryScan', component: () => import('@/views/mobile/MInventoryScan.vue'), meta: { mobile: true, title: '库存扫码查询' } },
      { path: 'profile', name: 'MProfile', component: () => import('@/views/mobile/MProfile.vue'), meta: { mobile: true, title: '我的' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/error/404' }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

router.beforeEach(async (to, from, next) => {
  // 设备形态分流：移动设备打开 PC 业务页时自动进入移动 H5（/mobile/*）。
  // 手动「切换到电脑版」(sessionStorage dms_view_pref=pc) 后不再强制弹回；
  // /print 打印页与错误页保持 PC 渲染，避免影响单据打印。
  const forceMobile = shouldUseMobile()
  const isPrint = to.path.startsWith('/print')
  const isError = to.path.startsWith('/error')
  if (forceMobile && !to.meta.mobile && !isPrint && !isError) {
    next(getToken() ? '/mobile/home' : '/mobile/login')
    return
  }
  const token = getToken()
  if (to.meta.public) {
    next()
    return
  }
  if (!token) {
    next(to.meta.mobile ? '/mobile/login' : '/login')
    return
  }
  const required = to.meta.permission || ROUTE_PERMISSION_MAP[to.name === 'Module' ? to.params.key : null]
  if (required) {
    const perms = buildPermissionSet()
    if (!perms.has(required)) {
      ElMessage.error('无权限访问该页面')
      next('/error/403')
      return
    }
  }
  next()
})

export default router
