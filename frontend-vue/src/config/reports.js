// 13 张报表 + Dashboard 的元数据
export const REPORTS = {
  'sales-ranking': {
    title: '销售业绩排行', group: '销售类', icon: 'DataAnalysis',
    api: '/api/reports/sales-ranking', method: 'get', defaultRange: 'year',
    desc: '经销商销售排行与同比，含客单价、审核通过率、订单活跃度',
    filters: [
      { key: 'from', label: '起始日期', type: 'date' },
      { key: 'to', label: '截止日期', type: 'date' },
      { key: 'level', label: '经销商级别', type: 'select', options: [
        { value: 'A', label: 'A级' }, { value: 'B', label: 'B级' }, { value: 'C', label: 'C级' } ] },
      { key: 'region', label: '区域（模糊）', type: 'text' },
      { key: 'status', label: '订单状态', type: 'select', options: [
        { value: 'DRAFT', label: '草稿' }, { value: 'APPROVED', label: '已审核' },
        { value: 'SHIPPING', label: '出库中' }, { value: 'COMPLETED', label: '已完成' } ] },
      { key: 'orderType', label: '订单类型', type: 'select', options: [
        { value: 'NORMAL', label: '常规' }, { value: 'URGENT', label: '加急' } ] }
    ],
    kpi: [
      { key: 'totalAmount', label: '总销售额', agg: 'sum', value: 'totalAmount', format: 'money', color: '#2e6ba8' },
      { key: 'orderCount', label: '订单数', agg: 'sum', value: 'orderCount', format: 'number', color: '#52c41a' },
      { key: 'avgAmount', label: '平均客单价', agg: 'avg', value: 'avgAmount', format: 'money', color: '#faad14' },
      { key: 'dealerCount', label: '活跃经销商数', agg: 'count', value: 'dealerName', format: 'number', color: '#ff4d4f' }
    ],
    chart: { type: 'bar', x: 'dealerName', y: 'totalAmount', name: '销售额', topN: 20 },
    cols: [
      { k: 'dealerCode', l: '编码', w: 120, link: { type: 'route', route: 'DealerProfile', param: 'dealerId' } },
      { k: 'dealerName', l: '经销商', w: 180, link: { type: 'route', route: 'DealerProfile', param: 'dealerId' } },
      { k: 'dealerLevel', l: '级别', w: 70 }, { k: 'region', l: '区域', w: 100 },
      { k: 'orderCount', l: '订单数', w: 90, align: 'right' },
      { k: 'totalAmount', l: '销售总额', w: 140, format: 'money', align: 'right' },
      { k: 'avgAmount', l: '客单价', w: 120, format: 'money', align: 'right' },
      { k: 'approvedCount', l: '通过', w: 70, align: 'right' },
      { k: 'draftCount', l: '草稿', w: 70, align: 'right' },
      { k: 'cancelledCount', l: '取消', w: 70, align: 'right' },
      { k: 'lastOrderAt', l: '最近下单', w: 160 },
      { k: 'firstOrderAt', l: '首次下单', w: 160 }
    ],
    drilldown: {
      type: 'row', target: 'dealerId', route: { name: 'DealerProfile' },
      child: { title: '该经销商订单明细', endpoint: (row, ctx) => `/api/reports/dealer-orders?dealerId=${row.dealerId}` + (ctx && ctx.from ? '&from=' + ctx.from : '') + (ctx && ctx.to ? '&to=' + ctx.to : '') }
    },
    exportName: '销售业绩排行'
  },
  'product-top10': {
    title: '产品销售 TOP10', group: '销售类', icon: 'TrophyBase',
    api: '/api/reports/product-top10', method: 'get', defaultRange: 'year',
    desc: '按销售额/销量排序的产品汇总，含分类、覆盖经销商数',
    filters: [
      { key: 'from', label: '起始日期', type: 'date' },
      { key: 'to', label: '截止日期', type: 'date' },
      { key: 'categoryCode', label: '产品分类', type: 'text' }
    ],
    kpi: [
      { key: 'totalAmount', label: '总销售额', agg: 'sum', value: 'totalAmount', format: 'money', color: '#2e6ba8' },
      { key: 'totalQty', label: '总销量', agg: 'sum', value: 'totalQty', format: 'number', color: '#52c41a' },
      { key: 'dealerCount', label: '覆盖经销商数', agg: 'sum', value: 'dealerCount', format: 'number', color: '#faad14' },
      { key: 'orderCount', label: '订单数', agg: 'sum', value: 'orderCount', format: 'number', color: '#ff4d4f' }
    ],
    chart: { type: 'bar', x: 'productName', y: 'totalAmount', name: '销售额', topN: 20 },
    cols: [
      { k: 'productCode', l: '产品编码', w: 130, link: { type: 'route', route: 'ProductDetail', param: 'productId' } }, { k: 'productName', l: '产品名称', w: 200, link: { type: 'route', route: 'ProductDetail', param: 'productId' } },
      { k: 'productSpec', l: '规格', w: 120 }, { k: 'categoryName', l: '分类', w: 120 },
      { k: 'productUnit', l: '单位', w: 70 },
      { k: 'totalQty', l: '销量', w: 100, align: 'right' },
      { k: 'totalAmount', l: '销售额', w: 140, format: 'money', align: 'right' },
      { k: 'orderCount', l: '订单数', w: 90, align: 'right' },
      { k: 'dealerCount', l: '经销商数', w: 100, align: 'right' },
      { k: 'avgUnitPrice', l: '均价', w: 100, format: 'money', align: 'right' },
      { k: 'lastSaleAt', l: '最近销售', w: 160 }
    ],
    drilldown: {
      type: 'row', target: 'productId', route: { name: 'ProductDetail' },
      child: { title: '该产品销售明细', endpoint: (row, ctx) => `/api/reports/product-sales-detail?productId=${row.productId}` + (ctx && ctx.from ? '&from=' + ctx.from : '') + (ctx && ctx.to ? '&to=' + ctx.to : '') }
    },
    exportName: '产品销售TOP10'
  },
  'inventory-turnover': {
    title: '库存周转', group: '库存类', icon: 'Histogram',
    api: '/api/reports/inventory-turnover', method: 'get', defaultRange: 'none',
    desc: '按产品聚合当前库存与平均库龄',
    filters: [ { key: 'categoryCode', label: '产品分类', type: 'text' } ],
    kpi: [
      { key: 'currentStock', label: '总库存', agg: 'sum', value: 'currentStock', format: 'number', color: '#2e6ba8' },
      { key: 'qualifiedStock', label: '合格库存', agg: 'sum', value: 'qualifiedStock', format: 'number', color: '#52c41a' },
      { key: 'pendingStock', label: '待检库存', agg: 'sum', value: 'pendingStock', format: 'number', color: '#faad14' },
      { key: 'defectiveStock', label: '不合格库存', agg: 'sum', value: 'defectiveStock', format: 'number', color: '#ff4d4f' }
    ],
    chart: { type: 'bar', x: 'productName', y: 'currentStock', name: '当前库存', topN: 20 },
    cols: [
      { k: 'productCode', l: '编码', w: 130 }, { k: 'productName', l: '产品', w: 200 },
      { k: 'productSpec', l: '规格', w: 120 }, { k: 'categoryName', l: '分类', w: 120 },
      { k: 'currentStock', l: '当前库存', w: 110, align: 'right' },
      { k: 'qualifiedStock', l: '合格', w: 100, align: 'right' },
      { k: 'pendingStock', l: '待检', w: 100, align: 'right' },
      { k: 'defectiveStock', l: '不合格', w: 100, align: 'right' },
      { k: 'recentInQty', l: '近30天入', w: 100, align: 'right' },
      { k: 'recentOutQty', l: '近30天出', w: 100, align: 'right' },
      { k: 'avgAgeDays', l: '平均库龄(天)', w: 120, align: 'right' }
    ],
    drilldown: {
      type: 'row', target: 'productId', route: { name: 'ProductDetail' },
      child: { title: '该产品销售明细', endpoint: (row) => `/api/reports/product-sales-detail?productId=${row.productId}` }
    },
    exportName: '库存周转'
  },
  'order-trace': {
    title: '订单追溯', group: '订单类', icon: 'TrendCharts',
    api: '/api/reports/order-trace', method: 'get', defaultRange: '30d',
    desc: '订单 -> 出库 -> 收货全链路追溯',
    filters: [
      { key: 'from', label: '起始日期', type: 'date' },
      { key: 'to', label: '截止日期', type: 'date' },
      { key: 'status', label: '订单状态', type: 'select', options: [
        { value: 'DRAFT', label: '草稿' }, { value: 'APPROVED', label: '已审核' },
        { value: 'SHIPPING', label: '出库中' }, { value: 'COMPLETED', label: '已完成' },
        { value: 'CANCELLED', label: '已取消' } ] },
      { key: 'orderType', label: '订单类型', type: 'select', options: [
        { value: 'NORMAL', label: '常规' }, { value: 'URGENT', label: '加急' } ] }
    ],
    kpi: [
      { key: 'orderCount', label: '订单数', agg: 'count', value: 'orderId', format: 'number', color: '#2e6ba8' },
      { key: 'totalAmount', label: '订单总额', agg: 'sum', value: 'totalAmount', format: 'money', color: '#52c41a' }
    ],
    chart: { type: 'bar', x: 'orderDate', y: 'orderId', name: '订单数' },
    cols: [
      { k: 'orderCode', l: '订单号', w: 180, link: { type: 'route', route: 'SalesOutEdit', param: 'orderId' } },
      { k: 'orderType', l: '类型', w: 80 }, { k: 'dealerName', l: '经销商', w: 180 },
      { k: 'approvalStatus', l: '订单状态', w: 100 },
      { k: 'totalAmount', l: '订单金额', w: 130, format: 'money', align: 'right' },
      { k: 'productCount', l: '产品数', w: 80, align: 'right' },
      { k: 'orderDate', l: '下单时间', w: 160 }, { k: 'approvedAt', l: '审核时间', w: 160 },
      { k: 'shippedAt', l: '出库时间', w: 160 }, { k: 'receivedAt', l: '收货时间', w: 160 },
      { k: 'shipmentStatus', l: '出库状态', w: 110 }
    ],
    drilldown: {
      type: 'row', target: 'orderId',
      child: { title: '订单行与状态历史', endpoint: (row) => `/api/reports/order-detail-child/${row.orderId}` }
    },
    exportName: '订单追溯'
  },
  'receivables': {
    title: '应收款项', group: '财务类', icon: 'Coin',
    api: '/api/reports/receivables', method: 'get', defaultRange: '90d',
    desc: '经销商应收汇总与 30/60/90+ 账龄分布',
    filters: [
      { key: 'from', label: '起始日期', type: 'date' },
      { key: 'to', label: '截止日期', type: 'date' },
      { key: 'level', label: '经销商级别', type: 'select', options: [
        { value: 'A', label: 'A级' }, { value: 'B', label: 'B级' }, { value: 'C', label: 'C级' } ] },
      { key: 'region', label: '区域', type: 'text' }
    ],
    kpi: [
      { key: 'totalReceivable', label: '应收总额', agg: 'sum', value: 'totalReceivable', format: 'money', color: '#ff4d4f' },
      { key: 'age30', label: '0-30天', agg: 'sum', value: 'age30', format: 'money', color: '#52c41a' },
      { key: 'ageOver90', label: '90+超期', agg: 'sum', value: 'ageOver90', format: 'money', color: '#ff4d4f' },
      { key: 'dealerCount', label: '应收经销商数', agg: 'count', value: 'dealerName', format: 'number', color: '#2e6ba8' }
    ],
    chart: { type: 'stackBar', x: 'dealerName', y: ['age30', 'age60', 'age90', 'ageOver90'], name: '账龄', topN: 20 },
    cols: [
      { k: 'dealerCode', l: '编码', w: 120, link: { type: 'route', route: 'DealerProfile', param: 'dealerId' } },
      { k: 'dealerName', l: '经销商', w: 180, link: { type: 'route', route: 'DealerProfile', param: 'dealerId' } },
      { k: 'dealerLevel', l: '级别', w: 70 }, { k: 'region', l: '区域', w: 100 },
      { k: 'unpaidCount', l: '未结单数', w: 100, align: 'right' },
      { k: 'totalReceivable', l: '应收总额', w: 140, format: 'money', align: 'right' },
      { k: 'age30', l: '0-30天', w: 120, format: 'money', align: 'right' },
      { k: 'age60', l: '31-60天', w: 120, format: 'money', align: 'right' },
      { k: 'age90', l: '61-90天', w: 120, format: 'money', align: 'right' },
      { k: 'ageOver90', l: '90+天', w: 120, format: 'money', align: 'right' },
      { k: 'oldestUnpaidAt', l: '最早未结', w: 160 }
    ],
    drilldown: {
      type: 'row', target: 'dealerId', route: { name: 'DealerProfile' },
      child: { title: '该经销商订单明细', endpoint: (row) => `/api/reports/dealer-orders?dealerId=${row.dealerId}` }
    },
    exportName: '应收款项'
  },
  'surgery-stats': {
    title: '手术报台统计', group: '报台类', icon: 'PieChart',
    api: '/api/reports/surgery-stats', method: 'get', defaultRange: 'year',
    desc: '医院报台排行与医生、产品覆盖',
    filters: [
      { key: 'from', label: '起始日期', type: 'date' },
      { key: 'to', label: '截止日期', type: 'date' }
    ],
    kpi: [
      { key: 'surgeryCount', label: '报台数', agg: 'sum', value: 'surgeryCount', format: 'number', color: '#2e6ba8' },
      { key: 'totalImplants', label: '植入数', agg: 'sum', value: 'totalImplants', format: 'number', color: '#52c41a' },
      { key: 'hospitalCount', label: '覆盖医院', agg: 'count', value: 'hospitalName', format: 'number', color: '#faad14' },
      { key: 'doctorCount', label: '覆盖医生', agg: 'sum', value: 'doctorCount', format: 'number', color: '#ff4d4f' }
    ],
    chart: { type: 'bar', x: 'hospitalName', y: 'surgeryCount', name: '报台数', topN: 20 },
    cols: [
      { k: 'hospitalCode', l: '编码', w: 130 }, { k: 'hospitalName', l: '医院', w: 220 },
      { k: 'hospitalLevel', l: '级别', w: 80 }, { k: 'city', l: '城市', w: 100 },
      { k: 'province', l: '省份', w: 100 },
      { k: 'surgeryCount', l: '报台数', w: 90, align: 'right' },
      { k: 'totalImplants', l: '植入数', w: 90, align: 'right' },
      { k: 'productCount', l: '产品数', w: 90, align: 'right' },
      { k: 'doctorCount', l: '医生数', w: 90, align: 'right' },
      { k: 'firstSurgeryAt', l: '首次报台', w: 160 }, { k: 'lastSurgeryAt', l: '最近报台', w: 160 }
    ],
    drilldown: {
      type: 'row', target: 'hospitalId', route: { name: 'HospitalDetail' },
      child: { title: '该医院/终端的手术明细', endpoint: (row) => `/api/reports/hospital-surgery?hospitalId=${row.hospitalId}` }
    },
    exportName: '手术报台统计'
  },
  'sales': {
    title: '销售明细', group: '销售类', icon: 'List',
    api: '/api/reports/sales/query', method: 'post', defaultRange: 'year',
    desc: '按月聚合的经销商销售数量与金额（ReportService.sales）',
    filters: [
      { key: 'from', label: '起始月份', type: 'text', placeholder: 'YYYY-MM' },
      { key: 'to', label: '截止月份', type: 'text', placeholder: 'YYYY-MM' }
    ],
    kpi: [
      { key: 'totalAmount', label: '总销售额', agg: 'sum', value: 'totalAmount', format: 'money', color: '#2e6ba8' },
      { key: 'totalQty', label: '总销量', agg: 'sum', value: 'totalQty', format: 'number', color: '#52c41a' }
    ],
    chart: { type: 'line', x: 'period', y: 'totalAmount', name: '销售额' },
    cols: [
      { k: 'dealerId', l: '经销商ID', w: 100 }, { k: 'period', l: '期间', w: 120 },
      { k: 'totalQty', l: '销量', w: 120, align: 'right' },
      { k: 'totalAmount', l: '销售额', w: 140, format: 'money', align: 'right' }
    ],
    drilldown: {
      type: 'row', target: 'dealerId', route: { name: 'DealerProfile' },
      child: { title: '该经销商订单明细', endpoint: (row) => `/api/reports/dealer-orders?dealerId=${row.dealerId}` }
    },
    exportName: '销售明细'
  },
  'contract': {
    title: '合同台账', group: '合同/授权', icon: 'Document',
    api: '/api/reports/contract/query', method: 'post', defaultRange: 'none',
    desc: '按状态聚合的合同数量与占比',
    filters: [],
    kpi: [ { key: 'total', label: '合同总数', agg: 'sum', value: 'cnt', format: 'number', color: '#2e6ba8' } ],
    chart: { type: 'pie', x: 'status', y: 'cnt', name: '合同数' },
    cols: [ { k: 'status', l: '状态', w: 160 }, { k: 'cnt', l: '数量', w: 120, align: 'right' } ],
    drilldown: { type: 'none' }, exportName: '合同台账'
  },
  'authorization': {
    title: '授权余额/超期', group: '合同/授权', icon: 'Key',
    api: '/api/reports/authorization/query', method: 'post', defaultRange: 'none',
    desc: '经销商授权台账与分类汇总',
    filters: [ { key: 'authType', label: '授权类型', type: 'select', options: [
      { value: 'PRODUCT', label: '产品' }, { value: 'REGION', label: '区域' }, { value: 'HOSPITAL', label: '医院' } ] } ],
    kpi: [ { key: 'total', label: '授权数', agg: 'count', value: 'dealerId', format: 'number', color: '#2e6ba8' } ],
    chart: { type: 'pie', x: 'authType', y: 'cnt', name: '授权数' },
    cols: [ { k: 'dealerId', l: '经销商', w: 120 }, { k: 'authType', l: '授权类型', w: 120 },
            { k: 'cnt', l: '数量', w: 100, align: 'right' } ],
    drilldown: { type: 'none' }, exportName: '授权余额超期'
  },
  'loan': {
    title: '借货余额/超期', group: '合同/授权', icon: 'Van',
    api: '/api/reports/loan/query', method: 'post', defaultRange: 'none',
    desc: '借货台账与状态汇总',
    filters: [],
    kpi: [ { key: 'total', label: '借货单数', agg: 'sum', value: 'cnt', format: 'number', color: '#2e6ba8' } ],
    chart: { type: 'pie', x: 'status', y: 'cnt', name: '借货数' },
    cols: [ { k: 'status', l: '状态', w: 160 }, { k: 'cnt', l: '数量', w: 120, align: 'right' } ],
    drilldown: { type: 'none' }, exportName: '借货余额超期'
  },
  'rebate-discount': {
    title: '返利/折扣对账', group: '财务类', icon: 'Discount',
    api: '/api/reports/rebate/query', method: 'post', defaultRange: 'year',
    desc: '经销商返利周期数据（合并折扣请见详情）',
    filters: [ { key: 'periodYyyymm', label: '期间', type: 'text', placeholder: 'YYYYMM' } ],
    kpi: [
      { key: 'netRebate', label: '净返利总额', agg: 'sum', value: 'netRebate', format: 'money', color: '#2e6ba8' },
      { key: 'grossRebate', label: '毛返利', agg: 'sum', value: 'grossRebate', format: 'money', color: '#52c41a' }
    ],
    chart: { type: 'line', x: 'periodYyyymm', y: 'netRebate', name: '净返利' },
    cols: [
      { k: 'dealerId', l: '经销商', w: 120 }, { k: 'periodYyyymm', l: '期间', w: 120 },
      { k: 'grossRebate', l: '毛返利', w: 130, format: 'money', align: 'right' },
      { k: 'deductions', l: '扣减', w: 130, format: 'money', align: 'right' },
      { k: 'netRebate', l: '净返利', w: 140, format: 'money', align: 'right' }
    ],
    drilldown: {
      type: 'row', target: 'dealerId', route: { name: 'DealerProfile' },
      child: { title: '该经销商订单明细', endpoint: (row) => `/api/reports/dealer-orders?dealerId=${row.dealerId}` }
    },
    exportName: '返利折扣对账'
  },
  'inventory-aging': {
    title: '库存呆滞/超期', group: '库存类', icon: 'Warning',
    api: '/api/reports/inventory-aging', method: 'get', defaultRange: 'none',
    desc: '按库龄分桶的库存预警，含效期/已过期/滞销/正常',
    filters: [
      { key: 'agingDays', label: '滞销阈值(天)', type: 'number' },
      { key: 'categoryCode', label: '产品分类', type: 'text' }
    ],
    kpi: [
      { key: 'skus', label: '滞销SKU数', agg: 'count', value: 'productId', format: 'number', color: '#ff4d4f' }
    ],
    chart: { type: 'pie', x: 'ageBucket', y: 'qty', name: '库龄分布' },
    cols: [
      { k: 'productCode', l: '产品编码', w: 130 }, { k: 'productName', l: '产品', w: 200 },
      { k: 'batchNo', l: '批号', w: 150 }, { k: 'prodDate', l: '生产日期', w: 120 },
      { k: 'expDate', l: '效期', w: 120 },
      { k: 'qty', l: '数量', w: 100, align: 'right' },
      { k: 'daysToExpire', l: '距到期(天)', w: 110, align: 'right' },
      { k: 'ageDays', l: '库龄(天)', w: 100, align: 'right' },
      { k: 'ageBucket', l: '状态', w: 110 }
    ],
    drilldown: {
      type: 'row', target: 'productId', route: { name: 'ProductDetail' },
      child: { title: '该产品销售明细', endpoint: (row) => `/api/reports/product-sales-detail?productId=${row.productId}` }
    }, exportName: '库存呆滞超期'
  },
  'order-approval': {
    title: '拒单率/审批时长', group: '订单类', icon: 'CircleCheck',
    api: '/api/reports/order-approval-stats', method: 'get', defaultRange: '90d',
    desc: '订单审批通过率、平均审批时长分布',
    filters: [
      { key: 'from', label: '起始日期', type: 'date' },
      { key: 'to', label: '截止日期', type: 'date' }
    ],
    kpi: [
      { key: 'rejectRate', label: '拒单率', agg: 'custom', value: 'rejectRate', format: 'percent', color: '#ff4d4f' },
      { key: 'avgHours', label: '平均审批时长(小时)', agg: 'avg', value: 'avgHours', format: 'number', color: '#2e6ba8' }
    ],
    chart: { type: 'line', x: 'date', y: 'submitCount', name: '提交数' },
    cols: [
      { k: 'orderCode', l: '单号', w: 180, link: { type: 'route', route: 'SalesOutEdit', param: 'orderId' } },
      { k: 'submitAt', l: '提交时间', w: 160 }, { k: 'approvedAt', l: '审核时间', w: 160 },
      { k: 'rejectedAt', l: '驳回时间', w: 160 },
      { k: 'currentStatus', l: '当前状态', w: 100 }, { k: 'result', l: '结果', w: 100 },
      { k: 'approverName', l: '审核人', w: 120 }
    ],
    drilldown: { type: 'none' }, exportName: '拒单率审批时长'
  }
}

export const REPORT_GROUPS = [
  { title: '销售类', icon: 'Sell', keys: ['sales-ranking', 'product-top10', 'sales'] },
  { title: '库存类', icon: 'Box', keys: ['inventory-turnover', 'inventory-aging'] },
  { title: '订单类', icon: 'Document', keys: ['order-trace', 'order-approval'] },
  { title: '财务类', icon: 'Coin', keys: ['receivables', 'rebate-discount'] },
  { title: '合同/授权', icon: 'Key', keys: ['contract', 'authorization', 'loan'] },
  { title: '报台与画像', icon: 'FirstAidKit', keys: ['surgery-stats'] }
]

export function rangeFor(key) {
  const now = new Date()
  const y = now.getFullYear()
  const m = now.getMonth()
  const d = now.getDate()
  const fmt = (x) => {
    const yy = x.getFullYear()
    const mm = String(x.getMonth() + 1).padStart(2, '0')
    const dd = String(x.getDate()).padStart(2, '0')
    return `${yy}-${mm}-${dd}`
  }
  const to = new Date(y, m, d)
  switch (key) {
    case 'today': return [fmt(new Date(y, m, d)), fmt(to)]
    case '7d': { const x = new Date(y, m, d - 7); return [fmt(x), fmt(to)] }
    case '30d': { const x = new Date(y, m, d - 30); return [fmt(x), fmt(to)] }
    case '90d': { const x = new Date(y, m, d - 90); return [fmt(x), fmt(to)] }
    case 'week': { const day = now.getDay() || 7; const x = new Date(y, m, d - day + 1); return [fmt(x), fmt(to)] }
    case 'month': return [fmt(new Date(y, m, 1)), fmt(to)]
    case 'quarter': { const q = Math.floor(m / 3) * 3; return [fmt(new Date(y, q, 1)), fmt(to)] }
    case 'year': return [fmt(new Date(y, 0, 1)), fmt(to)]
    case 'none':
    default: return [null, null]
  }
}
