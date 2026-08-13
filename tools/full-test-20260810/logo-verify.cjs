const { chromium } = require('./node_modules/playwright');
const fs = require('fs');
const path = require('path');
const base = 'http://43.128.145.141:8083';
const out = path.resolve(__dirname, '..', '..', 'test-results', 'logo-final-verify');
fs.mkdirSync(out, { recursive: true });
(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 }, ignoreHTTPSErrors: true });
  const errors = [];
  const page = await context.newPage();
  page.on('console', msg => { if (msg.type() === 'error') errors.push(msg.text()); });
  page.on('pageerror', err => errors.push(err.stack || err.message));
  async function shot(name) { await page.screenshot({ path: path.join(out, name), fullPage: true }); }

  await page.goto(base + '/login', { waitUntil: 'domcontentloaded' });
  await page.locator('input.el-input__inner').nth(0).fill('default');
  await page.locator('input.el-input__inner').nth(1).fill('sys_admin');
  await page.locator('input.el-input__inner').nth(2).fill('Dms@123456');
  await shot('01-pc-login.png');
  await page.getByRole('button', { name: '登 录' }).first().click();
  await page.waitForURL('**/home', { timeout: 20000 });
  await page.waitForTimeout(1500);
  await shot('02-pc-home.png');

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(base + '/mobile/login', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(800);
  const mobileInputs = page.locator('input');
  await mobileInputs.nth(0).fill('default');
  await mobileInputs.nth(1).fill('sys_admin');
  await mobileInputs.nth(2).fill('Dms@123456');
  await shot('03-mobile-login.png');
  await page.getByRole('button', { name: '登 录' }).first().click();
  await page.waitForURL('**/mobile/home', { timeout: 20000 });
  await page.waitForTimeout(1500);
  await shot('04-mobile-home.png');

  await page.setViewportSize({ width: 1440, height: 960 });
  await page.goto(base + '/admin/login', { waitUntil: 'domcontentloaded' });
  await page.locator('input.el-input__inner').nth(0).fill('admin');
  await page.locator('input.el-input__inner').nth(1).fill('Sh123456');
  await shot('05-admin-login.png');
  await page.getByRole('button', { name: '登 录' }).first().click();
  await page.waitForURL(url => !url.pathname.includes('/login'), { timeout: 20000 });
  await page.waitForTimeout(1500);
  await shot('06-admin-home.png');

  await browser.close();
  console.log(JSON.stringify({ out, errors: [...new Set(errors)].slice(0, 20) }, null, 2));
})().catch(async err => {
  console.error(err);
  try { await page.screenshot({ path: path.join(out, 'error.png'), fullPage: true }); } catch {}
  process.exit(1);
});
