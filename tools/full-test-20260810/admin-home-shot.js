const { chromium } = require('D:/Workspace/TRAE/DMS/tools/full-test-20260810/node_modules/playwright');
const fs = require('fs');
const path = require('path');
(async () => {
  const base = 'http://43.128.145.141:8083';
  const outDir = path.resolve('D:/Workspace/TRAE/DMS/test-results/logo-final-verify-latest');
  fs.mkdirSync(outDir, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, ignoreHTTPSErrors: true });
  const errors = [];
  page.on('console', msg => { if (msg.type() === 'error') errors.push(msg.text()); });
  page.on('pageerror', err => errors.push(err && err.stack ? err.stack : String(err)));

  await page.goto(base + '/admin/login', { waitUntil: 'networkidle' });
  await page.waitForTimeout(800);
  await page.screenshot({ path: path.join(outDir, 'admin-login.png'), fullPage: true });
  await page.locator('input').nth(0).fill('admin');
  await page.locator('input').nth(1).fill('Sh123456');
  await page.getByRole('button', { name: '登 录' }).click();
  await page.waitForURL('**/admin/**', { timeout: 15000 });
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(1200);
  await page.screenshot({ path: path.join(outDir, 'admin-home.png'), fullPage: true });

  const logoCount = await page.locator('.dms-logo, svg').count();
  const bodyText = (await page.locator('body').innerText()).slice(0, 800);
  console.log(JSON.stringify({ url: page.url(), logoCount, errors, bodyText }, null, 2));
  await browser.close();
})();
