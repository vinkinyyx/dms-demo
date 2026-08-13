const { loginAPI, apiGet } = require('./harness');
(async()=>{
  const r = await loginAPI('sys_admin','Dms@123456');
  const token = r.json.data.accessToken;
  const endpoints = [
    '/api/auth/me','/api/menus/my','/api/menus/tree','/api/menus','/api/rbac/permissions','/api/rbac/my-permissions',
    '/api/dashboard/home','/api/dashboard/kpi','/api/home/kpi','/api/home','/api/products?page=1&size=5',
    '/api/dealers?page=1&size=5','/api/hospitals?page=1&size=5','/api/warehouses?page=1&size=5',
    '/api/product-categories/tree','/api/product-lines/tree','/api/regions/tree','/api/suppliers?page=1&size=5',
    '/api/product-prices?page=1&size=5','/api/contracts?page=1&size=5','/api/contracts/templates?page=1&size=5',
    '/api/orders?page=1&size=5','/api/sales-orders?page=1&size=5','/api/sales-returns?page=1&size=5',
    '/api/purchase-orders?page=1&size=5','/api/purchase-returns?page=1&size=5','/api/inventory?page=1&size=5',
    '/api/inventory/query?page=1&size=5','/api/goods-receipts?page=1&size=5','/api/goods-issues?page=1&size=5',
    '/api/inventory-moves?page=1&size=5','/api/inventory-checks?page=1&size=5','/api/inventory-adjustments?page=1&size=5',
    '/api/surgery-reports?page=1&size=5','/api/marketing-activities?page=1&size=5','/api/authorizations?page=1&size=5',
    '/api/approval/pending','/api/approval/tasks','/api/approvals?page=1&size=5',
    '/api/positions?page=1&size=5','/api/users?page=1&size=5','/api/roles?page=1&size=5',
    '/api/tenant-page-configs','/api/api-call-logs?page=1&size=5','/api/email-logs?page=1&size=5',
    '/api/dashboard/sales-trend','/api/dashboard/order-funnel','/api/dashboard/top-dealers','/api/dashboard/inventory-summary',
    '/api/reports/sales','/api/reports/inventory','/api/product-mappings?page=1&size=5'
  ];
  for (const p of endpoints) {
    try {
      const x = await apiGet(token, p);
      const body = JSON.stringify(x.json);
      const ok = x.status===200 && x.json?.code===0;
      console.log(`${ok?'OK  ':'FAIL'} ${x.status} ${p} -> ${body.slice(0,150)}`);
    } catch(e) { console.log('ERR ', p, e.message); }
  }
})();
