// v4.4.0 浏览器深度交互验证（铁律9补充）：菜单可见性 / 400报错溯源 / 订单类型门禁 / 销退货源门禁
// 用法: node automation_test/v440_browser_deep.js
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = 'http://43.128.145.141';
const DMS = BASE + '/dms';
const OUTDIR = path.join(__dirname, 'v4-browser-results');
fs.mkdirSync(OUTDIR, { recursive: true });
const RESULTS = [];
function log(name, ok, detail) {
  RESULTS.push({ name, ok: !!ok, detail });
  console.log((ok ? 'PASS ' : 'FAIL ') + name + ' :: ' + JSON.stringify(detail).slice(0, 450));
}

function attach(page) {
  const reqs = []; // {method,url,status}
  page.on('response', r => {
    const u = r.url();
    if (u.includes('/api/')) reqs.push({ method: r.request().method(), url: u.replace(BASE, '').slice(0, 160), status: r.status() });
  });
  page.on('console', m => {
    if (m.type() === 'error' && !/favicon|ResizeObserver|net::ERR_|DevTools/i.test(m.text())) {
      reqs.push({ consoleError: m.text().slice(0, 200) });
    }
  });
  page.on('pageerror', e => reqs.push({ pageerror: String(e).slice(0, 200) }));
  return {
    badReqs: () => reqs.filter(r => r.status && r.status >= 400),
    errs: () => reqs.filter(r => r.consoleError || r.pageerror),
    all: () => reqs.slice()
  };
}

async function waitApp(page, t = 10000) {
  const s = Date.now();
  while (Date.now() - s < t) {
    const n = await page.locator('*').count().catch(() => 0);
    if (n > 20) return n;
    await page.waitForTimeout(300);
  }
  return await page.locator('*').count().catch(() => 0);
}

async function shoot(page, name) {
  await page.screenshot({ path: path.join(OUTDIR, name + '.png'), fullPage: false }).catch(() => {});
}

async function loginPC(page) {
  await page.goto(DMS + '/login', { waitUntil: 'networkidle', timeout: 20000 }).catch(() => {});
  await waitApp(page);
  await page.locator('input[placeholder*="租户"]').first().fill('default').catch(() => {});
  await page.locator('input[placeholder*="账号"]').first().fill('admin').catch(() => {});
  await page.locator('input[type="password"]').first().fill('Sh123456').catch(() => {});
  await page.locator('button:has-text("登录"), button:has-text("登 录"), .login-btn').first().click().catch(() => {});
  await page.waitForTimeout(4500);
  await page.waitForLoadState('networkidle').catch(() => {});
  // 确认进入工作台；若仍在登录页则重试一次
  if (/\/login/.test(page.url())) {
    await page.waitForTimeout(2000);
    if (/\/login/.test(page.url())) {
      await page.locator('button:has-text("登录"), button:has-text("登 录"), .login-btn').first().click().catch(() => {});
      await page.waitForTimeout(4000);
    }
  }
  return !/\/login/.test(page.url());
}

async function ensureLogin(page) {
  if (/\/login/.test(page.url())) { await loginPC(page); await page.waitForTimeout(1500); }
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, ignoreHTTPSErrors: true });
  const page = await ctx.newPage();
  const mon = attach(page);
  const logged = await loginPC(page);
  log('D0-PC登录', logged, { url: page.url().replace(BASE, '') });

  // ===== D1: 侧边菜单完整采集（hover 展开每个分组）=====
  try {
    await page.goto(DMS + '/home', { waitUntil: 'networkidle' }).catch(() => {});
    await page.waitForTimeout(2000);
    await ensureLogin(page);
    // el-menu 默认非手风琴模式：对每个分组 title 仅 click 一次即可全部展开（避免 hover popper 脱离 DOM / 重复 click 收起）
    const subs = page.locator('aside .el-sub-menu__title, .el-menu--vertical .el-sub-menu__title');
    const n = await subs.count();
    for (let i = 0; i < n; i++) {
      await subs.nth(i).click().catch(() => {});
      await page.waitForTimeout(180);
    }
    await page.waitForTimeout(800);
    // 关键修复：折叠态 aside .el-menu 的 innerText 只含可见顶层项（约 57 字），
    // 子菜单项虽在 DOM 但折叠时 innerText 取不到；必须用 .el-menu-item/.el-sub-menu__title 全量取文本
    let menuTxt = (await page.locator('.el-menu-item, .el-sub-menu__title').allInnerTexts().catch(() => [])).join(' ');
    const compact = menuTxt.replace(/\s+/g, ' ');
    const hasSupplier = /供应商/.test(compact);
    const hasPurchase = /采购订单/.test(compact);
    const hasWarehouse = /仓库管理/.test(compact);
    const hasConsign = /寄售库存/.test(compact);
    const hasCredit = /资信/.test(compact);
    // 动态读取当前租户进销存开关：开启→采购/仓库/供应商菜单可见；关闭→厂家隐藏
    let inventoryOn = true;
    try {
      const feat = await page.evaluate(async () => {
        const tok = localStorage.getItem('token') || '';
        const r = await fetch('/api/tenant/features', { headers: { Authorization: 'Bearer ' + tok } });
        if (!r.ok) return null;
        const j = await r.json(); return (j && j.data) ? j.data : j;
      });
      if (feat && typeof feat.inventoryEnabled !== 'undefined') inventoryOn = !!feat.inventoryEnabled;
    } catch (e) {}
    log('D1-侧边菜单:寄售库存可见', hasConsign, { hasConsign });
    log('D1-侧边菜单:资信账期可见', hasCredit, { hasCredit });
    log('D1-侧边菜单:进销存菜单与开关一致', inventoryOn ? (hasPurchase && hasWarehouse) : (!hasPurchase && !hasWarehouse), { inventoryOn, hasPurchase, hasWarehouse });
    log('D1-侧边菜单:供应商菜单与开关一致', inventoryOn ? hasSupplier : !hasSupplier, { inventoryOn, hasSupplier });
    await shoot(page, 'D1-侧边菜单全展开');
  } catch (e) { log('D1-侧边菜单采集', false, { error: String(e).slice(0, 200) }); }

  // ===== D2: 供应商菜单点击后的实际表现（若菜单可见）=====
  try {
    // 先展开含供应商的父级 sub-menu（折叠态直接点叶项会落空），再点可见叶项
    const supSub = page.locator('.el-sub-menu', { hasText: '供应商' }).first();
    if (await supSub.count()) { await supSub.locator('.el-sub-menu__title').first().click().catch(()=>{}); await page.waitForTimeout(600); }
    const supLink = page.locator('.el-menu-item:has-text("供应商"):visible').first();
    if (await supLink.count()) {
      mon.all(); // reset marker by slicing below
      const before = mon.all().length;
      await supLink.click().catch(() => {});
      await page.waitForTimeout(3000);
      const url = page.url();
      const txt = (await page.locator('body').innerText().catch(() => '')) || '';
      const compact = txt.replace(/\s+/g, ' ');
      const newReqs = mon.all().slice(before);
      const bad = newReqs.filter(r => r.status && r.status >= 400);
      const errDialog = /未启用|进销存|40006|没有权限|无权限/.test(compact);
      log('D2-点击供应商菜单', bad.length === 0 && /supplier/.test(url), {
        url: url.replace(BASE, ''), badReqs: bad.slice(0, 4), errDialog,
        bodySample: compact.slice(0, 180)
      });
      await shoot(page, 'D2-供应商页面');
    } else {
      log('D2-点击供应商菜单', true, { skipped: '菜单中无供应商入口（与后端拦截一致）' });
    }
  } catch (e) { log('D2-供应商菜单', false, { error: String(e).slice(0, 200) }); }

  // ===== D3: 销退新建页 400 报错溯源 + 货源门禁 =====
  try {
    const before = mon.all().length;
    await page.goto(DMS + '/sales-return-edit', { waitUntil: 'networkidle', timeout: 18000 }).catch(() => {});
    await page.waitForTimeout(2500);
    await ensureLogin(page);
    await page.waitForTimeout(2500);
    const newReqs = mon.all().slice(before);
    const bad = newReqs.filter(r => r.status && r.status >= 400);
    const txt = (await page.locator('body').innerText().catch(() => '')) || '';
    const compact = txt.replace(/\s+/g, ' ');
    const hasGateHint = /请先选择经销商|先在上方/.test(compact);
    const hasReturnType = /0金额产品退货|退货类型/.test(compact);
    log('D3-销退新建页无异常400报错', bad.length === 0, {
      badReqs: bad.slice(0, 6), gateHint: hasGateHint, hasReturnType,
      bodySample: compact.slice(0, 200)
    });
    // 未选经销商时点"选择发货单"按钮应被门禁
    const pickBtn = page.locator('button:has-text("选择发货单"), button:has-text("选择发货")').first();
    if (await pickBtn.count()) {
      const b2 = mon.all().length;
      await pickBtn.click().catch(() => {});
      await page.waitForTimeout(1500);
      const after = mon.all().slice(b2);
      const dialogOpened = await page.locator('.el-dialog:visible, .el-dialog__wrapper:visible').count().catch(() => 0);
      const body2 = (await page.locator('body').innerText().catch(() => '')) || '';
      const gated = /请先选择|经销商|仓库/.test(body2.replace(/\s+/g, ' '));
      log('D3-未选经销商时发货单弹窗门禁', dialogOpened === 0 || gated, {
        dialogOpened, badReqs: after.filter(r => r.status && r.status >= 400).slice(0, 4), gated
      });
    }
    await shoot(page, 'D3-销退新建页');
  } catch (e) { log('D3-销退新建页', false, { error: String(e).slice(0, 200) }); }

  // ===== D4: 销售订单新建页 400 溯源 + 订单类型切换 =====
  try {
    const before = mon.all().length;
    await page.goto(DMS + '/order-create/sales', { waitUntil: 'networkidle', timeout: 18000 }).catch(() => {});
    await page.waitForTimeout(2500);
    await ensureLogin(page);
    await page.waitForTimeout(2500);
    const newReqs = mon.all().slice(before);
    const bad = newReqs.filter(r => r.status && r.status >= 400);
    log('D4-订单新建页无异常400报错', bad.length === 0, { badReqs: bad.slice(0, 6), onLoginPage: /\/login/.test(page.url()) });

    // 订单类型下拉：检查 补货/开票 选项是否存在且未开启寄售时禁用
    // 真实点开订单类型 select 并等待下拉浮层出现（Element Plus 选项渲染在 body）
    await page.keyboard.press('Escape').catch(() => {});
    await page.waitForTimeout(200);
    const typeSelect = page.locator('.el-select').filter({ hasText: /销售订单|补货|开票|样品|选择订单类型|订单类型/ }).first();
    await typeSelect.click().catch(() => {});
    try { await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().waitFor({ timeout: 5000 }); } catch (e) {}
    await page.waitForTimeout(400);
    let opts = (await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').allInnerTexts().catch(() => []))
      .join(' ').replace(/\s+/g, ' ');
    const hasReplenish = /补货订单/.test(opts);
    const hasInvoice = /开票订单/.test(opts);
    const hasSample = /样品订单/.test(opts);
    // 未选经销商时，补货/开票选项应禁用（ dealerConsignment=false）
    const disabledItems = await page.locator('.el-select-dropdown:visible .el-select-dropdown__item.is-disabled').allInnerTexts().catch(() => []);
    log('D4-订单类型选项完整(补货/开票/样品)', hasReplenish && hasInvoice && hasSample,
      { options: opts.slice(0, 200), disabledItems: (disabledItems || []).join(' | ').slice(0, 200) });
    await page.keyboard.press('Escape').catch(() => {});
    await shoot(page, 'D4-订单类型下拉');
  } catch (e) { log('D4-订单新建页', false, { error: String(e).slice(0, 200) }); }

  // ===== D5: 寄售库存页表格内容与筛选（R3 台账展示）=====
  try {
    const before = mon.all().length;
    await page.goto(DMS + '/consignment-stock', { waitUntil: 'networkidle', timeout: 18000 }).catch(() => {});
    await page.waitForTimeout(2500);
    await ensureLogin(page);
    await page.waitForTimeout(2500);
    const newReqs = mon.all().slice(before);
    const bad = newReqs.filter(r => r.status && r.status >= 400);
    const rows = await page.locator('.el-table__body-wrapper tr').count().catch(() => 0);
    const headers = (await page.locator('.el-table__header-wrapper').innerText().catch(() => '')) || '';
    const hCompact = headers.replace(/\s+/g, ' ');
    const hasCols = /批号/.test(hCompact) && /序列号/.test(hCompact) && /可用/.test(hCompact) && /标准单价/.test(hCompact);
    log('D5-寄售库存页台账列完整', rows > 0 && hasCols && bad.length === 0,
      { rows, hasCols, badReqs: bad.slice(0, 4), headers: hCompact.slice(0, 220) });
    await shoot(page, 'D5-寄售库存页');
  } catch (e) { log('D5-寄售库存页', false, { error: String(e).slice(0, 200) }); }

  // ===== D6: 资信账期页表格内容（R5）=====
  try {
    const before = mon.all().length;
    await page.goto(DMS + '/dealer-credit', { waitUntil: 'networkidle', timeout: 18000 }).catch(() => {});
    await page.waitForTimeout(2500);
    await ensureLogin(page);
    await page.waitForTimeout(2500);
    const newReqs = mon.all().slice(before);
    const bad = newReqs.filter(r => r.status && r.status >= 400);
    const rows = await page.locator('.el-table__body-wrapper tr').count().catch(() => 0);
    const headers = (await page.locator('.el-table__header-wrapper').innerText().catch(() => '')) || '';
    const hCompact = headers.replace(/\s+/g, ' ');
    const hasCols = /信用额度/.test(hCompact) && /寄售占用/.test(hCompact) && /账期/.test(hCompact);
    log('D6-资信账期页字段完整', rows > 0 && hasCols && bad.length === 0,
      { rows, hasCols, badReqs: bad.slice(0, 4), headers: hCompact.slice(0, 220) });
    await shoot(page, 'D6-资信账期页');
  } catch (e) { log('D6-资信账期页', false, { error: String(e).slice(0, 200) }); }

  await browser.close();

  const passed = RESULTS.filter(r => r.ok).length;
  const failed = RESULTS.filter(r => !r.ok);
  const summary = { total: RESULTS.length, passed, failed: failed.length, at: new Date().toISOString() };
  fs.writeFileSync(path.join(OUTDIR, 'deep-results.json'), JSON.stringify({ summary, results: RESULTS }, null, 2), 'utf-8');
  console.log('\n==== DEEP SUMMARY ====');
  console.log(JSON.stringify(summary));
  failed.forEach(f => console.log('FAILED:', f.name, '->', JSON.stringify(f.detail).slice(0, 350)));
  process.exit(0);
})().catch(e => { console.error('FATAL', e); process.exit(2); });
