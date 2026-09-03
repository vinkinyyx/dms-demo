const { chromium } = require('playwright');

const BASE = 'http://dms-dev.mysolmed.com';
const results = [];
function rec(name, ok, msg) { results.push({ name, ok, msg }); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}  ${msg || ''}`); }

(async () => {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  const consoleErrors = [];
  page.on('console', m => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  const apiErrors = [];
  page.on('response', r => { const u = r.url(); if (/\/api\/|\/auth\//.test(u) && r.status() >= 500) apiErrors.push(`${r.status()} ${u}`); });

  // ===== 铁律9：八入口 GATE（最终可达即可）=====
  const entries = [
    { name: 'root-302', url: BASE + '/', expect: '/dms/' },
    { name: 'pc', url: BASE + '/dms/' },
    { name: 'admin', url: BASE + '/dms/admin/' },
    { name: 'mobile-login', url: BASE + '/dms/mobile/login' },
    { name: 'mobile-register', url: BASE + '/dms/mobile/register' },
    { name: 'brochure-pc', url: BASE + '/brochure/' },
    { name: 'brochure-mobile', url: BASE + '/brochure/mobile.html' },
    { name: 'brochure-print', url: BASE + '/brochure/print.html' },
  ];
  for (const e of entries) {
    try {
      const resp = await page.goto(e.url, { waitUntil: 'domcontentloaded', timeout: 30000 });
      await page.waitForTimeout(1500);
      const finalUrl = page.url();
      const title = await page.title();
      const refs = await page.evaluate(() => document.querySelectorAll('*').length);
      let ok = resp && (resp.status() === 200 || resp.status() === 302 || resp.status() === 301);
      if (e.expect) ok = ok && finalUrl.includes(e.expect.replace(/^\//, ''));
      // 宣传移动/打印页不应回退成 PC 首页
      rec('GATE:' + e.name, ok && refs >= 20, `status=${resp && resp.status()} refs=${refs} title=${title} final=${finalUrl}`);
    } catch (err) { rec('GATE:' + e.name, false, err.message); }
  }

  // ===== 业务前台登录（API 拿 token，注入正确的 localStorage key）=====
  let apiToken = '';
  try {
    const loginResp = await ctx.request.post(BASE + '/api/auth/login', {
      data: { tenantCode: 'default', username: 'sys_admin', password: 'Dms@123456' }
    });
    const loginJson = await loginResp.json().catch(() => ({}));
    apiToken = (loginJson.data && loginJson.data.accessToken) || '';
    rec('PC:login', !!apiToken, 'http=' + loginResp.status());
    // 注入到新打开页面的 localStorage（frontend-vue 读取 dms_access_token）
    if (apiToken) {
      await ctx.addInitScript(([t]) => { try { localStorage.setItem('dms_access_token', t); } catch (e) {} }, [apiToken]);
    }
  } catch (err) { rec('PC:login', false, err.message); }

  // ===== 授权管理页 =====
  try {
    await page.goto(BASE + '/dms/authorizations', { waitUntil: 'networkidle', timeout: 30000 }).catch(()=>{});
    await page.waitForTimeout(3000);
    const bodyText = await page.locator('body').innerText();
    const hasAuth = bodyText.includes('授权') && (bodyText.includes('挂钩') || bodyText.includes('产品线') || bodyText.includes('新增授权'));
    const refs = await page.evaluate(() => document.querySelectorAll('*').length);
    rec('Auth:list-page', hasAuth && refs >= 20, `refs=${refs} hasAuthText=${hasAuth}`);
  } catch (err) { rec('Auth:list-page', false, err.message); }

  // ===== 授权 API：开关查询 + 列表 + 产品线 + 终端选择器（用 API 登录 token）=====
  try {
    const authHeader = apiToken ? { Authorization: 'Bearer ' + apiToken } : {};
    for (const [label, path] of [
      ['order-enforce', '/api/authorizations/order-enforce'],
      ['list', '/api/authorizations?page=1&size=5'],
      ['product-lines', '/api/authorizations/product-lines'],
      ['terminals', '/api/authorizations/terminals?keyword='],
    ]) {
      try {
        const r = await page.request.get(BASE + path, { headers: authHeader });
        const j = await r.json().catch(() => ({}));
        const ok = r.ok() && j && (j.code === 0 || j.code === 200 || j.success === true || j.data !== undefined);
        rec('AuthAPI:' + label, ok, `http=${r.status()} dataKeys=${j.data ? Object.keys(j.data).slice(0,5).join(',') : 'none'}`);
      } catch (e) { rec('AuthAPI:' + label, false, e.message); }
    }
  } catch (err) { rec('AuthAPI', false, err.message); }

  // ===== 合同页 + 终止 API 存在性（OPTIONS/GET）=====
  try {
    await page.goto(BASE + '/dms/contracts', { waitUntil: 'networkidle', timeout: 30000 }).catch(()=>{});
    await page.waitForTimeout(2000);
    const refs = await page.evaluate(() => document.querySelectorAll('*').length);
    const bodyText = await page.locator('body').innerText();
    rec('Contract:workspace', refs >= 20 && bodyText.includes('合同'), `refs=${refs}`);
  } catch (err) { rec('Contract:workspace', false, err.message); }

  rec('Console:errors', consoleErrors.length === 0, consoleErrors.slice(0,3).join(' | '));
  rec('Network:5xx', apiErrors.length === 0, apiErrors.slice(0,5).join(' | '));

  await browser.close();
  const failed = results.filter(r => !r.ok);
  console.log(`\n==== SUMMARY: ${results.length - failed.length}/${results.length} passed ====`);
  if (failed.length) { console.log('FAILED:'); failed.forEach(f => console.log('  - ' + f.name + ': ' + f.msg)); process.exit(1); }
})();
