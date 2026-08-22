const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const config = require(path.join(__dirname, '..', 'config'));

const BASE = config.BASE;
const outDir = path.join(__dirname, '..', '..', 'v4-browser-results', 'rma-amount-lineno-' + Date.now());
fs.mkdirSync(outDir, { recursive: true });
const fail = [];
function ok(name, cond, extra) {
  console.log((cond ? 'PASS ' : 'FAIL ') + name + (extra ? ' ' + extra : ''));
  if (!cond) fail.push(name + (extra ? ' ' + extra : ''));
}
async function loginToken() {
  const r = await fetch(BASE + '/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ tenantCode: config.pc.tenant, username: config.pc.username, password: config.pc.password })
  });
  const j = await r.json();
  if (!j.data || !j.data.accessToken) throw new Error('login failed');
  return j.data.accessToken;
}
let token;
async function api(method, url, body) {
  const r = await fetch(BASE + url, {
    method, headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
    body: body ? JSON.stringify(body) : undefined
  });
  const text = await r.text();
  let data; try { data = text ? JSON.parse(text) : null; } catch { data = { raw: text }; }
  if (r.status >= 400 || (data && data.code && data.code !== 0 && data.code !== 200))
    throw new Error(`${method} ${url} ${r.status}: ${JSON.stringify(data)}`);
  return data.data || data;
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  token = await loginToken();
  const { ctx, page } = await (require(path.join(__dirname, '..', 'helpers/auth'))).loginPC(browser);
  await ctx.addInitScript((t) => localStorage.setItem('dms_access_token', t), token);
  const errors = [];
  page.on('console', m => { if (m.type === 'error') errors.push(m.text()); });
  page.on('pageerror', e => errors.push('PAGE:' + e.message));

  const today = new Date().toISOString().slice(0, 10);
  const createdRmas = [];
  try {
    // Find a completed/partial shipped out that has a gift line (is_gift via source order line)
    // shipped-outs search endpoint returns sales outs with status
    const outs = await api('GET', '/api/sales-returns/shipped-outs');
    const list = Array.isArray(outs) ? outs : (outs.list || outs.records || []);
    ok('shipped-outs-list', Array.isArray(list) && list.length > 0, 'count=' + list.length);

    let verifiedGiftExclusion = false, verifiedLineNo = false, createdSumMatch = false;
    for (const o of list) {
      const det = await api('GET', `/api/sales-returns/shipped-outs/${o.id}/lines`);
      if (!det.lines || !det.lines.length) continue;
      // every returnable line carries a line number
      if (det.lines.every(l => l.lineNo != null)) verifiedLineNo = true;
      // cross-check: raw sales-out detail to see if any line is a gift (final_amount=0 from is_gift order line)
      // The picker must exclude zero-priced gift lines. We detect by hitting an out known to contain gifts:
      // GI-20260822-00002 (id 99) contains gift PRD-J003.
      if (o.code === 'GI-20260822-00002' || o.id === 99) {
        const gift = det.lines.find(l => l.productCode === 'PRD-J003');
        ok('rma-lines-exclude-gift', !gift, gift ? 'gift leaked: ' + gift.sourceOutLineId : 'gift absent');
        verifiedGiftExclusion = true;
      }
    }
    ok('rma-lines-carry-lineno', verifiedLineNo);
    if (!verifiedGiftExclusion) ok('rma-lines-exclude-gift', false, 'seeded out GI-20260822-00002 not found');

    // Create an RMA from one returnable line, then assert header = sum of line finalAmount
    const usable = list.find(o => (o.status === 'COMPLETED' || o.status === 'PARTIAL_SHIPPED'));
    if (usable) {
      const det = await api('GET', `/api/sales-returns/shipped-outs/${usable.id}/lines`);
      const target = det.lines.find(l => Number(l.returnableQty) >= 1 && Number(l.unitPrice) > 0);
      if (target) {
        const rma = await api('POST', '/api/sales-returns', {
          sourceSalesOutId: usable.id, refSalesOutId: usable.id,
          dealerId: det.dealerId, warehouseId: det.warehouseId,
          expectedDate: today, reasonCode: 'NORMAL', reason: '常规退货',
          lines: [{ sourceOutLineId: target.sourceOutLineId, productId: target.productId,
            productCode: target.productCode, productName: target.productName,
            qty: 1, unitPrice: target.unitPrice, taxRate: target.taxRate || 0.13 }]
        });
        createdRmas.push(rma.id);
        const full = await api('GET', `/api/sales-returns/${rma.id}`);
        const sum = full.lines.reduce((s, l) => s + Number(l.finalAmount || 0), 0);
        ok('rma-header-equals-line-sum', Math.abs(sum - Number(full.finalAmount)) < 0.01,
          'header=' + full.finalAmount + ' sum=' + sum.toFixed(2));
        ok('rma-detail-has-lineno', full.lines.every(l => l.lineNo != null),
          full.lines.map(l => l.lineNo + '/' + l.orderLineNo).join(','));
        createdSumMatch = true;
      }
    }
    if (!createdSumMatch) ok('rma-header-equals-line-sum', false, 'no usable returnable line');
  } catch (e) {
    fail.push('EXCEPTION: ' + e.message);
    console.error(e.stack);
  } finally {
    for (const id of createdRmas) {
      await api('POST', `/api/sales-returns/${id}/cancel`, {}).catch(()=>{});
    }
    fs.writeFileSync(path.join(outDir, 'summary.json'), JSON.stringify({ fail, errors }, null, 2));
    await browser.close();
  }
  if (fail.length || errors.length) {
    console.error('\nFAILURES:', fail);
    console.error('CONSOLE ERRORS:', errors);
    process.exit(1);
  }
  console.log('\nALL RMA CHECKS PASSED');
})();
