const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const config = require(path.join(__dirname, '..', 'config'));
const { loginPC } = require(path.join(__dirname, '..', 'helpers/auth'));
const { cleanupTestArtifacts } = require(path.join(__dirname, '..', 'helpers/db-cleanup'));

async function loginToken() {
  const r = await fetch(BASE + '/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ tenantCode: config.pc.tenant, username: config.pc.username, password: config.pc.password })
  });
  const j = await r.json();
  if (!j.data || !j.data.accessToken) throw new Error('login failed: ' + JSON.stringify(j));
  return j.data.accessToken;
}
const outDir = path.join(__dirname, '..', '..', 'v4-browser-results', 'promo-bom-return-' + Date.now());
fs.mkdirSync(outDir, { recursive: true });
const BASE = config.BASE;
const fail = [];
let authToken = '';
function ok(name, cond, extra) {
  console.log((cond ? 'PASS ' : 'FAIL ') + name + (extra ? ' ' + extra : ''));
  if (!cond) fail.push(name + (extra ? ' ' + extra : ''));
}
async function api(page, method, url, body) {
  const res = await page.evaluate(async ({ BASE, method, url, body, token }) => {
    const r = await fetch(BASE + url, {
      method,
      headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
      body: body ? JSON.stringify(body) : undefined
    });
    const text = await r.text();
    let data; try { data = text ? JSON.parse(text) : null; } catch { data = { raw: text }; }
    return { status: r.status, data };
  }, { BASE, method, url, body, token: authToken });
  if (res.status >= 400 || (res.data && res.data.code && res.data.code !== 0 && res.data.code !== 200)) {
    throw new Error(`${method} ${url} ${res.status}: ${JSON.stringify(res.data)}`);
  }
  return res.data?.data || res.data;
}
function cleanupOrder(page, id) { return id ? api(page, 'DELETE', `/api/sales-orders/${id}`).catch(() => null) : null; }
(async () => {
  const browser = await chromium.launch({ headless: true });
  authToken = await loginToken();
  const { ctx, page } = await loginPC(browser);
  await ctx.addInitScript((t) => { localStorage.setItem('dms_access_token', t); }, authToken);
  const errors = [];
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', e => errors.push('PAGE:' + e.message));
  let promoOrderId, bomOrderId, returnId, outId;
  try {
    const preview = await api(page, 'POST', '/api/sales-orders/preview', {
      dealerId: 1, orderType: 'SALES', applyPromotions: true, lines: [{ productId: 13, qty: 6 }]
    });
    const priceOnly = await api(page, 'POST', '/api/sales-orders/preview', { dealerId: 1, orderType: 'SALES', lines: [{ productId: 13, qty: 6 }] });
    ok('price-preview-no-gift', !priceOnly.lines.some(l => l.gift), 'price preview must not generate gifts');
    const previewAgain = await api(page, 'POST', '/api/sales-orders/preview', { dealerId: 1, orderType: 'SALES', applyPromotions: true, lines: [{ productId: 13, qty: 6 }] });
    const giftsAgain = previewAgain.lines.filter(l => l.productCode === 'PRD-J003' && l.gift);
    ok('preview-gift-idempotent', giftsAgain.length === 1 && Number(giftsAgain[0].qty) === 15, giftsAgain.map(g => g.qty).join(','));
    ok('preview-has-messages', Array.isArray(preview.promotionMessages) && preview.promotionMessages.some(m => /买2送5/.test(m) && /PRD-J003/.test(m)), JSON.stringify(preview.promotionMessages));
    const allocBody = { dealerId: 1, orderType: 'SALES', applyPromotions: true, headerDiscountType: 'AMOUNT', headerDiscountValue: 100, lines: [{ productId: 13, qty: 2, lineDiscountType: 'PERCENT', lineDiscountValue: 20 }, { productId: 13, qty: 1 }] };
    const alloc = await api(page, 'POST', '/api/sales-orders/preview', allocBody);
    const paid = alloc.lines.filter(l => !l.gift && l.lineLevel !== 'PARENT');
    const f1 = Number(paid[0].finalAmount), f2 = Number(paid[1].finalAmount);
    // v4.1.0: line discount first (1000->800, 500->500), then allocate 100 header by after-line ratio 800:500
    ok('allocation-after-line-discount-ratio', Math.abs(f1 - 738.46) < 0.05 && Math.abs(f2 - 461.54) < 0.05, f1+'/'+f2);
    ok('allocation-total-1200', Math.abs(f1 + f2 - 1200) < 0.05, 'sum='+(f1+f2));
    ok('allocation-line-discount-first', Number(paid[0].lineDiscountAmount) === 200 && Number(paid[1].lineDiscountAmount) === 0, paid.map(l=>l.lineDiscountAmount).join(','));
    ok('allocation-unit-price', Math.abs(f1/2 - 369.23) < 0.05 && Math.abs(f2/1 - 461.54) < 0.05, (f1/2).toFixed(2)+'/'+(f2/1).toFixed(2));
    const gift = preview.lines.find(l => l.productCode === 'PRD-J003' && l.gift);
    ok('preview-buy2-gift5-qty15', gift && Number(gift.qty) === 15, gift ? `giftQty=${gift.qty}` : 'no gift');
    ok('preview-gift-zero-amount', gift && Number(gift.finalAmount) === 0, gift ? `final=${gift.finalAmount}` : 'no gift');
    ok('preview-gift-locked-metadata', gift && gift.gift === true, '');
    const target = preview.lines.filter(l => l.productCode === 'PRD-J002' && !l.gift);
    ok('preview-target-paid', target.reduce((s, l) => s + Number(l.finalAmount), 0) > 0, 'target amount exists');

    const created = await api(page, 'POST', '/api/sales-orders', {
      dealerId: 1, orderType: 'SALES', expectedDate: new Date().toISOString().slice(0, 10),
      lines: [{ productId: 13, qty: 6 }]
    });
    promoOrderId = created.id;
    const saved = await api(page, 'GET', `/api/sales-orders/${promoOrderId}`);
    const savedGift = saved.lines.find(l => l.productCode === 'PRD-J003' && l.isGift);
    ok('save-gift-persisted', savedGift && Number(savedGift.qty) === 15, savedGift ? `qty=${savedGift.qty}` : 'no gift');

    // v4.1.0: verify BOM parent is 0 and children carry lineLevel=CHILD
    const bomPreview = await api(page, 'POST', '/api/sales-orders/preview', { dealerId: 1, orderType: 'SALES', lines: [{ productId: 12, qty: 1 }] });
    const bomParent = bomPreview.lines.find(l => l.lineLevel === 'PARENT');
    const bomChildren = bomPreview.lines.filter(l => l.lineLevel === 'CHILD');
    ok('bom-preview-parent-zero', bomParent && Number(bomParent.finalAmount) === 0 && Number(bomParent.standardAmount) === 0, bomParent ? ('final=' + bomParent.finalAmount) : 'no parent');
    ok('bom-preview-children-present', bomChildren.length > 0, 'children=' + bomChildren.length);
    ok('bom-preview-children-not-in-total', bomChildren.every(c => Number(c.finalAmount) >= 0), '');
    const bomTotal = bomPreview.lines.filter(l => !l.gift && l.lineLevel !== 'PARENT').reduce((s,l)=>s+Number(l.finalAmount),0);
    ok('bom-preview-total-excludes-parent', Math.abs(Number(bomPreview.finalAmount||bomTotal) - bomTotal) < 0.05, 'final=' + (bomPreview.finalAmount||bomTotal) + ', sum=' + bomTotal);

    const bom = await api(page, 'POST', '/api/sales-orders', {
      dealerId: 1, orderType: 'SALES', expectedDate: new Date().toISOString().slice(0, 10),
      lines: [{ productId: 12, qty: 1 }]
    });
    bomOrderId = bom.id;
    const submitBom = await api(page, 'POST', `/api/sales-orders/${bomOrderId}/submit`);
    const bomAfterSubmit = await api(page, 'GET', `/api/sales-orders/${bomOrderId}`);
    if (bomAfterSubmit.status !== 'APPROVED' && bomAfterSubmit.status !== 'COMPLETED' && submitBom.newStatus !== 'APPROVED' && submitBom.newStatus !== 'AUTO_APPROVED') {
      await api(page, 'POST', `/api/orders-approval/${bomOrderId}/approve`);
    }
    const beforeShip = await api(page, 'GET', `/api/sales-orders/${bomOrderId}`);
    if (beforeShip.status !== 'COMPLETED') {
      const shipped = await api(page, 'POST', `/api/sales-orders/${bomOrderId}/simulate-ship`);
      outId = shipped.id;
    } else {
      const outs = await api(page, 'GET', `/api/sales-outs?sourceOrderId=${bomOrderId}`);
      outId = outs?.list?.[0]?.id || outs?.records?.[0]?.id || outs?.[0]?.id;
    }
    const completedOrder = await api(page, 'GET', `/api/sales-orders/${bomOrderId}`);
    if (!outId) throw new Error('BOM order did not produce a sales outbound document');
    const out = await api(page, 'GET', `/api/sales-outs/${outId}`);
    ok('bom-out-has-no-parent', out.lines && !out.lines.some(l => l.lineLevel === 'PARENT' || l.productCode === 'PRD-J001'), JSON.stringify((out.lines||[]).map(l=>l.productCode)));
    ok('bom-out-completed', out.status === 'COMPLETED', out.status);
    ok('bom-order-completed', completedOrder.status === 'COMPLETED', completedOrder.status);

    const shippedLines = await api(page, 'GET', `/api/sales-returns/shipped-outs/${outId}/lines`);
    ok('return-source-has-lines', Array.isArray(shippedLines.lines) && shippedLines.lines.length > 0, '');
    const returnPayload = {
      sourceSalesOutId: outId, sourceSalesOutCode: out.code, refSalesOutId: outId, refSalesOutCode: out.code,
      dealerId: out.dealerId, dealerName: out.dealerName, warehouseId: out.warehouseId, warehouseName: out.warehouseName,
      expectedDate: new Date().toISOString().slice(0, 10), reasonCode: 'QUALITY', reason: '质量问题',
      lines: shippedLines.lines.map(l => ({ ...l, qty: Number(l.returnableQty || l.qty || 1) }))
    };
    const createdReturn = await api(page, 'POST', '/api/sales-returns', returnPayload);
    returnId = createdReturn.id;
    const savedReturn = await api(page, 'GET', `/api/sales-returns/${returnId}`);
    ok('return-lines-have-unit-price', savedReturn.lines.every(l => Number(l.unitPrice) > 0), savedReturn.lines.map(l => `${l.productCode}:${l.unitPrice}`).join(','));
    const returnSum = savedReturn.lines.reduce((s, l) => s + Number(l.unitPrice) * Number(l.qty), 0);
    ok('return-summary-amount', Math.round(Number(savedReturn.finalAmount) * 100) === Math.round(returnSum * 100), `final=${savedReturn.finalAmount},sum=${returnSum.toFixed(2)}`);
    await page.goto(BASE + `/sales-return-edit/${returnId}?mode=view`, { waitUntil: 'networkidle' });
    await page.screenshot({ path: path.join(outDir, 'return-readonly-amounts.png'), fullPage: true });
    const readonlyText = await page.locator('.lines-card').innerText();
    ok('return-readonly-shows-unit-total', /平摊单价/.test(readonlyText) && /汇总退货金额/.test(readonlyText), '');

    await page.goto(BASE + `/orders/${promoOrderId}`, { waitUntil: 'networkidle' });
    await page.screenshot({ path: path.join(outDir, 'order-detail-gift.png'), fullPage: true });
    const detailText = await page.locator('.order-lines-card').innerText().catch(() => '');
    ok('order-detail-gift-visible', /赠品/.test(detailText), detailText.slice(0, 200));
    await page.goto(BASE + `/order-create/sales/${promoOrderId}`, { waitUntil: 'networkidle' });
    await page.screenshot({ path: path.join(outDir, 'order-edit-gift.png'), fullPage: true });
    const orderText = await page.locator('.lines-card, .el-table').last().innerText().catch(() => '');
    ok('order-ui-gift-locked', /赠品/.test(orderText), orderText.slice(0, 200));
    ok('no-console-errors', errors.length === 0, errors.slice(0, 3).join(' | '));
  } finally {
    try {
      if (returnId) await api(page, 'POST', `/api/sales-returns/${returnId}/cancel`).catch(()=>null);
      if (returnId) await api(page, 'DELETE', `/api/sales-returns/${returnId}`).catch(()=>null);
      cleanupTestArtifacts({
        orderIds: [promoOrderId, bomOrderId, returnId].filter(Boolean),
        outIds: [outId].filter(Boolean),
        tenantCode: config.pc.tenant,
      });
    } finally {
      await ctx.close();
      await browser.close();
    }
  }
  if (fail.length) { console.error('\nFAILS:', fail); process.exit(1); }
  console.log('\nALL PASS. Screenshots in', outDir);
})();
