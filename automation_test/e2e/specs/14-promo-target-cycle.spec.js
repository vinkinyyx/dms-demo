const path = require('path');
const { chromium } = require('playwright');
const config = require(path.join(__dirname, '..', 'config'));
const { loginPC } = require(path.join(__dirname, '..', 'helpers/auth'));

const BASE = config.BASE;
const stamp = Date.now().toString(36).toUpperCase();
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
  if (!j.data || !j.data.accessToken) throw new Error('login failed: ' + JSON.stringify(j));
  return j.data.accessToken;
}

function apiFactory(page, token) {
  return async function api(method, url, body, expectFail) {
    const res = await page.evaluate(async ({ BASE, method, url, body, token }) => {
      const r = await fetch(BASE + url, {
        method,
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
        body: body ? JSON.stringify(body) : undefined
      });
      const text = await r.text();
      let data; try { data = text ? JSON.parse(text) : null; } catch { data = { raw: text }; }
      return { status: r.status, data };
    }, { BASE, method, url, body, token });
    const bizOk = res.status < 400 && (!res.data || res.data.code === 0 || res.data.code === 200 || res.data.code === undefined);
    if (!expectFail && !bizOk) throw new Error(`${method} ${url} ${res.status}: ${JSON.stringify(res.data)}`);
    return res;
  };
}

function detailBody(promoType, targetType, target, extra) {
  const d = { targetType, thresholdQty: 2, ...extra };
  if (targetType === 'SKU') d.targetProductId = target;
  else d.targetProductLineId = target;
  return {
    code: `E2E14_${stamp}_${Math.floor(Math.random() * 10000)}`,
    name: `E2E14 ${promoType} ${targetType}`,
    promoType, priority: 50,
    validFrom: '2026-01-01 00:00:00', validTo: '2026-12-31 23:59:59',
    status: 'draft', rules: [{ seq: 1, ruleDetail: d }]
  };
}

(async () => {
  const browser = await chromium.launch({ headless: config.HEADLESS });
  const token = await loginToken();
  const { ctx, page } = await loginPC(browser);
  await ctx.addInitScript((t) => { localStorage.setItem('dms_access_token', t); }, token);
  const errors = [];
  page.on('console', m => {
    if (m.type() !== 'error') return;
    const t = m.text();
    // 负向校验用例会触发预期内的 400，不计为页面错误
    if (/status of 400/.test(t)) return;
    errors.push(t);
  });
  page.on('pageerror', e => errors.push('PAGE:' + e.message));
  const api = apiFactory(page, token);
  const created = [];
  // 隔离：先停用历史遗留的 E2E14_ 促销，避免重复运行时叠加赠品
  try {
    const existing = await api('GET', '/api/promotions?page=1&size=200');
    const recs = existing.data?.data?.records || existing.data?.data?.list || existing.data?.records || [];
    for (const it of recs) {
      if (String(it.code || '').startsWith('E2E14_') && it.status === 'active') {
        await api('POST', `/api/promotions/${it.id}/deactivate`).catch(() => null);
      }
    }
  } catch (_) {}
  try {
    // ---- R1: 满A减钱(FULL_REDUCTION) 选了SKU后必须能保存（历史 bug：隐藏的赠品列仍被必填校验）----
    let r = await api('POST', '/api/promotions', detailBody('FULL_REDUCTION', 'SKU', 1, { reduceAmount: 500 }));
    ok('R1 save FULL_REDUCTION/SKU succeeds', r.status === 200 && r.data?.data?.id, r.data?.message);
    const fullRedId = r.data.data.id; created.push(fullRedId);
    let g = await api('GET', `/api/promotions/${fullRedId}`);
    const frRule = g.data.data.rules[0].ruleDetail;
    ok('R1 FULL_REDUCTION persists target without gift field',
      Number(frRule.targetProductId) === 1 && Number(frRule.reduceAmount) === 500 && !frRule.giftProductId,
      JSON.stringify(frRule));

    // 满A减钱 选择产品层次(LINE) 也能保存（R3：支持产品层次命中）
    r = await api('POST', '/api/promotions', detailBody('FULL_REDUCTION', 'LINE', 1, { reduceAmount: 300 }));
    ok('R3 save FULL_REDUCTION/LINE succeeds', r.status === 200 && r.data?.data?.id, r.data?.message);
    created.push(r.data.data.id);

    // ---- R2: GIFT 支持 cycle ONCE / EVERY_N；EVERY_N 需要 everyN ----
    r = await api('POST', '/api/promotions', detailBody('GIFT', 'LINE', 1, { giftProductId: 3, giftQty: 1, cycle: 'EVERY_N', everyN: 3 }));
    ok('R2 save GIFT/LINE/EVERY_N succeeds', r.status === 200 && r.data?.data?.id, r.data?.message);
    const giftEveryNId = r.data.data.id; created.push(giftEveryNId);
    g = await api('GET', `/api/promotions/${giftEveryNId}`);
    const geRule = g.data.data.rules[0].ruleDetail;
    ok('R2 GIFT/LINE/EVERY_N persists',
      Number(geRule.targetProductLineId) === 1 && Number(geRule.everyN) === 3 && Number(geRule.giftProductId) === 3,
      JSON.stringify(geRule));

    r = await api('POST', '/api/promotions', detailBody('GIFT', 'SKU', 1, { giftProductId: 3, giftQty: 1, cycle: 'ONCE' }));
    ok('R2 save GIFT/SKU/ONCE succeeds', r.status === 200 && r.data?.data?.id, r.data?.message);
    const giftOnceId = r.data.data.id; created.push(giftOnceId);

    // ---- 负向校验 ----
    r = await api('POST', '/api/promotions', detailBody('GIFT', 'SKU', 1, { cycle: 'ONCE' }), true);
    ok('negative: GIFT without gift rejected', r.status >= 400 || (r.data?.data && r.data.code !== 0), r.status);
    r = await api('POST', '/api/promotions', detailBody('GIFT', 'SKU', 1, { giftProductId: 3, giftQty: 1, cycle: 'EVERY_N' }), true);
    ok('negative: EVERY_N without everyN rejected', r.status >= 400 || (r.data?.data && r.data.code !== 0), r.status);
    r = await api('POST', '/api/promotions', detailBody('FULL_REDUCTION', 'LINE', 1, {}), true);
    ok('negative: FULL_REDUCTION without amount rejected', r.status >= 400 || (r.data?.data && r.data.code !== 0), r.status);

    // ---- 计价端到端 ----
    // FULL_REDUCTION/SKU: product1(2800) qty2 => 5600-500=5100
    await api('POST', `/api/promotions/${fullRedId}/activate`);
    let p = await api('POST', '/api/sales-orders/preview', { dealerId: 0, applyPromotions: true, lines: [{ productId: 1, qty: 2 }] });
    let paid = p.data.data.lines.filter(l => !l.gift && l.lineLevel !== 'PARENT');
    let final = paid.reduce((s, l) => s + Number(l.finalAmount || 0), 0);
    ok('pricing FULL_RED/SKU final=5100', Math.abs(final - 5100) < 0.05, 'final=' + final);
    ok('pricing message mentions reduction', (p.data.data.promotionMessages || []).some(m => /减免/.test(m)), JSON.stringify(p.data.data.promotionMessages));

    // GIFT/LINE/EVERY_N: 创伤线 product1 qty8 => threshold2 everyN3 => 1+floor((8-2)/3)=3 件 product3
    await api('POST', `/api/promotions/${fullRedId}/deactivate`);
    await api('POST', `/api/promotions/${giftEveryNId}/activate`);
    p = await api('POST', '/api/sales-orders/preview', { dealerId: 0, applyPromotions: true, lines: [{ productId: 1, qty: 8 }] });
    let gifts = p.data.data.lines.filter(l => l.gift);
    ok('pricing GIFT/LINE/EVERY_N gives 3 of product3',
      gifts.length === 1 && Number(gifts[0].productId) === 3 && Number(gifts[0].qty) === 3,
      JSON.stringify(gifts.map(x => [x.productId, x.qty])));
    ok('pricing gift line amount=0 and not editable flag', gifts.every(x => Number(x.finalAmount) === 0 && x.gift === true));

    // LINE 隔离：脊柱 product7 qty8 不应触发创伤线赠品
    p = await api('POST', '/api/sales-orders/preview', { dealerId: 0, applyPromotions: true, lines: [{ productId: 7, qty: 8 }] });
    gifts = p.data.data.lines.filter(l => l.gift);
    ok('pricing LINE scope: spine product no gift', gifts.length === 0, 'gifts=' + gifts.length);

    // ONCE + EVERY_N 叠加：product1 qty8 => 1 + 3 = 4 件赠品
    await api('POST', `/api/promotions/${giftOnceId}/activate`);
    p = await api('POST', '/api/sales-orders/preview', { dealerId: 0, applyPromotions: true, lines: [{ productId: 1, qty: 8 }] });
    gifts = p.data.data.lines.filter(l => l.gift);
    const totalGift = gifts.reduce((s, g) => s + Number(g.qty || 0), 0);
    ok('pricing ONCE(1)+EVERY_N(3)=4 gifts', totalGift === 4, 'total=' + totalGift);

    // 低于 ONCE 门槛(qty1) 不赠
    await api('POST', `/api/promotions/${giftEveryNId}/deactivate`);
    p = await api('POST', '/api/sales-orders/preview', { dealerId: 0, applyPromotions: true, lines: [{ productId: 1, qty: 1 }] });
    gifts = p.data.data.lines.filter(l => l.gift);
    ok('pricing ONCE below threshold no gift', gifts.length === 0, 'gifts=' + gifts.length);

    // ---- 浏览器实点：促销规则列表/新建弹窗能打开，无 console 红错 ----
    await page.goto(BASE + '/m/promotions', { waitUntil: 'networkidle', timeout: 25000 });
    await page.waitForTimeout(1500);
    const hasTable = await page.locator('table, .el-table').first().count();
    ok('browser promotions page renders table', hasTable > 0);
    const newBtn = page.locator('button:has-text("新增"), button:has-text("新建")').first();
    await newBtn.click().catch(() => {});
    await page.waitForTimeout(1200);
    const dialogOpen = await page.locator('.el-dialog, .el-drawer').first().count();
    ok('browser promotions new dialog opens', dialogOpen > 0);
    await page.keyboard.press('Escape');
    await page.waitForTimeout(500);
    ok('browser no console red errors', errors.length === 0, errors.slice(0, 3).join(' | '));
  } finally {
    for (const id of created) {
      await api('POST', `/api/promotions/${id}/deactivate`).catch(() => null);
    }
    await browser.close();
  }

  console.log('\n' + (fail.length ? `FAILED ${fail.length}: ` + fail.join('; ') : 'ALL PROMO E2E PASS'));
  process.exit(fail.length ? 1 : 0);
})().catch(e => { console.error('SPEC ERROR', e); process.exit(1); });
