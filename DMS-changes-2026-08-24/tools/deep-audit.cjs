const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = process.env.DMS_BASE_URL || 'http://43.128.145.141';
const STAMP = Date.now();
const OUT = path.join(__dirname, '..', 'automation_test', 'v4-browser-results', `deep-audit-${STAMP}`);
fs.mkdirSync(OUT, { recursive: true });

const results = [];
const allConsoleErrors = [];
const allNetworkErrors = [];

function rec(scope, id, status, detail) {
  const row = { scope, id, status, detail: String(detail || '').slice(0, 600), t: new Date().toISOString() };
  results.push(row);
  const icon = status === 'PASS' ? 'PASS' : status === 'WARN' ? 'WARN' : 'FAIL';
  console.log(`[${icon}] ${scope}/${id} :: ${String(detail || '').slice(0, 180)}`);
}
const sleep = ms => new Promise(r => setTimeout(r, ms));

function attachListeners(page, label) {
  const consoleErrors = [];
  const networkErrors = [];
  page.on('console', msg => {
    if (msg.type() === 'error') {
      const text = msg.text();
      if (/favicon|ResizeObserver|Download is prohibited|net::ERR_ABORTED|ERR_INTERNET_DISCONNECTED/i.test(text)) return;
      consoleErrors.push({ label, text: text.slice(0, 300) });
      allConsoleErrors.push({ label, text: text.slice(0, 300) });
    }
  });
  page.on('pageerror', err => {
    const text = String(err.message || err);
    consoleErrors.push({ label, text: 'pageerror: ' + text.slice(0, 300) });
    allConsoleErrors.push({ label, text: 'pageerror: ' + text.slice(0, 300) });
  });
  page.on('requestfailed', req => {
    const u = req.url();
    if (/favicon|googleapis|gstatic/i.test(u)) return;
    networkErrors.push({ label, url: u, err: req.failure()?.errorText });
    allNetworkErrors.push({ label, url: u, err: req.failure()?.errorText });
  });
  page.on('response', resp => {
    const s = resp.status();
    if (s >= 500) {
      networkErrors.push({ label, url: resp.url(), status: s });
      allNetworkErrors.push({ label, url: resp.url(), status: s });
    }
  });
  return { consoleErrors, networkErrors };
}

async function loginPC(page) {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await sleep(800);
  const inputs = page.locator('.login-form input');
  if (await inputs.count() >= 3) {
    await inputs.nth(0).fill('default');
    await inputs.nth(1).fill('admin');
    await inputs.nth(2).fill('Sh123456');
  } else {
    await inputs.nth(0).fill('admin');
    await inputs.nth(1).fill('Sh123456');
  }
  await page.locator('.btn-login, button[type=submit], .el-button--primary').first().click();
  await page.waitForURL(/^((?!\/login).)*$/, { timeout: 15000 }).catch(() => {});
  await sleep(2000);
  return !page.url().includes('/login');
}

async function loginAdmin(page) {
  await page.goto(`${BASE}/admin/login`, { waitUntil: 'networkidle' });
  await sleep(800);
  const inputs = page.locator('input');
  await inputs.nth(0).fill('admin');
  await inputs.nth(1).fill('Sh123456');
  await page.locator('button.el-button--primary, button[type=submit]').first().click();
  await sleep(2500);
  return !page.url().includes('/login');
}

async function loginMobile(page) {
  await page.goto(`${BASE}/mobile/login`, { waitUntil: 'networkidle' });
  await sleep(800);
  const inputs = page.locator('.van-field input, .m-form input, input');
  const count = await inputs.count();
  for (let i = 0; i < count; i++) {
    const t = await inputs.nth(i).getAttribute('type') || 'text';
    if (t === 'password') await inputs.nth(i).fill('Sh123456');
    else {
      const v = await inputs.nth(i).inputValue().catch(() => '');
      if (!v) {
        await inputs.nth(i).fill('admin');
        break;
      }
    }
  }
  await page.getByRole('button', { name: /登\s*录/ }).first().click();
  await sleep(2500);
  return !page.url().includes('/login');
}

async function visitPage(page, url, label, timeoutMs = 20000) {
  const listener = attachListeners(page, label);
  try {
    const resp = await page.goto(url, { waitUntil: 'networkidle', timeout: timeoutMs });
    await sleep(700);
    const status = resp ? resp.status() : 0;
    const body = (await page.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ').trim();
    const has404 = /抱歉.*页面不存在|404.*页面不存在/.test(body);
    const has500 = /Internal Server Error|系统异常|系统错误|Cannot read prop|TypeError:|ReferenceError:/.test(body);
    const hasLayout = body.length > 30;
    const ok = status < 400 && !has404 && !has500 && hasLayout;
    rec(label, 'loads', ok ? 'PASS' : 'FAIL', `http=${status} len=${body.length} ${body.slice(0, 120)}`);
    await page.screenshot({ path: path.join(OUT, `${label}.png`), fullPage: false }).catch(() => {});
    return { ok, status, body, listener };
  } catch (e) {
    rec(label, 'loads', 'FAIL', e.message.slice(0, 200));
    return { ok: false, status: 0, body: '', listener, error: e.message };
  }
}

async function checkDetailDialog(page, label) {
  try {
    const rows = page.locator('.el-table__body-wrapper tbody tr');
    const rowCount = await rows.count();
    if (rowCount === 0) {
      rec(label, 'detail', 'WARN', 'no data rows - cannot test detail');
      return;
    }
    const firstRow = rows.first();
    const actionBtn = firstRow.locator('.el-button').filter({ hasText: /查看|详情/ }).first();
    if (await actionBtn.count() === 0) {
      const anyBtn = firstRow.locator('.el-button').first();
      if (await anyBtn.count() === 0) { rec(label, 'detail', 'WARN', 'no action buttons'); return; }
      await anyBtn.click();
    } else {
      await actionBtn.click();
    }
    await sleep(1000);
    const dialog = page.locator('.el-dialog:visible, .el-drawer:visible, .el-descriptions:visible').first();
    const dialogVisible = await dialog.count() > 0 && await dialog.isVisible().catch(() => false);
    if (dialogVisible) {
      const dlgText = (await dialog.innerText().catch(() => '')).replace(/\s+/g, ' ').trim();
      const hasRawId = /\b(partnerId|productId|orderId|dealerId|hospitalId|warehouseId|targetProductId)\b\s*[:：]\s*\d/.test(dlgText);
      const hasRawEnum = /\b(EVERY_N|SKU|BOM|PENDING_APPROVAL|DRAFT|SUBMITTED|APPROVED|REJECTED|CANCELLED|COMPLETED|QUALIFIED|UNQUALIFIED)\b/.test(dlgText)
        && !/状态|审批|草稿|已提交|已完成|已取消|待审批|合格|不合格/.test(dlgText);
      rec(label, 'detail', 'PASS', `dialog shown len=${dlgText.length} rawId=${hasRawId} rawEnum=${hasRawEnum} text=${dlgText.slice(0,150)}`);
      await page.screenshot({ path: path.join(OUT, `${label}-detail.png`) }).catch(() => {});
      await page.keyboard.press('Escape').catch(() => {});
      const close = page.locator('.el-dialog__headerbtn:visible, .el-drawer__close-btn:visible').first();
      if (await close.count()) await close.click().catch(() => {});
      await sleep(400);
    } else {
      rec(label, 'detail', 'FAIL', 'clicked action but no dialog/drawer/detail appeared');
    }
  } catch (e) {
    rec(label, 'detail', 'FAIL', e.message.slice(0, 200));
  }
}

async function checkCreateForm(page, label) {
  try {
    const createBtn = page.locator('button').filter({ hasText: /^(新增|新建|添加|创建)$/ }).first();
    if (await createBtn.count() === 0) {
      rec(label, 'create', 'WARN', 'no create button on page');
      return;
    }
    await createBtn.click();
    await sleep(900);
    const form = page.locator('.el-dialog:visible .el-form, .el-drawer:visible .el-form').first();
    const formVisible = await form.count() > 0 && await form.isVisible().catch(() => false);
    if (formVisible) {
      const items = await form.locator('.el-form-item').count();
      const required = await form.locator('.el-form-item.is-required').count();
      rec(label, 'create', 'PASS', `form opened items=${items} required=${required}`);
      await page.screenshot({ path: path.join(OUT, `${label}-create.png`) }).catch(() => {});
      const cancel = page.locator('.el-dialog:visible button, .el-drawer:visible button').filter({ hasText: /取消|关闭/ }).first();
      if (await cancel.count()) await cancel.click().catch(() => {});
      else await page.keyboard.press('Escape').catch(() => {});
      await sleep(400);
    } else {
      rec(label, 'create', 'FAIL', 'clicked create but no form dialog appeared');
    }
  } catch (e) {
    rec(label, 'create', 'FAIL', e.message.slice(0, 200));
  }
}

async function auditPageLayout(page, label) {
  try {
    const dims = await page.evaluate(() => {
      const main = document.querySelector('.app-main, .main-content, .el-main, main, .page-container') || document.body;
      const rect = main.getBoundingClientRect();
      const tables = document.querySelectorAll('.el-table').length;
      const btns = document.querySelectorAll('button:visible').length;
      const overflowX = document.documentElement.scrollWidth > window.innerWidth + 5;
      return { width: Math.round(rect.width), height: Math.round(rect.height), tables, btns, overflowX };
    }).catch(() => ({}));
    const overlaps = await page.evaluate(() => {
      const dialogs = [...document.querySelectorAll('.el-dialog:visible, .el-drawer:visible')];
      return dialogs.map(d => {
        const r = d.getBoundingClientRect();
        return { left: Math.round(r.left), right: Math.round(r.right), w: Math.round(r.width), offscreen: r.right > window.innerWidth + 10 || r.left < -10 };
      });
    }).catch(() => []);
    const offscreen = overlaps.some(o => o.offscreen);
    rec(label, 'layout', offscreen ? 'WARN' : 'PASS', JSON.stringify({ ...dims, dialogs: overlaps.length, offscreen }));
  } catch (e) {
    rec(label, 'layout', 'FAIL', e.message.slice(0, 200));
  }
}

(async () => {
  console.log('=== DMS Deep Audit ===');
  console.log('Base:', BASE);
  console.log('Out:', OUT);

  const browser = await chromium.launch({ headless: true });

  // ==================== ADMIN ====================
  console.log('\n--- Admin Backend ---');
  const adCtx = await browser.newContext({ viewport: { width: 1440, height: 900 }, ignoreHTTPSErrors: true });
  const adPage = await adCtx.newPage();
  const adListener = attachListeners(adPage, 'ADMIN');
  const adLogin = await loginAdmin(adPage);
  rec('ADMIN', 'login', adLogin ? 'PASS' : 'FAIL', adPage.url());

  const adminPages = [
    { path: '/admin/tenants/manufacturers', label: 'ADM-manufacturers' },
    { path: '/admin/tenants/dealers', label: 'ADM-dealers' },
    { path: '/admin/role-templates', label: 'ADM-role-templates' },
    { path: '/admin/menus', label: 'ADM-menus' },
    { path: '/admin/dicts', label: 'ADM-dicts' },
    { path: '/admin/logs/api', label: 'ADM-api-logs' },
    { path: '/admin/logs/audits', label: 'ADM-audit-logs' },
  ];
  if (adLogin) {
    for (const p of adminPages) {
      const r = await visitPage(adPage, `${BASE}${p.path}`, p.label);
      if (r.ok) {
        await checkDetailDialog(adPage, p.label);
        await checkCreateForm(adPage, p.label);
        await auditPageLayout(adPage, p.label);
      }
    }
  }
  rec('ADMIN', 'console', adListener.consoleErrors.length === 0 ? 'PASS' : 'WARN',
    adListener.consoleErrors.map(e => e.text).join(' | ').slice(0, 400) || 'clean');
  rec('ADMIN', 'network-5xx', adListener.networkErrors.filter(e => e.status >= 500).length === 0 ? 'PASS' : 'FAIL',
    JSON.stringify(adListener.networkErrors.filter(e => e.status >= 500)).slice(0, 400) || 'clean');

  // ==================== MOBILE ====================
  console.log('\n--- Mobile H5 ---');
  const mCtx = await browser.newContext({
    viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true,
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148',
    ignoreHTTPSErrors: true
  });
  const mPage = await mCtx.newPage();
  const mListener = attachListeners(mPage, 'MOBILE');
  const mLogin = await loginMobile(mPage);
  rec('MOBILE', 'login', mLogin ? 'PASS' : 'FAIL', mPage.url());

  const mobilePages = [
    { path: '/mobile/home', label: 'MB-home' },
    { path: '/mobile/approvals', label: 'MB-approvals' },
    { path: '/mobile/surgery-reports', label: 'MB-surgery-reports' },
    { path: '/mobile/surgery-reports/create', label: 'MB-surgery-create' },
    { path: '/mobile/messages', label: 'MB-messages' },
  ];
  if (mLogin) {
    for (const p of mobilePages) {
      const r = await visitPage(mPage, `${BASE}${p.path}`, p.label, 15000);
      if (r.ok) {
        const hasVant = await mPage.locator('.van-cell, .van-card, .van-button, .van-empty, .van-list').count().catch(() => 0);
        rec(p.label, 'vant-widgets', hasVant > 0 ? 'PASS' : 'WARN', `vant elements=${hasVant}`);
      }
    }
  }
  rec('MOBILE', 'console', mListener.consoleErrors.length === 0 ? 'PASS' : 'WARN',
    mListener.consoleErrors.map(e => e.text).join(' | ').slice(0, 400) || 'clean');

  // ==================== PC DEEP DETAIL AUDIT ====================
  console.log('\n--- PC Deep Detail Audit ---');
  const pcCtx = await browser.newContext({ viewport: { width: 1440, height: 900 }, ignoreHTTPSErrors: true });
  const pcPage = await pcCtx.newPage();
  const pcListener = attachListeners(pcPage, 'PC');
  const pcLogin = await loginPC(pcPage);
  rec('PC', 'login', pcLogin ? 'PASS' : 'FAIL', pcPage.url());

  const deepAuditPages = [
    { path: '/m/products', label: 'PC-products' },
    { path: '/m/dealers', label: 'PC-dealers' },
    { path: '/m/orders', label: 'PC-orders' },
    { path: '/m/inventory', label: 'PC-inventory' },
    { path: '/m/product-prices', label: 'PC-product-prices' },
    { path: '/m/promotions', label: 'PC-promotions' },
    { path: '/m/authorizations', label: 'PC-authorizations' },
    { path: '/contracts', label: 'PC-contracts' },
    { path: '/approval/admin', label: 'PC-approval-admin' },
    { path: '/m/users', label: 'PC-users' },
  ];
  if (pcLogin) {
    for (const p of deepAuditPages) {
      const r = await visitPage(pcPage, `${BASE}${p.path}`, p.label);
      if (r.ok) {
        await checkDetailDialog(pcPage, p.label);
        await checkCreateForm(pcPage, p.label);
        await auditPageLayout(pcPage, p.label);
      }
    }
  }
  rec('PC', 'console', pcListener.consoleErrors.length === 0 ? 'PASS' : 'WARN',
    pcListener.consoleErrors.map(e => e.text).join(' | ').slice(0, 500) || 'clean');
  rec('PC', 'network-5xx', pcListener.networkErrors.filter(e => e.status >= 500).length === 0 ? 'PASS' : 'FAIL',
    JSON.stringify(pcListener.networkErrors.filter(e => e.status >= 500)).slice(0, 500) || 'clean');

  // ==================== API BUSINESS STATE VERIFICATION ====================
  console.log('\n--- API Business Readback ---');
  const apiCtx = await browser.newContext({ ignoreHTTPSErrors: true });
  const apiPage = await apiCtx.newPage();
  let token = '';
  apiPage.on('response', async resp => {
    if (resp.url().includes('/auth/login') && resp.ok()) {
      try { const j = await resp.json(); token = j.data?.token || j.token || ''; } catch {}
    }
  });
  await loginPC(apiPage);
  await sleep(500);
  rec('API', 'token-acquired', token ? 'PASS' : 'FAIL', token ? `token len=${token.length}` : 'no token');

  const apiChecks = [
    { name: 'products-list', url: '/api/products?page=1&size=5', expectKeys: ['records', 'total'] },
    { name: 'dealers-list', url: '/api/dealers?page=1&size=5', expectKeys: ['records'] },
    { name: 'orders-list', url: '/api/orders?page=1&size=5', expectKeys: ['records'] },
    { name: 'inventory-list', url: '/api/inventory?page=1&size=5', expectKeys: ['records'] },
    { name: 'categories-list', url: '/api/categories/list', expectKeys: null },
    { name: 'dashboard-stats', url: '/api/dashboard/stats', expectKeys: null },
    { name: 'menus-tree', url: '/api/menus/tree', expectKeys: null },
  ];
  for (const c of apiChecks) {
    try {
      const resp = await apiPage.request.get(`${BASE}${c.url}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {}
      });
      const status = resp.status();
      let body = '';
      try { body = (await resp.text()).slice(0, 500); } catch {}
      let hasKeys = true;
      if (c.expectKeys && status === 200) {
        try {
          const j = JSON.parse(body);
          const d = j.data || j;
          hasKeys = c.expectKeys.every(k => k in d);
        } catch { hasKeys = false; }
      }
      rec('API', c.name, (status === 200 && hasKeys) ? 'PASS' : 'FAIL', `http=${status} keys=${hasKeys} body=${body.slice(0,180)}`);
    } catch (e) {
      rec('API', c.name, 'FAIL', e.message.slice(0, 200));
    }
  }

  // ==================== SUMMARY ====================
  const failed = results.filter(r => r.status === 'FAIL');
  const warned = results.filter(r => r.status === 'WARN');
  const passed = results.filter(r => r.status === 'PASS');
  const summary = {
    base: BASE, timestamp: new Date().toISOString(),
    total: results.length, passed: passed.length, warned: warned.length, failed: failed.length,
    failures: failed, warnings: warned,
    consoleErrors: allConsoleErrors,
    networkErrors: allNetworkErrors,
    resultDir: OUT
  };
  fs.writeFileSync(path.join(OUT, 'report.json'), JSON.stringify(summary, null, 2));

  console.log('\n=== Summary ===');
  console.log(`Total: ${results.length} | PASS: ${passed.length} | WARN: ${warned.length} | FAIL: ${failed.length}`);
  if (failed.length) {
    console.log('\nFAILURES:');
    failed.forEach(f => console.log(`  FAIL ${f.scope}/${f.id}: ${f.detail}`));
  }
  if (warned.length) {
    console.log('\nWARNINGS:');
    warned.forEach(f => console.log(`  WARN ${f.scope}/${f.id}: ${f.detail}`));
  }
  if (allConsoleErrors.length) {
    console.log('\nCONSOLE ERRORS:');
    allConsoleErrors.slice(0, 15).forEach(e => console.log(`  [${e.label}] ${e.text}`));
  }
  if (allNetworkErrors.filter(e => e.status >= 500).length) {
    console.log('\n5XX NETWORK ERRORS:');
    allNetworkErrors.filter(e => e.status >= 500).forEach(e => console.log(`  [${e.label}] ${e.status} ${e.url}`));
  }
  console.log('\nReport:', path.join(OUT, 'report.json'));
  console.log('Screenshots:', OUT);

  await browser.close();
  process.exit(failed.length ? 1 : 0);
})();
