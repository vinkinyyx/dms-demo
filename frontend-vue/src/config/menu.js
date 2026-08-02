export const MENU_GROUPS = [
  {
    group: '基础数据',
    items: [
      { key: 'products', icon: 'Goods', label: '产品管理' },
      { key: 'categories', icon: 'Files', label: '产品分类' },
      { key: 'product-lines', icon: 'Connection', label: '产品线管理' },
      { key: 'product-package-levels', icon: 'Box', label: '产品包装层级' },
      { key: 'product-bundles', icon: 'Goods', label: '产品组合' },
      { key: 'dealers', icon: 'OfficeBuilding', label: '经销商管理' },
      { key: 'hospitals', icon: 'FirstAidKit', label: '医院/终端' },
      { key: 'warehouses', icon: 'House', label: '仓库管理' },
      { key: 'regions', icon: 'Location', label: '区域管理' },
      { key: 'suppliers', icon: 'Shop', label: '供应商' },
      { key: 'product-prices', icon: 'Money', label: '产品价格' }
    ]
  },
  {
    group: '合同授权',
    items: [
      { key: 'contract-apps', icon: 'EditPen', label: '合同申请' },
      { key: 'contracts', icon: 'Document', label: '合同' },
      { key: 'authorizations', icon: 'Key', label: '授权管理' }
    ]
  },
  {
    group: '订单业务',
    items: [
      { key: 'orders', icon: 'Sell', label: '销售订单' },
      { key: 'sales-returns', icon: 'RefreshLeft', label: '销退订单' },
      { key: 'purchase-orders', icon: 'ShoppingCart', label: '采购订单' },
      { key: 'purchase-returns', icon: 'RefreshRight', label: '采退订单' }
    ]
  },
  {
    group: '库存业务',
    items: [
      { key: 'inventory', icon: 'Box', label: '库存查询' },
      { key: 'sales-outs', icon: 'Van', label: '销售出库' },
      { key: 'receipts', icon: 'TakeawayBox', label: '收货入库' },
      { key: 'stock-moves', icon: 'Switch', label: '库存移动' },
      { key: 'inventory-adjustments', icon: 'ScaleToOriginal', label: '库存调整' }
    ]
  },
  {
    group: '手术与营销',
    items: [
      { key: 'surgery-reports', icon: 'FirstAidKit', label: '手术植入报台' },
      { key: 'promotions', icon: 'Present', label: '促销规则' }
    ]
  },
  {
    group: '业务报表',
    items: [
      { key: 'dashboard', icon: 'DataLine', label: '数据看板', route: '/dashboard' },
      { key: 'report-sales-ranking', icon: 'DataAnalysis', label: '销售业绩排行' },
      { key: 'report-product-top10', icon: 'TrophyBase', label: '产品销售 TOP10' },
      { key: 'report-inventory-turnover', icon: 'Histogram', label: '库存周转' },
      { key: 'report-surgery-stats', icon: 'PieChart', label: '手术报台统计' },
      { key: 'report-receivables', icon: 'Coin', label: '应收账款' },
      { key: 'report-order-trace', icon: 'TrendCharts', label: '订单追溯' }
    ]
  },
  {
    group: '用户与权限',
    items: [
      { key: 'positions', icon: 'OfficeBuilding', label: '销售岗位', route: '/positions' },
      { key: 'users', icon: 'User', label: '账号管理' },
      { key: 'roles', icon: 'Avatar', label: '角色管理' },
      { key: 'admin', icon: 'Setting', label: '后台管理', route: '/admin' },
      { key: 'api-call-logs', icon: 'Connection', label: '接口调用日志', route: '/api-call-logs' }
    ]
  }
]
