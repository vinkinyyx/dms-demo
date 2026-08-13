const { chromium } = require('D:/Workspace/TRAE/DMS/tools/full-test-20260810/node_modules/playwright');
(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  await page.goto('http://43.128.145.141:8083/admin/login', { waitUntil: 'networkidle' });
  const info = await page.evaluate(() => ({
    inputs: Array.from(document.querySelectorAll('input')).map((el, i) => ({ i, type: el.type, placeholder: el.placeholder, name: el.name, id: el.id, className: el.className })),
    buttons: Array.from(document.querySelectorAll('button')).map((el, i) => ({ i, text: el.innerText, type: el.type, className: el.className }))
  }));
  console.log(JSON.stringify(info, null, 2));
  await browser.close();
})();
