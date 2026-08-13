const { chromium } = require('D:/Workspace/TRAE/DMS/tools/full-test-20260810/node_modules/playwright');
(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  const responses = [];
  page.on('response', async res => {
    if (res.url().includes('/api/admin/auth/login')) {
      let body = '';
      try { body = await res.text(); } catch {}
      responses.push({ status: res.status(), body });
    }
  });
  await page.goto('http://43.128.145.141:8083/admin/login', { waitUntil: 'networkidle' });
  await page.locator('input').nth(0).fill('admin');
  await page.locator('input').nth(1).fill('Sh123456');
  await page.getByRole('button', { name: '登 录' }).click();
  await page.waitForTimeout(5000);
  console.log(JSON.stringify({ url: page.url(), responses, body: (await page.locator('body').innerText()).slice(0,500) }, null, 2));
  await browser.close();
})();
