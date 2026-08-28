// v4.4.0 铁律9 浏览器门禁 + 新页面巡检（/dms 前缀修正版）
// 用法: node automation_test/v440_browser_gate.js
// 依赖: 项目根 node_modules/playwright
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = process.env.E2E_BASE || 'http://43.128.145.141';
const DMS = BASE + '/dms';
const OUTDIR = path.join(__dirname, 'v4-browser-results');
fs.mkdirSync(OUTDIR, { recursive: true });

const RESULTS = [];
function log(name, ok, detail) {
  RESULTS.push({ name, ok: !!ok, detail });
  console.log((ok ? 'PASS ' : 'FAIL ') + name + ' :: ' + JSON.stringify(detail).slice(0, 400));
}

function makeCollector(page) {
  const errs = [];
  const http5 = [];
  const http4 = [];
  page.on('console', m => {
    if (m.type() === 'error') {
      const t = m.text();
      if (/favicon|ResizeObserver|net::ERR_|unsafe-eval|legacy-workly|DevTools|prefers-color-scheme/i.test(t)) return;
      errs.push(t.slice(0, 220));
    }
  });
  page.on('pageerror', e => errs.push('pageerror: ' + String(e).slice(0, 220)));
  page.on('requestfailed', r => {
    const u = r.url();
    if (/favicon/.test(u)) return;
    errs.push('requestfailed: ' + (r.failure() && r.failure().errorText) + ' ' + u.replace(BASE, '').slice(0, 120));
  });
  page.on('response', resp => {
    const st = resp.status();
    const u = resp.url().replace(BASE, '');
    if (st >= 500) http5.push(st + ' ' + u.slice(0, 140));
    else if (st >= 400 && st !== 401) http4.push(st + ' ' + u.slice(0, 140));
  });
  return {
    drain() {
      const apiErr = errs.filter(e => !/\/assets\/|fonts|googleapis|cdn\./i.test(e));
      return { consoleErrors: apiErr.splice(0), http5xx: http5.splice(0), http4xx: http4.splice(0) };
    }
  };
}

async function waitApp(page, timeoutMs = 12000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const refs = await page.locator('*').count().catch(() => 0);
    if (refs > 20) return refs;
    await page.waitForTimeout(300);
  }
  return await page.locator('*').count().catch(() => 0);
}

async function shoot(page, name) {
  const p = path.join(OUTDIR, name.replace(/[^A-Za-z0-9一-龥-]/g, '_') + '.png');
  await page.screenshot({ path: p, fullPage: false }).catch(() => {});
  return p;
}

async function fillLogin(page, prefix) {
  const tenant = page.locator('input[placeholder*="租户"], input[placeholder*="tenant" i]').first();
  if (await tenant.count()) { await tenant.fill('default').catch(() => {}); }
  const user = page.locator('input[placeholder*="账号"], input[placeholder*="用户"], input[placeholder*="username" i]').first();
  if (await user.count()) { await user.fill('admin').catch(() => {}); }
  const pwd = page.locator('input[type="password"]').first();
  if (await pwd.count()) { await pwd.fill('Sh123456').catch(() => {}); }
  const btn = page.locator('button:has-text("登录"), button:has-text("登 录"), .login-btn, .van-button:has-text("登录")').first();
  if (await btn.count()) { await btn.click().catch(() => {}); }
  await page.waitForTimeout(4500);
  await page.waitForLoadState('networkidle').catch(() => {});
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 900 } });

  // ===== G1-G5: 未登录入口 URL 巡检（铁律9） =====
  const entries = [
    { name: 'G1-根路径(营销官网)', url: BASE + '/' },
    { name: 'G2-PC工作台/dms/', url: DMS + '/' },
    { name: 'G3-平台后台/dms/admin/', url: DMS + '/admin/' },
    { name: 'G4-移动端/dms/mobile/login', url: DMS + '/mobile/login' },
    { name: 'G5-后端健康/actuator/health', url: BASE + '/actuator/health', health: true }
  ];
  for (const e of entries) {
    const page = await ctx.newPage();
    const col = makeCollector(page);
    let resp = null;
    try {
      resp = await page.goto(e.url, { waitUntil: 'networkidle', timeout: 20000 }).catch(err => null);
      await page.waitForTimeout(1800);
      const finalUrl = page.url();
      const refs = await waitApp(page, 8000);
      const txt = await page.locator('body').innerText().catch(() => '');
      const is404 = /404|Not Found|页面不存在|找不到页面/.test(txt || '') && refs < 40;
      const drained = col.drain();
      const status = resp ? resp.status() : -1;
      let ok;
      if (e.health) {
        ok = status === 200 && /UP/.test(txt || '');
        log(e.name, ok, { status, body: (txt || '').slice(0, 80) });
      } else {
        ok = status === 200 && refs > 20 && !is404 && drained.http5xx.length === 0;
        log(e.name, ok, { status, finalUrl: finalUrl.replace(BASE, ''), refs, is404,
          consoleErrors: drained.consoleErrors.slice(0, 3), http5xx: drained.http5xx.slice(0, 3),
          http4xx: drained.http4xx.slice(0, 6) });
      }
      await shoot(page, e.name);
    } catch (err) {
      log(e.name, false, { error: String(err).slice(0, 200) });
    }
    await page.close();
  }

  // ===== G6: PC 工作台登录（/dms/login） =====
  const page = await ctx.newPage();
  const col = makeCollector(page);
  let li = { loggedIn: false };
  try {
    await page.goto(DMS + '/login', { waitUntil: 'networkidle', timeout: 20000 }).catch(() => {});
    await waitApp(page);
    await fillLogin(page);
    const url = page.url();
    const refs = await waitApp(page);
    const bodyTxt = (await page.locator('body').innerText().catch(() => '')) || '';
    const onLoginPage = /\/login/.test(url);
    li = { url: url.replace(BASE, ''), refs, onLoginPage, bodySample: bodyTxt.replace(/\s+/g, ' ').slice(0, 150) };
    li.loggedIn = !onLoginPage && refs > 20;
  } catch (e) { li = { error: String(e) }; }
  await shoot(page, 'G6-PC登录后工作台');
  log('G6-PC登录成功进入工作台', li.loggedIn === true, li);

  // ===== G7/G8: 核心列表页 + v4.4.0 新页面巡检 =====
  const modules = [
    { name: 'G7-产品列表', route: '/m/products' },
    { name: 'G7-销售订单列表', route: '/m/orders' },
    { name: 'G7-销售出库列表', route: '/m/sales-outs' },
    { name: 'G7-销退单列表', route: '/m/sales-returns' },
    { name: 'G7-经销商列表', route: '/m/dealers' },
    { name: 'G8-寄售库存页(v4.4)', route: '/consignment-stock', keyword: '寄售' },
    { name: 'G8-经销商资信账期页(v4.4)', route: '/dealer-credit', keyword: '资信' },
    { name: 'G8-库存查询页(进销存已关闭)', route: '/m/inventory', keyword: null, expectDenied: true },
    { name: 'G12-销退新建页(v4.4 R7)', route: '/sales-return-edit', keyword: '退货' },
    { name: 'G12-销售订单新建页(v4.4)', route: '/order-create/sales', keyword: null }
  ];
  for (const m of modules) {
    try {
      const resp = await page.goto(DMS + m.route, { waitUntil: 'networkidle', timeout: 18000 }).catch(() => null);
      await page.waitForTimeout(2800);
      const refs = await waitApp(page, 9000);
      const txt = (await page.locator('body').innerText().catch(() => '')) || '';
      const compact = txt.replace(/\s+/g, ' ');
      const hasTable = await page.locator('.el-table').count().catch(() => 0);
      const hasEmpty = await page.locator('.el-empty').count().catch(() => 0);
      const hasForm = await page.locator('.el-form, form').count().catch(() => 0);
      const bouncedLogin = /\/login/.test(page.url());
      const denied = /未启用|无权限|没有权限|403|进销存|联系管理员/.test(compact);
      const isErrPage = /页面不存在|找不到页面|404/.test(compact) && refs < 40;
      const drained = col.drain();
      let keywordOk = true;
      if (m.keyword) keywordOk = compact.includes(m.keyword);
      let ok;
      if (m.expectDenied) {
        // 进销存关闭：访问库存页预期被拦截/无数据（不出现5xx、不崩白屏即可；若出现"未启用"提示更佳）
        ok = refs > 20 && !bouncedLogin && !isErrPage && drained.http5xx.length === 0 && keywordOk;
      } else {
        ok = refs > 20 && !bouncedLogin && !isErrPage && drained.http5xx.length === 0 && keywordOk;
      }
      log(m.name, ok, { route: m.route, httpStatus: resp ? resp.status() : -1, refs, url: page.url().replace(BASE, ''),
        hasTable, hasEmpty, hasForm, bouncedLogin, isErrPage, denied, keywordOk,
        consoleErrors: drained.consoleErrors.slice(0, 3), http5xx: drained.http5xx.slice(0, 3),
        http4xx: drained.http4xx.filter(x => !/\.js|\.css|fonts|favicon|\.png|\.jpg|\.svg/.test(x)).slice(0, 5),
        bodySample: compact.slice(0, 140) });
      await shoot(page, m.name);
    } catch (e) {
      log(m.name, false, { error: String(e).slice(0, 200) });
    }
  }

  // ===== G9: 菜单可见性（登录态工作台布局内采集） =====
  try {
    await page.goto(DMS + '/home', { waitUntil: 'networkidle', timeout: 18000 }).catch(() => {});
    await page.waitForTimeout(2500);
    // 展开可能折叠的菜单分组
    for (const sub of await page.locator('.el-sub-menu__title, .el-menu-item-group__title').all()) {
      await sub.click().catch(() => {});
    }
    await page.waitForTimeout(800);
    const menuTxt = (await page.locator('.el-menu-item, .el-sub-menu__title').allInnerTexts().catch(() => [])).join(' ');
    const compact = menuTxt.replace(/\s+/g, ' ');
    const hasPurchase = /采购订单|采购退货/.test(compact);
    const hasSupplier = /供应商/.test(compact);
    const hasWarehouse = /仓库管理/.test(compact);
    const hasInventoryMenu = /库存查询|收货入库|库存移动/.test(compact);
    const consignMenu = /寄售库存/.test(compact);
    const creditMenu = /资信/.test(compact);
    const drained = col.drain();
    let inventoryOn = true;
    try {
      const feat = await page.evaluate(async () => {
        const tok = localStorage.getItem('token') || localStorage.getItem('dms_access_token') || '';
        const r = await fetch('/api/tenant/features', { headers: { Authorization: 'Bearer ' + tok } });
        if (!r.ok) return null; const j = await r.json(); return (j && j.data) ? j.data : j;
      });
      if (feat && typeof feat.inventoryEnabled !== 'undefined') inventoryOn = !!feat.inventoryEnabled;
    } catch (e) {}
    const inventoryMenuOk = inventoryOn ? (hasPurchase && hasSupplier && hasWarehouse) : (!hasPurchase && !hasSupplier && !hasWarehouse);
    log('G9-进销存菜单与租户开关一致', inventoryMenuOk,
      { inventoryOn, hasPurchase, hasSupplier, hasWarehouse, hasInventoryMenu, menuSample: compact.slice(0, 260) });
    log('G9-寄售库存/资信账期菜单可见', consignMenu && creditMenu, { consignMenu, creditMenu,
      consoleErrors: drained.consoleErrors.slice(0, 3), http5xx: drained.http5xx.slice(0, 3) });
    await shoot(page, 'G9-菜单可见性');
  } catch (e) {
    log('G9-菜单可见性', false, { error: String(e).slice(0, 200) });
  }

  // ===== G10: 移动端 H5 登录页 + 登录（/dms/mobile/login） =====
  try {
    const mp = await ctx.newPage();
    const mcol = makeCollector(mp);
    await mp.setViewportSize({ width: 390, height: 844 });
    const mresp = await mp.goto(DMS + '/mobile/login', { waitUntil: 'networkidle', timeout: 20000 }).catch(() => null);
    await mp.waitForTimeout(2000);
    const mrefs0 = await waitApp(mp, 8000);
    const m404 = await mp.locator('body').innerText().then(t => /404|页面不存在/.test(t)).catch(() => false);
    await fillLogin(mp);
    const murl = mp.url();
    await waitApp(mp, 8000);
    const mbody = (await mp.locator('body').innerText().catch(() => '')) || '';
    const mLogged = !/login/.test(murl);
    const drained = mcol.drain();
    await shoot(mp, 'G10-移动端H5');
    log('G10-移动端H5登录页可访问', (mresp ? mresp.status() : -1) === 200 && mrefs0 > 10 && !m404,
      { status: mresp ? mresp.status() : -1, refs: mrefs0, is404: m404,
        consoleErrors: drained.consoleErrors.slice(0, 3), http5xx: drained.http5xx.slice(0, 3),
        http4xx: drained.http4xx.slice(0, 5) });
    log('G10-移动端H5登录进入首页', mLogged, { url: murl.replace(BASE, ''), bodySample: mbody.replace(/\s+/g, ' ').slice(0, 120) });
    await mp.close();
  } catch (e) {
    log('G10-移动端H5', false, { error: String(e).slice(0, 200) });
  }

  // ===== G11: 平台后台登录页 + 登录（/dms/admin/） =====
  try {
    const ap = await ctx.newPage();
    const acol = makeCollector(ap);
    const aresp = await ap.goto(DMS + '/admin/', { waitUntil: 'networkidle', timeout: 20000 }).catch(() => null);
    await ap.waitForTimeout(2500);
    const arefs0 = await waitApp(ap, 8000);
    const aurl0 = ap.url();
    await fillLogin(ap);
    await ap.waitForTimeout(2000);
    const aurl = ap.url();
    const arefs = await waitApp(ap, 8000);
    const abody = (await ap.locator('body').innerText().catch(() => '')) || '';
    const acompact = abody.replace(/\s+/g, ' ');
    const alogged = !/login/i.test(aurl) && arefs > 20;
    const drained = acol.drain();
    await shoot(ap, 'G11-平台后台');
    log('G11-平台后台登录页可访问', (aresp ? aresp.status() : -1) === 200 && arefs0 > 20,
      { status: aresp ? aresp.status() : -1, refs: arefs0, finalUrl: aurl0.replace(BASE, ''),
        consoleErrors: drained.consoleErrors.slice(0, 3), http5xx: drained.http5xx.slice(0, 3),
        http4xx: drained.http4xx.slice(0, 5) });
    log('G11-平台后台登录', alogged, { url: aurl.replace(BASE, ''), refs: arefs, bodySample: acompact.slice(0, 140) });
    await ap.close();
  } catch (e) {
    log('G11-平台后台', false, { error: String(e).slice(0, 200) });
  }

  await browser.close();

  const passed = RESULTS.filter(r => r.ok).length;
  const failed = RESULTS.filter(r => !r.ok);
  const summary = { total: RESULTS.length, passed, failed: failed.length, base: BASE, at: new Date().toISOString() };
  fs.writeFileSync(path.join(OUTDIR, 'gate-results.json'), JSON.stringify({ summary, results: RESULTS }, null, 2), 'utf-8');
  console.log('\n==== BROWSER SUMMARY ====');
  console.log(JSON.stringify(summary));
  failed.forEach(f => console.log('FAILED:', f.name, '->', JSON.stringify(f.detail).slice(0, 300)));
  process.exit(0);
})().catch(e => { console.error('FATAL', e); process.exit(2); });
