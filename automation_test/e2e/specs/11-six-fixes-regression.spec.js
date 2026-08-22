const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const config = require(path.join(__dirname, '..', 'config'));
const { loginPC } = require(path.join(__dirname, '..', 'helpers/auth'));
const sleep = ms => new Promise(r => setTimeout(r, ms));
const outDir = path.join(__dirname, '..', '..', 'v4-browser-results', 'six-fixes-' + Date.now());
fs.mkdirSync(outDir, { recursive: true });
const BASE = config.BASE;

const fail = [];
function ok(name, cond, extra) {
  console.log((cond ? 'PASS ' : 'FAIL ') + name + (extra ? ' ' + extra : ''));
  if (!cond) fail.push(name + (extra ? ' ' + extra : ''));
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const { ctx, page } = await loginPC(browser);
  const consoleErrors = [];
  page.on('console', m => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  page.on('pageerror', e => consoleErrors.push('PAGE:' + e.message));

  // 1) Promotion detail must not show raw numeric IDs for target / gift / cycle
  await page.goto(BASE + '/promotions/19', { waitUntil: 'networkidle' });
  await sleep(1500);
  await page.screenshot({ path: path.join(outDir, '01-promo-detail.png'), fullPage: true });
  const tableText = await page.locator('.rd-lines .el-table').innerText().catch(() => '');
  ok('promo-detail-no-raw-id-13', !/\b13\b/.test(tableText), '');
  ok('promo-detail-no-raw-id-14', !/\b14\b/.test(tableText), '');
  ok('promo-detail-no-EVERY_N-token', !/EVERY_N/.test(tableText), '');
  ok('promo-detail-cycle-label', /仅赠一次|每满N循环/.test(tableText), 'cycle shown as label');

  // 3) Order create: dealer column wider + picker displays full text; click 新建 twice must reset
  await page.goto(BASE + '/order-create/sales', { waitUntil: 'networkidle' });
  await sleep(1000);
  // select dealer
  await page.locator('.el-form-item').filter({ hasText: '经销商' }).locator('input, .resource-picker input, input[readonly]').first().click();
  await sleep(500);
  // choose first dealer in dialog
  const dlg = page.locator('.el-dialog:visible').last();
  await dlg.locator('tbody tr').first().waitFor({ timeout: 8000 });
  await dlg.locator('tbody tr').first().locator('button:has-text("选择"), .el-button--primary').first().click().catch(async () => {
    await dlg.locator('tbody tr').first().dblclick();
  });
  await sleep(800);
  const dealerVal1 = await page.locator('.el-form-item').filter({ hasText: '经销商' }).locator('input').first().inputValue().catch(() => '');
  ok('dealer-selected', dealerVal1.length > 3, JSON.stringify(dealerVal1));
  // measure col span via dealer's el-col class
  const dealerColClass = await page.locator('.el-form-item').filter({ hasText: '经销商' }).locator('xpath=ancestor::div[contains(@class,"el-col-")]').first().getAttribute('class');
  ok('dealer-col-span-12', /el-col-12/.test(dealerColClass || ''), dealerColClass || '');

  // navigate away then back to new (simulate 新建)
  await page.goto(BASE + '/m/orders', { waitUntil: 'networkidle' });
  await sleep(500);
  await page.goto(BASE + '/order-create/sales', { waitUntil: 'networkidle' });
  await sleep(1000);
  const dealerVal2 = await page.locator('.el-form-item').filter({ hasText: '经销商' }).locator('input').first().inputValue().catch(() => 'NO_INPUT');
  // add line button with no dealer must warn
  await page.getByRole('button', { name: '添加行' }).click().catch(() => {});
  await sleep(300);
  const warnedAfterReset = await page.locator('.el-message').innerText().catch(() => '');
  ok('new-button-clears-dealer', !dealerVal2, 'dealer cleared on re-new, got=' + dealerVal2);
  ok('add-line-warns-when-empty', /请先选择经销商/.test(warnedAfterReset), JSON.stringify(warnedAfterReset));
  await page.screenshot({ path: path.join(outDir, '03-order-create-reset.png'), fullPage: true });

  // 5) Sales return source card compact + dialog smaller
  await page.goto(BASE + '/sales-return-edit', { waitUntil: 'networkidle' });
  await sleep(1000);
  const emptyText = await page.locator('.source-empty').innerText().catch(() => '');
  ok('source-empty-compact', /选择发货单/.test(emptyText), emptyText);
  await page.locator('.source-card').getByRole('button', { name: /选择发货单/ }).first().click();
  await sleep(500);
  const dialog = page.locator('.shipment-picker-dialog:visible').last();
  const width = await dialog.evaluate(el => el.offsetWidth);
  ok('source-dialog-width-640', Math.abs(width - 640) <= 20, String(width));
  await page.screenshot({ path: path.join(outDir, '05-return-source.png'), fullPage: true });

  ok('no-console-errors', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '));
  await ctx.close();
  await browser.close();
  if (fail.length) { console.error('\nFAILS:', fail); process.exit(1); }
  console.log('\nALL PASS. Screenshots in', outDir);
})();
