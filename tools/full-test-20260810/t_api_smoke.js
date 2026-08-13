const { record, save, loginAPI, apiGet } = require('./harness');

const ENDPOINTS = [
  // auth / me
  ['AUTH','/api/auth/me'],
  ['PERM','/api/me/permissions'],
  ['MENU','/api/menus'],
  ['MENU','/api/menu-configs'],
  // home/dashboard
  ['HOME','/api/home/dashboard'],
  ['HOME','/api/dashboard/kpi'],
  ['HOME','/api/dashboard/overview'],
  ['HOME','/api/dashboard/summary'],
  ['HOME','/api/dashboard/todos'],
  ['HOME','/api/dashboard/sales-trend'],
  ['HOME','/api/dashboard/order-funnel'],
  ['HOME','/api/dashboard/top-dealers'],
  ['HOME','/api/dashboard/top-hospitals'],
  ['HOME','/api/dashboard/inventory-stats'],
  ['HOME','/api/dashboard/inventory-pie'],
  ['HOME','/api/dashboard/activity-7d'],
  // basic data
  ['PROD','/api/products?page=1&size=5'],
  ['PROD','/api/product-categories/tree'],
  ['PROD','/api/product-categories?page=1&size=5'],
  ['PROD','/api/product-lines?page=1&size=5'],
  ['PROD','/api/product-package-levels?page=1&size=5'],
  ['PROD','/api/product-bundles?page=1&size=5'],
  ['PROD','/api/product-prices?page=1&size=5'],
  ['PROD','/api/product-mappings?page=1&size=5'],
  ['DEALER','/api/dealers?page=1&size=5'],
  ['DEALER','/api/dealers/profile?page=1&size=5'],
  ['HOSP','/api/hospitals?page=1&size=5'],
  ['WH','/api/warehouses?page=1&size=5'],
  ['REGION','/api/regions/tree'],
  ['REGION','/api/regions?page=1&size=5'],
  ['SUP','/api/suppliers?page=1&size=5'],
  // contracts
  ['CT','/api/contracts?page=1&size=5'],
  ['CT','/api/contract-templates?page=1&size=5'],
  ['AUTHZ','/api/authorizations?page=1&size=5'],
  ['AUTHZ','/api/rma-authorizations?page=1&size=5'],
  // orders
  ['SO','/api/orders?page=1&size=5'],
  ['SO','/api/sales-orders?page=1&size=5'],
  ['SR','/api/sales-returns?page=1&size=5'],
  ['PO','/api/purchase-orders?page=1&size=5'],
  ['PR','/api/purchase-returns?page=1&size=5'],
  ['SO','/api/orders/actions-for-status?status=DRAFT'],
  ['SO','/api/orders/export-tasks?page=1&size=5'],
  // inventory
  ['INV','/api/inventory?page=1&size=5'],
  ['INV','/api/inventory-status/available'],
  ['INV','/api/inventory/available-batches'],
  ['INV','/api/inventory/available-lots'],
  ['INV','/api/inventory/available-serials'],
  ['INV','/api/inventory-adjustments?page=1&size=5'],
  ['INV','/api/stock-moves?page=1&size=5'],
  ['INV','/api/stocktakes?page=1&size=5'],
  ['INV','/api/receipts?page=1&size=5'],
  ['INV','/api/sales-outs?page=1&size=5'],
  // surgery/marketing
  ['SURG','/api/surgery-reports?page=1&size=5'],
  ['PROMO','/api/promotions?page=1&size=5'],
  ['MISC','/api/loans?page=1&size=5'],
  // approvals
  ['APPR','/api/approval?page=1&size=5'],
  ['APPR','/api/approval/tasks/my-done?page=1&size=5'],
  ['APPR','/api/approval/instances/my-submitted?page=1&size=5'],
  ['APPR','/api/approval/cc/my?page=1&size=5'],
  ['APPR','/api/approval/templates?page=1&size=5'],
  ['APPR','/api/approval/delegations?page=1&size=5'],
  ['APPR','/api/approval/admin/instances?page=1&size=5'],
  // users/permissions
  ['USER','/api/users?page=1&size=5'],
  ['USER','/api/roles'],
  ['USER','/api/sales-positions/tree'],
  ['USER','/api/sales-positions/my-scope'],
  ['USER','/api/sales-positions/candidate-users'],
  ['USER','/api/sales-org/tree'],
  ['USER','/api/sales-org/my-dealers'],
  ['USER','/api/tenants?page=1&size=5'],
  ['USER','/api/my-dealer-tenants'],
  // configs/logs
  ['CFG','/api/dicts/types?page=1&size=5'],
  ['CFG','/api/form-configs/forms'],
  ['CFG','/api/system/settings'],
  ['CFG','/api/system/stats'],
  ['LOG','/api/api-call-logs?page=1&size=5'],
  ['LOG','/api/email-logs?page=1&size=5'],
  ['LOG','/api/operation-logs?page=1&size=5'],
  ['LOG','/api/system/audit-logs?page=1&size=5'],
  ['LOG','/api/system/login-logs?page=1&size=5'],
  ['LOG','/api/notifications?page=1&size=5'],
  ['LOG','/api/system/notifications?page=1&size=5'],
  ['SYS','/api/system-ops/cache/status'],
  ['SYS','/api/system-ops/my-data-scope'],
  ['SYS','/api/system-ops/rbac/matrix'],
  ['SYS','/api/system-ops/workflows?page=1&size=5'],
  ['SYS','/api/system-ops/seed-status'],
  // reports
  ['RPT','/api/reports?page=1&size=5'],
  ['RPT','/api/reports/overview'],
  ['RPT','/api/reports/sales-ranking'],
  ['RPT','/api/reports/product-top10'],
  ['RPT','/api/reports/product-sales-detail'],
  ['RPT','/api/reports/dealer-orders'],
  ['RPT','/api/reports/hospital-surgery'],
  ['RPT','/api/reports/surgery-stats'],
  ['RPT','/api/reports/inventory-aging'],
  ['RPT','/api/reports/inventory-turnover'],
  ['RPT','/api/reports/order-approval-stats'],
  ['RPT','/api/reports/order-trace'],
  ['RPT','/api/reports/receivables'],
  // invoices
  ['FIN','/api/sales-invoices?page=1&size=5'],
  ['FIN','/api/purchase-invoices?page=1&size=5'],
  ['RMA','/api/rma-orders?page=1&size=5'],
  // traceability
  ['TRACE','/api/traceability/by-batch?batch=TEST'],
  // lookups
  ['LOOK','/api/lookups/products'],
  ['LOOK','/api/lookups/dealers'],
  ['LOOK','/api/lookups/hospitals'],
  ['LOOK','/api/lookups/warehouses'],
  ['LOOK','/api/lookups/regions'],
  ['LOOK','/api/lookups/categories'],
  ['LOOK','/api/lookups/suppliers'],
  ['LOOK','/api/lookups/orders'],
  ['LOOK','/api/lookups/contracts'],
  ['LOOK','/api/lookups/org-units'],
];

(async () => {
  const login = await loginAPI('sys_admin','Dms@123456');
  const token = login.json.data.accessToken;
  for (const [scope, p] of ENDPOINTS) {
    try {
      const r = await apiGet(token, p);
      const ok = r.status === 200 && r.json?.code === 0;
      const dataInfo = r.json?.data ? (Array.isArray(r.json.data) ? `array[${r.json.data.length}]` : (r.json.data.total!==undefined?`total=${r.json.data.total} page=${r.json.data.page}`:'object')) : 'null';
      record(scope, p, p, ok ? 'PASS' : 'FAIL', `HTTP ${r.status} code=${r.json?.code} ${r.json?.message||''} data=${dataInfo}`);
    } catch(e) {
      record(scope, p, p, 'FAIL', `Exception ${e.message}`);
    }
  }
  const sum = save();
  console.log('\n=== SUMMARY ==='); console.log(JSON.stringify(sum,null,2));
})();
