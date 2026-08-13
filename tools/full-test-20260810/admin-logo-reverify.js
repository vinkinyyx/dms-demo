const { chromium } = require('D:/Workspace/TRAE/DMS/tools/full-test-20260810/node_modules/playwright');
const fs = require('fs');
const path = require('path');

(async () => {
  const base = 'http://43.128.145.141:8083';
  const outDir = path.resolve('D:/Workspace/TRAE/DMS/test-results/admin-logo-reverify');
  fs.mkdirSync(outDir, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, ignoreHTTPSErrors: true });
  const errors = [];
  page.on('console', msg => { if (msg.type() === 'error') errors.push(msg.text()); });
  page.on('pageerror', err => errors.push(err ? (err.stack || String(err)) : String(err)));

  await page.goto(base + '/admin/', { waitUntil: 'networkidle', timeout: 30000 });
  await page.waitForTimeout(2000);
  await page.screenshot({ path: path.join(outDir, '01-admin-login.png'), fullPage: true });

  const title = await page.title();
  const url = page.url();
  const bodyText = (await page.locator('body').innerText()).slice(0, 1000);
  const inputCount = await page.locator('input').count();
  console.log(JSON.stringify({ step: 'login', title, url, inputCount, bodyText, errors }, null, 2));

  if (inputCount > 0) {
    await page.locator('input').first().fill('admin');
    const inputs = page.locator('input');
    if (await inputs.count() > 1) await inputs.nth(1).fill('Sh123456!');
    await page.getByRole('button', { name: /登录|登 录|Login/i }).first().click().catch(async () => {
      await page.locator('button[type="submit"]').first().click().catch(async () => {
        await page.locator('button').first().click();
      });
    });
    await page.waitForLoadState('networkidle').catch(() => {});
    await page.waitForTimeout(3000);
    await page.screenshot({ path: path.join(outDir, '02-admin-home.png'), fullPage: true });
    console.log(JSON.stringify({ step: 'after-login', title: await page.title(), url: page.url(), bodyText: (await page.locator('body').innerText()).slice(0, 1000), errors }, null, 2));
  }

  await browser.close();
})();
