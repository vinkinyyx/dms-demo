const { record, save, loginAPI, apiGet } = require('./harness');
(async()=>{
  const t = (await loginAPI('sys_admin','Dms@123456')).json.data.accessToken;
  const p = await apiGet(t, '/api/products?page=1&size=1');
  const productId = p.json.data.list[0].id;
  const d = await apiGet(t, '/api/dealers?page=1&size=1');
  const dealerId = d.json.data.list[0].id;
  const h = await apiGet(t, '/api/hospitals?page=1&size=1');
  const hospitalId = h.json.data.list[0].id;
  record('LOOK','pid','取测试用产品ID','INFO',`productId=${productId}, dealerId=${dealerId}, hospitalId=${hospitalId}`);
  const checks = [
    ['DEALER', `/api/dealers/profile?dealerId=${dealerId}`],
    ['INV', `/api/inventory-status/available?productId=${productId}`],
    ['INV', `/api/inventory/available-batches?productId=${productId}`],
    ['INV', `/api/inventory/available-lots?productId=${productId}`],
    ['INV', `/api/inventory/available-serials?productId=${productId}`],
    ['RPT', `/api/reports/dealer-orders?dealerId=${dealerId}`],
    ['RPT', `/api/reports/hospital-surgery?hospitalId=${hospitalId}`],
    ['TRACE', '/api/traceability/by-batch?batchNo=BATCH001'],
    ['TRACE', '/api/traceability/by-serial?serialNo=SN001'],
  ];
  for (const [scope,path] of checks) {
    const r = await apiGet(t, path);
    const ok = r.status===200 && r.json?.code===0;
    record(scope, path, path, ok?'PASS':'FAIL', `HTTP ${r.status} ${r.json?.message||''} data=${Array.isArray(r.json?.data)?'array['+r.json.data.length+']':typeof r.json?.data}`);
  }
  save();
})();
