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
      { path: 'admin', name: 'Admin', component: () => import('@/views/Admin.vue'), meta: { title: '后台管理' } },
      { path: 'order-create/sales', name: 'SalesOrderCreate', component: () => import('@/views/OrderCreate.vue'), meta: { title: '新建销售订单' } },
      { path: 'order-create/purchase', name: 'PurchaseOrderCreate', component: () => import('@/views/OrderCreate.vue'), meta: { title: '新建采购订单' } },
      { path: 'positions', name: 'Positions', component: () => import('@/views/PositionTree.vue'), meta: { title: '销售岗位' } },
      { path: 'receipt-edit/:id', name: 'ReceiptEdit', component: () => import('@/views/ReceiptEdit.vue'), meta: { title: '收货入库' } },
      { path: 'stock-move-edit/:id', name: 'StockMoveEdit', component: () => import('@/views/StockMoveEdit.vue'), meta: { title: '库存移动' } },
      { path: 'sales-out-edit/:id', name: 'SalesOutEdit', component: () => import('@/views/SalesOutEdit.vue'), meta: { title: '发货出库' } },
      { path: 'm/:key', name: 'Module', component: () => import('@/views/ModuleView.vue'), meta: { title: '业务模块' } }
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
      { path: 'dashboard', name: 'MDashboard', component: () => import('@/views/mobile/MDashboard.vue'), meta: { mobile: true, title: '仪表盘' } },
      { path: 'orders', name: 'MOrders', component: () => import('@/views/mobile/MOrders.vue'), meta: { mobile: true, title: '订单' } },
      { path: 'orders/create', name: 'MOrderCreate', component: () => import('@/views/mobile/MOrderCreate.vue'), meta: { mobile: true, title: '下订单' } },
      { path: 'receipt', name: 'MReceipt', component: () => import('@/views/mobile/MReceipt.vue'), meta: { mobile: true, title: '收货' } },
      { path: 'shipment', name: 'MShipment', component: () => import('@/views/mobile/MShipment.vue'), meta: { mobile: true, title: '发货' } },
      { path: 'report', name: 'MReport', component: () => import('@/views/mobile/MReport.vue'), meta: { mobile: true, title: '报表' } },
      { path: 'inventory', name: 'MInventory', component: () => import('@/views/mobile/MInventory.vue'), meta: { mobile: true, title: '库存' } },
      { path: 'messages', name: 'MMessages', component: () => import('@/views/mobile/MMessages.vue'), meta: { mobile: true, title: '消息' } },
      { path: 'surgery-reports/create', name: 'MSurgeryReportCreate', component: () => import('@/views/mobile/MSurgeryReportCreate.vue'), meta: { mobile: true, title: '手术植入报台' } },
      { path: 'report-order-trace', name: 'MOrderTrace', component: () => import('@/views/mobile/MOrderTrace.vue'), meta: { mobile: true, title: '订单追溯' } }
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
