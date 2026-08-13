const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const BASE = process.env.DMS_BASE_URL || 'http://43.128.145.141';
const OUT = path.join(__dirname, 'results');
const results = [];
const U = {
  login: '\u767b\u5f55',
  report: '\u62a5\u8868\u8ba2\u9605',
  create: '\u65b0\u5efa\u8ba2\u9605',
  save: '\u4fdd\u5b58',
  delete: '\u5220\u9664',
  confirm: '\u786e\u5b9a',
  stop: '\u505c\u7528',
  stock: '\u5e93\u5b58\u76d8\u70b9',
  upload: '\u4e0a\u4f20\u76d8\u70b9\u5355',
  profile: '\u4e2a\u4eba\u8d44\u6599\u4e0e\u5b89\u5168',
  mfa: '\u4e8c\u6b21\u9a8c\u8bc1',
  enableMfa: '\u542f\u7528 MFA',
  expiry: '\u6548\u671f\u9884\u8b66',
  trace: '\u5e8f\u5217\u53f7',
  receive: '\u626b\u7801\u6536\u8d27',
  manual: '\u624b\u52a8\u8f93\u5165',
  receipts: '\u5f85\u6536\u8d27\u5355',
  inv: '\u5e93\u5b58\u626b\u7801\u67e5\u8be2',
  invResult: '\u5e93\u5b58\u7ed3\u679c'
};
function rec(scope, id, title, status, detail) {
  const row = { scope, id, title, status, detail: String(detail || '').slice(0, 500) };
  results.push(row);
  console.log(`[${status}] ${scope}/${id} ${title} :: ${row.detail}`);
}
async function expectText(page, scope, id, title, pattern) {
  const text = await page.locator('body').innerText();
  rec(scope, id, title, pattern.test(text) ? 'PASS' : 'FAIL', text.slice(0, 300).replace(/\s+/g, ' '));
}
async function clickButton(page, name) { return page.getByRole('button', { name }).first().click(); }
(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  const errors = [];
  page.on('pageerror', error => errors.push(String(error)));
  try {
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
    const inputs = page.locator('.login-form input');
    await inputs.nth(1).fill('admin');
    await inputs.nth(2).fill('Sh123456');
    await page.locator('.btn-login').first().click();
    await page.waitForTimeout(2500);
    rec('AUTH', 'login', 'admin login succeeds', page.url().includes('/login') ? 'FAIL' : 'PASS', page.url());
    const unique = `E2E_${Date.now()}`;
    await page.goto(`${BASE}/report-subscriptions`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(800);
    await expectText(page, 'RS', 'page', 'report subscription fields', new RegExp(`${U.report}|${U.create}`));
    await clickButton(page, U.create);
    await page.locator('.el-dialog').waitFor({ state: 'visible' });
    await page.locator('.el-dialog .el-form-item').filter({ hasText: /\u540d\u79f0|\u8ba2\u9605/ }).locator('input').fill(unique);
    await page.locator('.el-dialog .el-form-item').filter({ hasText: /\u6536\u4ef6\u4eba/ }).locator('input').fill('e2e@example.com');
    await clickButton(page, U.save);
    await page.waitForTimeout(1200);
    let text = await page.locator('body').innerText();
    rec('RS', 'create', 'created row visible', text.includes(unique) ? 'PASS' : 'FAIL', unique);
    const row = page.locator('.el-table__row').filter({ hasText: unique }).first();
    rec('RS', 'toggle', 'row is active by default', await row.getByRole('button', { name: U.stop }).count() > 0 ? 'PASS' : 'FAIL', 'stop button visible');
    await row.getByRole('button', { name: U.delete }).click();
    await page.locator('.el-message-box').waitFor({ state: 'visible' });
    await clickButton(page, U.confirm);
    await page.waitForTimeout(1000);
    text = await page.locator('body').innerText();
    rec('RS', 'delete', 'deleted row gone', text.includes(unique) ? 'FAIL' : 'PASS', unique);
    await page.screenshot({ path: path.join(OUT, 'report-subscription.png'), fullPage: true });
    await page.goto(`${BASE}/stocktakes`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(800);
    await expectText(page, 'ST', 'page', 'stocktake fields', new RegExp(U.stock));
    await clickButton(page, U.upload);
    await page.locator('.el-dialog').waitFor({ state: 'visible' });
    const dialogText = await page.locator('.el-dialog').innerText();
    rec('ST', 'dialog', 'upload dialog form visible', /Excel|productId|bookQty|actualQty/.test(dialogText) ? 'PASS' : 'FAIL', dialogText.replace(/\s+/g, ' ').slice(0, 300));
    rec('ST', 'file', 'file input exists', await page.locator('input[type=file]').count() > 0 ? 'PASS' : 'FAIL', 'file input');
    await page.keyboard.press('Escape');
    await page.screenshot({ path: path.join(OUT, 'stocktakes.png'), fullPage: true });
    await page.goto(`${BASE}/profile`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(800);
    await expectText(page, 'PF', 'page', 'profile mfa visible', new RegExp(`${U.profile}|${U.mfa}|${U.enableMfa}`));
    rec('PF', 'button', 'enable mfa enabled', await page.getByRole('button', { name: U.enableMfa }).first().isEnabled() ? 'PASS' : 'FAIL', 'enabled');
    await page.screenshot({ path: path.join(OUT, 'profile.png'), fullPage: true });
    await page.goto(`${BASE}/expiry-alerts`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(800);
    await expectText(page, 'EX', 'page', 'expiry page renders', new RegExp(U.expiry));
    await page.goto(`${BASE}/traceability`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(800);
    await expectText(page, 'TR', 'page', 'traceability page renders', new RegExp(U.trace));
    const mobileContext = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
    const mobile = await mobileContext.newPage();
    await mobile.goto(`${BASE}/mobile/login`, { waitUntil: 'networkidle' });
    const mobileInputs = mobile.locator('.m-form input');
    await mobileInputs.nth(0).fill('');
    await mobileInputs.nth(1).fill('admin');
    await mobileInputs.nth(2).fill('Sh123456');
    await mobile.locator('button[type=submit], .van-button--primary').last().click();
    await mobile.waitForTimeout(2000);
    rec('MOB', 'login', 'mobile login succeeds', mobile.url().includes('/mobile/login') ? 'FAIL' : 'PASS', mobile.url());
    await mobile.goto(`${BASE}/mobile/scan-receive`, { waitUntil: 'networkidle' });
    await mobile.waitForTimeout(1000);
    let mobileText = await mobile.locator('body').innerText();
    rec('MOB', 'receive', 'receive scan labels', new RegExp(`${U.receive}|${U.manual}|${U.receipts}`).test(mobileText) ? 'PASS' : 'FAIL', mobileText.replace(/\s+/g, ' ').slice(0, 300));
    await mobile.goto(`${BASE}/mobile/scan-inventory`, { waitUntil: 'networkidle' });
    await mobile.waitForTimeout(1000);
    mobileText = await mobile.locator('body').innerText();
    rec('MOB', 'inventory', 'inventory scan labels', new RegExp(`${U.inv}|${U.invResult}`).test(mobileText) ? 'PASS' : 'FAIL', mobileText.replace(/\s+/g, ' ').slice(0, 300));
    await mobile.screenshot({ path: path.join(OUT, 'mobile-scan.png'), fullPage: true });
    rec('JS', 'errors', 'no browser errors', errors.length ? 'FAIL' : 'PASS', errors.slice(0, 3).join(' | '));
  } catch (error) {
    rec('FATAL', 'run', 'e2e completes', 'FAIL', error.stack || error.message);
  } finally {
    fs.writeFileSync(path.join(OUT, 'p2-e2e-results.json'), JSON.stringify(results, null, 2), 'utf8');
    await browser.close();
    const fails = results.filter(item => item.status === 'FAIL').length;
    console.log(`\nSUMMARY ${results.length} checks, ${fails} failures`);
    process.exit(fails ? 1 : 0);
  }
})();
