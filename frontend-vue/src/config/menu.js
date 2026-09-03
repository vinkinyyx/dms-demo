export const MENU_GROUPS = [
  {
    group: '基础数据',
    items: [
      { key: 'products', icon: 'Goods', label: '产品管理', permissionCode: 'products:view' },
      { key: 'categories', icon: 'Files', label: '产品分类', permissionCode: 'product_category:view' },
      { key: 'product-lines', icon: 'Connection', label: '产品线管理', permissionCode: 'product_line:view' },
      { key: 'product-bundles', icon: 'Goods', label: '产品组合', permissionCode: 'product_bundle:view' },
      { key: 'dealers', icon: 'OfficeBuilding', label: '经销商管理', permissionCode: 'dealer:view' },
      { key: 'customer-registrations', icon: 'UserFilled', label: '客户注册审核', permissionCode: 'customer_registration:view' },
      { key: 'hospitals', icon: 'FirstAidKit', label: '医院/终端', permissionCode: 'hospital:view' },
      { key: 'warehouses', icon: 'House', label: '仓库管理', inventoryOnly: true, permissionCode: 'warehouse:view' },
      { key: 'regions', icon: 'Location', label: '区域管理', permissionCode: 'region:view' },
      { key: 'suppliers', icon: 'Shop', label: '供应商', inventoryOnly: true, permissionCode: 'supplier:view' },
      { key: 'product-prices', icon: 'Money', label: '产品价格', permissionCode: 'product_price:view' }
    ]
  },
  {
    group: '合同管理',
    items: [
      { key: 'contracts-workbench', icon: 'Document', label: '合同工作台', route: '/contracts', permissionCode: 'contract:view' },
      { key: 'contract-templates', icon: 'Files', label: '合同模板', route: '/contracts/templates', permissionCode: 'contract_template:manage' },
      { key: 'authorizations', icon: 'Key', label: '授权管理', route: '/authorizations', permissionCode: 'authorization:view' }
    ]
  },
  {
    group: '订单业务',
    items: [
      { key: 'orders', icon: 'Sell', label: '销售订单', permissionCode: 'sales_order:view' },
      { key: 'sales-returns', icon: 'RefreshLeft', label: '销退订单', permissionCode: 'sales_return:view' },
      { key: 'purchase-orders', icon: 'ShoppingCart', label: '采购订单', purchaseOnly: true, permissionCode: 'purchase_order:view' },
      { key: 'purchase-returns', icon: 'RefreshRight', label: '采退订单', purchaseOnly: true, permissionCode: 'purchase_return:view' }
    ]
  },
  {
    group: '库存业务',
    items: [
      { key: 'inventory', inventoryOnly: true, icon: 'Box', label: '库存查询', permissionCode: 'inventory:view' },
      { key: 'sales-outs', icon: 'Van', label: '销售出库', permissionCode: 'sales_out:view' },
      { key: 'receipts', inventoryOnly: true, icon: 'TakeawayBox', label: '收货入库', permissionCode: 'receipt:view' },
      { key: 'stock-moves', inventoryOnly: true, icon: 'Switch', label: '库存移动', permissionCode: 'stock_move:view' },
      { key: 'inventory-adjustments', inventoryOnly: true, icon: 'ScaleToOriginal', label: '库存调整', permissionCode: 'inventory_adjustment:view' }
      ,{ key: 'expiry-alerts', inventoryOnly: true, icon: 'AlarmClock', label: '效期预警', route: '/expiry-alerts', permissionCode: 'inventory:view' }
      ,{ key: 'stocktakes', inventoryOnly: true, icon: 'Histogram', label: '库存盘点', route: '/stocktakes', permissionCode: 'inventory:view' }
      ,{ key: 'traceability', inventoryOnly: true, icon: 'Search', label: '序列号追溯', route: '/traceability', permissionCode: 'inventory:view' }
      ,{ key: 'consignment-stock', icon: 'Box', label: '寄售库存', route: '/consignment-stock', permissionCode: 'inventory:view' }
    ]
  },
  {
    group: '手术与营销',
    items: [
      { key: 'surgery-reports', icon: 'FirstAidKit', label: '手术植入报台', permissionCode: 'surgery_report:view' },
      { key: 'promotions', icon: 'Present', label: '促销规则', permissionCode: 'promotion:view' },
      { key: 'product-global-discounts', icon: 'Discount', label: '产品全局折扣', permissionCode: 'product_global_discount:view' },
      { key: 'dealer-global-discounts', icon: 'GoldMedal', label: '客户全局折扣', permissionCode: 'dealer_global_discount:view' },
      { key: 'customer-vouchers', route: '/customer-vouchers', icon: 'Ticket', label: '代金券管理', permissionCode: 'customer_voucher:view' }
    ]
  },
  {
    group: '数据看板',
    items: [
      { key: 'dashboard', icon: 'DataLine', label: '数据驾驶舱', route: '/dashboard', permissionCode: 'dashboard:view' },
      { key: 'reports', icon: 'Document', label: '报表中心', route: '/reports', permissionCode: 'report:view' },
      { key: 'dealer-profile', icon: 'User', label: '经销商画像', route: '/dealers/profile', permissionCode: 'dealer:view' },
      { key: 'dealer-credit', icon: 'Wallet', label: '经销商资信与账期', route: '/dealer-credit', permissionCode: 'dealer:view' }
    ]
  },
  {
    group: '业务报表',
    items: [
      { key: 'report-sales-ranking', icon: 'DataAnalysis', label: '销售业绩排行', route: '/reports?key=sales-ranking', permissionCode: 'report_sales_ranking:view' },
      { key: 'report-product-top10', icon: 'TrophyBase', label: '产品销售 TOP10', route: '/reports?key=product-top10', permissionCode: 'report_product_top10:view' },
      { key: 'report-inventory-turnover', icon: 'Histogram', label: '库存周转', route: '/reports?key=inventory-turnover', permissionCode: 'report_inventory_turnover:view' },
      { key: 'report-subscriptions', icon: 'BellFilled', label: '报表订阅', route: '/report-subscriptions', permissionCode: 'report:view' },
      { key: 'report-surgery-stats', icon: 'PieChart', label: '手术报台统计', route: '/reports?key=surgery-stats', permissionCode: 'report_surgery_stats:view' },
      { key: 'report-receivables', icon: 'Coin', label: '应收款项', route: '/reports?key=receivables', permissionCode: 'report_receivables:view' },
      { key: 'report-order-trace', icon: 'TrendCharts', label: '订单追溯', route: '/reports?key=order-trace', permissionCode: 'report_order_trace:view' }
    ]
  },
  {
    group: '产品对码',
    manufacturerOnly: true,
    items: [
      { key: 'product-mappings', icon: 'Connection', label: '产品对码', route: '/product-mappings', permissionCode: 'product_mapping:view' }
    ]
  },
  {
    group: '审批中心',
    items: [
      { key: 'approval-todo', icon: 'Bell', label: '我的审批', route: '/approval/todo' },
      { key: 'approval-templates', icon: 'SetUp', label: '审批流配置', route: '/approval/templates', permissionCode: 'approval:manage' },
      { key: 'approval-delegations', icon: 'Switch', label: '审批委托', route: '/approval/delegations', permissionCode: 'approval:manage' },
      { key: 'approval-admin', icon: 'Monitor', label: '审批监控', route: '/approval/admin', permissionCode: 'approval:admin' }
    ]
  },
  {
    group: '用户与权限',
    items: [
      { key: 'positions', icon: 'OfficeBuilding', label: '销售岗位', route: '/positions', permissionCode: 'position:view' },
      { key: 'users', icon: 'User', label: '账号管理', permissionCode: 'user:view' },
      { key: 'roles-manage', route: '/roles-manage', icon: 'Avatar', label: '角色权限', permissionCode: 'role:view' },
      { key: 'tenant-page-configs', route: '/tenant-page-configs', icon: 'Setting', label: '列表页配置', permissionCode: 'tenant_ui_config:view' },
      { key: 'log-center', icon: 'Notebook', label: '日志中心', route: '/log-center', permissionCode: 'api_log:view' }
      ,{ key: 'async-tasks', icon: 'Files', label: '导入导出任务', route: '/async-tasks' }
    ]
  }
]





