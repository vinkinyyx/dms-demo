const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const config = require(path.join(__dirname, '..', 'config'));
const { loginPC } = require(path.join(__dirname, '..', 'helpers/auth'));

const BASE = config.BASE;
const outDir = path.join(__dirname, '..', '..', 'v4-browser-results', 'outbound-batch-rma-cache-' + Date.now());
fs.mkdirSync(outDir, { recursive: true });
const fail = [];
function ok(name, cond, extra) {
  console.log((cond ? 'PASS ' : 'FAIL ') + name + (extra ? '  ' + extra : ''));
  if (!cond) fail.push(name + (extra ? '  ' + extra : ''));
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

(async () => {
  const browser = await chromium.launch({ headless: config.HEADLESS });
  const token = await loginToken();
  const { ctx, page } = await loginPC(browser);
  await ctx.addInitScript((t) => localStorage.setItem('dms_access_token', t), token);

  async function api(method, url, body, expectFail) {
    const res = await page.evaluate(async ({ BASE, method, url, body, token }) => {
      const r = await fetch(BASE + url, {
        method, headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
        body: body ? JSON.stringify(body) : undefined
      });
      const text = await r.text();
      let data; try { data = text ? JSON.parse(text) : null; } catch { data = { raw: text }; }
      return { status: r.status, data };
    }, { BASE, method, url, body, token });
    const bizOk = res.status < 400 && (!res.data || res.data.code === 0 || res.data.code === 200 || res.data.code === undefined);
    if (!expectFail && !bizOk) throw new Error(`${method} ${url} ${res.status}: ${JSON.stringify(res.data)}`);
    return res;
  }

  const createdOrders = [];
  const createdRmas = [];
  const consoleErrors = [];
  page.on('console', m => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  page.on('pageerror', e => consoleErrors.push('PAGE:' + e.message));
  const leaked = [];
  let watchLeak = false;
  page.on('request', req => { if (!watchLeak) return; const u = req.url(); if (/\/api\/sales-returns\/\d+/.test(u) && u.includes('/sales-returns/')) leaked.push(u); });

  try {
    // R1: order -> submit(auto approve) -> simulate-ship -> out doc must have CONFIRMED batch with line no/batch/price
    const preview = await api('POST', '/api/sales-orders/preview', { dealerId: 1, orderType: 'SALES', applyPromotions: false, lines: [{ productId: 23, qty: 2 }] });
    const previewData = preview.data.data || preview.data;
    const order = await api('POST', '/api/sales-orders', { dealerId: 1, orderType: 'SALES', expectedDate: '2026-08-30',
      lines: previewData.lines.map(l => ({ productId: l.productId, qty: Number(l.qty), lineLevel: l.lineLevel, unitPrice: l.unitPrice })) });
    const orderData = order.data.data || order.data;
    createdOrders.push(orderData.id);
    await api('POST', `/api/sales-orders/${orderData.id}/submit`);
    const shipped = await api('POST', `/api/sales-orders/${orderData.id}/simulate-ship`);
    const shippedData = shipped.data.data || shipped.data;
    const det = await api('GET', `/api/sales-outs/${shippedData.id}`);
    const outData = det.data.data || det.data;
    const batch = (outData.batches || [])[0];
    ok('out-has-confirmed-batch', !!batch && batch.status === 'CONFIRMED', batch ? batch.code : 'no batch');
    ok('batch-lines-have-lineno', !!batch && batch.lines.length > 0 && batch.lines.every(l => l.shipLineNo != null && l.expectedLineSeq != null));
    ok('batch-lines-have-batch-price', !!batch && batch.lines.every(l => l.batchNo && Number(l.unitPrice) > 0));
    ok('out-lines-seq-increment', (outData.lines || []).every((l, i) => Number(l.seq) === i + 1),
      (outData.lines || []).map(l => l.seq).join(','));

    // R2/R3 browser flow: open an existing RMA edit page, then click 新建, must be blank; navigate away, no leak
    const pick = await api('GET', '/api/sales-returns/shipped-outs');
    const pickData = pick.data.data || pick.data;
    const list = Array.isArray(pickData) ? pickData : (pickData.list || pickData.records || []);
    if (list.length) {
      const lines = await api('GET', `/api/sales-returns/shipped-outs/${list[0].id}/lines`);
      const linesData = lines.data.data || lines.data;
      const target = (linesData.lines || []).find(l => Number(l.returnableQty) >= 1 && Number(l.unitPrice) > 0);
      if (target) {
        const rma = await api('POST', '/api/sales-returns', {
          sourceSalesOutId: list[0].id, refSalesOutId: list[0].id,
          dealerId: linesData.dealerId, warehouseId: linesData.warehouseId,
          expectedDate: new Date().toISOString().slice(0, 10), reasonCode: 'NORMAL', reason: 'e2e',
          lines: [{ sourceOutLineId: target.sourceOutLineId, productId: target.productId,
            productCode: target.productCode, productName: target.productName,
            qty: 1, unitPrice: target.unitPrice, taxRate: target.taxRate || 0.13 }]
        });
        const rmaData = rma.data.data || rma.data;
        createdRmas.push(rmaData.id);
        await page.goto(BASE + '/sales-return-edit/' + rmaData.id, { waitUntil: 'networkidle' });
        await page.waitForTimeout(800);
        const beforeDealer = await page.locator('.source-info').first().textContent().catch(() => '');
        // click 新建 (left menu or button) - navigate directly to new route, component must reset
        await page.goto(BASE + '/sales-return-edit', { waitUntil: 'networkidle' });
        await page.waitForTimeout(800);
        const sourceVisible = await page.locator('.source-info').count();
        ok('rma-new-blanks-source', sourceVisible === 0, 'source blocks=' + sourceVisible);
        // navigate away to promotions (R3 leak check); only count requests after leaving
        watchLeak = true;
        leaked.length = 0;
        await page.goto(BASE + '/promotions', { waitUntil: 'networkidle' });
        await page.waitForTimeout(1500);
        ok('rma-no-leak-after-leave', !leaked.some(u => u.endsWith('/' + rmaData.id)), leaked.filter(u => u.endsWith('/' + rmaData.id)).join(','));
      } else {
        ok('rma-flow-target-found', false, 'no returnable priced line');
      }
    } else {
      ok('rma-flow-shippedouts-found', false, 'no shipped outs');
    }

    ok('no-console-errors', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '));
  } catch (e) {
    fail.push('EXCEPTION: ' + e.message);
    console.error(e.stack);
  } finally {
    for (const id of createdRmas) { await api('POST', `/api/sales-returns/${id}/cancel`, {}, true).catch(() => {}); }
    fs.writeFileSync(path.join(outDir, 'summary.json'), JSON.stringify({ fail, consoleErrors }, null, 2));
    await page.screenshot({ path: path.join(outDir, 'final.png'), fullPage: true }).catch(() => {});
    await browser.close();
  }
  if (fail.length || consoleErrors.length) {
    console.error('\nFAILURES:', fail);
    console.error('CONSOLE ERRORS:', consoleErrors);
    process.exit(1);
  }
  console.log('\nALL OUTBOUND/RMA CACHE CHECKS PASSED');
})();
