/*
 * 管理员后台深度审计：登录、遍历所有页面、监听 Console/网络错误。
 * 只读不写（所有写操作通过路由拦截返回 200）。
 */
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = process.env.DMS_BASE || 'http://43.128.145.141';
const PREFIX = BASE + '/dms/admin';
const STAMP = new Date().toISOString().replace(/[-:T.Z]/g, '').slice(0, 14);
const OUT = path.join(__dirname, '..', 'automation_test', 'v4-browser-results', `admin-${STAMP}`);
fs.mkdirSync(OUT, { recursive: true });

const PAGES = [
  { path: '/', name: '首页总览' },
  { path: '/tenants/manufacturers', name: '厂家租户' },
  { path: '/tenants/dealers', name: '经销商租户' },
  { path: '/tenant-admins', name: '租户管理员' },
  { path: '/role-templates', name: '角色模板' },
  { path: '/menus', name: '平台菜单' },
  { path: '/ui-configs', name: '页面配置' },
  { path: '/dicts', name: '全局字典' },
  { path: '/logs/api', name: '接口日志' },
  { path: '/logs/audits', name: '审计日志' },
  { path: '/reports', name: '报表总览' }
];

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  const consoleErrors = [];
  const networkErrors = [];
  page.on('console', m => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  page.on('pageerror', e => consoleErrors.push('PAGEERROR: ' + e.message));
  page.on('response', r => {
    const u = r.url();
    if (u.includes('/api/') && r.status() >= 400) networkErrors.push(`${r.status()} ${u.replace(BASE, '')}`);
  });
  await page.route('**/api/admin/**', async route => {
    const m = route.request().method();
    const u = route.request().url();
    // 放行登录/登出/刷新 token 等认证请求，否则登录无法真正建立会话
    if (m !== 'GET' && m !== 'HEAD' && !/\/auth\/(login|logout|refresh)/.test(u)) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, data: null }) });
    } else {
      await route.continue();
    }
  });

  const results = [];

  await page.goto(`${PREFIX}/login`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(800);
  await page.locator('input').nth(0).fill('admin');
  await page.locator('input[type=password]').fill('Sh123456');
  await page.getByRole('button', { name: /登/ }).click();
  await page.waitForTimeout(4000);
  const loggedIn = !page.url().includes('/login');
  results.push({ name: '登录', status: loggedIn ? 'PASS' : 'FAIL', url: page.url().replace(BASE, '') });

  for (const p of PAGES) {
    const r = { name: p.name, path: p.path };
    const ce = consoleErrors.length, ne = networkErrors.length;
    try {
      await page.goto(`${PREFIX}${p.path}`, { waitUntil: 'domcontentloaded', timeout: 20000 });
      await page.waitForTimeout(2500);
      const onLogin = page.url().includes('/login');
      const body = await page.evaluate(() => document.body.innerText);
      const tables = await page.locator('.el-table').count();
      const rows = await page.locator('.el-table__row').count();
      const hasError = /Cannot read|undefined is not|TypeError:|ReferenceError:|系统异常/.test(body);
      const has404 = /404|Not Found|页面不存在/.test(body);
      r.url = page.url().replace(BASE, '');
      r.tables = tables;
      r.rows = rows;
      r.consoleErrors = consoleErrors.slice(ce);
      r.networkErrors = networkErrors.slice(ne);
      r.status = (onLogin || hasError || has404) ? 'FAIL' : 'PASS';
      await page.screenshot({ path: path.join(OUT, `${p.name}.png`) });
    } catch (e) {
      r.status = 'FAIL';
      r.error = e.message;
    }
    results.push(r);
  }

  const summary = results.reduce((a, r) => { a[r.status] = (a[r.status]||0)+1; return a; }, {});
  fs.writeFileSync(path.join(OUT, 'admin-report.json'), JSON.stringify({ base: BASE, summary, results, remainingConsoleErrors: consoleErrors, remainingNetworkErrors: networkErrors }, null, 2), 'utf8');
  console.log(JSON.stringify({ summary, out: OUT }, null, 2));
  results.forEach(r => console.log(`[${r.status}] ${r.name.padEnd(8)} rows=${r.rows ?? '-'} ${(r.consoleErrors||[]).join('; ')}${(r.networkErrors||[]).join('; ')}${r.error||''}`));
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
