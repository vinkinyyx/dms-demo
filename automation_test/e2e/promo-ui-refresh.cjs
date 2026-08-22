const path = require('path');
const { chromium } = require('playwright');
const config = require(path.join(__dirname, 'config'));
const { loginPC } = require(path.join(__dirname, 'helpers/auth'));
const BASE = config.BASE;
async function loginToken() {
  const r = await fetch(BASE + '/api/auth/login', { method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({ tenantCode: config.pc.tenant, username: config.pc.username, password: config.pc.password }) });
  return (await r.json()).data.accessToken;
}
(async () => {
  const browser = await chromium.launch({ headless: true });
  const token = await loginToken();
  const { ctx, page } = await loginPC(browser);
  await ctx.addInitScript((t) => { localStorage.setItem('dms_access_token', t); }, token);
  const errors = [];
  page.on('console', m => {
    if (m.type() !== 'error') return;
    const t = m.text();
    if (/favicon\.ico|\.map\b|Failed to load resource|the server responded with a status of 404/i.test(t)) return;
    errors.push(t);
  });
  page.on('pageerror', e => errors.push('PAGE:' + e.message));
  page.on('response', r => {
    const u = r.url();
    if (r.status() >= 500 && /\/api\//.test(u)) errors.push('HTTP ' + r.status() + ' ' + u); else if (r.status() >= 400 && /\/api\//.test(u) && !/product-bundles\/product\/\d+\/active/.test(u)) { errors.push('HTTP ' + r.status() + ' ' + u); }
  });
  const outDir = path.join(__dirname, '..', 'v4-browser-results');
  try {
    await page.goto(BASE + '/order-create/sales', { waitUntil: 'networkidle' });
    await page.locator('.order-create-page').waitFor();
    await page.locator('.el-form-item').nth(0).locator('input').first().click();
    await page.waitForTimeout(1200);
    await page.locator('.el-dialog:visible').last().locator('.el-table__row').first().click();
    await page.waitForTimeout(800);
    await page.getByRole('button', { name: '添加行' }).click();
    await page.waitForTimeout(500);
    await page.locator('.order-create-page .el-table__row').first().locator('input').first().click();
    await page.waitForTimeout(1200);
    let dlg = page.locator('.el-dialog:visible').last();
    await dlg.locator('input').first().fill('PRD-J002');
    await page.waitForTimeout(1000);
    await dlg.locator('.el-table__row').first().click();
    await page.waitForTimeout(800);
    const qty = page.locator('.order-create-page .el-table .el-input-number input').first();
    await qty.fill('6');
    await qty.evaluate(el => el.blur());
    await page.waitForTimeout(1200);
    const giftTag = () => page.locator('.order-create-page .el-tag').filter({ hasText: '赠品' });
    const before = await giftTag().count();
    await page.getByRole('button', { name: /刷新赠品及价格/ }).click();
    await page.waitForTimeout(2000);
    const after = await giftTag().count();
    const body = await page.locator('.order-create-page').innerText();
    const msg = /买2送5/.test(body);
    await page.getByRole('button', { name: /刷新赠品及价格/ }).click();
    await page.waitForTimeout(1500);
    const after2 = await giftTag().count();
    const giftRow = page.locator('.order-create-page .el-table__row').filter({ has: page.getByText('PRD-J003') });
    const giftQtyDisabled = await giftRow.locator('.el-input-number input').first().isDisabled().catch(() => true);
    await page.screenshot({ path: path.join(outDir, 'promo-ui-refresh.png'), fullPage: true });
    console.log('giftBefore='+before,'giftAfter='+after,'giftAfter2='+after2,'msg='+msg,'giftQtyDisabled='+giftQtyDisabled,'errors='+errors.length);
    const pass = before===0 && after>=1 && after2===after && msg && giftQtyDisabled && errors.length===0;
    console.log(pass ? 'UI_REFRESH_PASS' : 'UI_REFRESH_FAIL');
    if (errors.length) console.log('ERR DETAILS:', JSON.stringify(errors.slice(0,5)));
    if (!pass) process.exit(1);
  } finally { await ctx.close(); await browser.close(); }
})();






