const { chromium } = require('playwright');
const { loginPC } = require('../helpers/auth');
const { createRunner } = require('../helpers/runner');
const { sleep, screenshot } = require('../helpers/page');
const config = require('../config');

function formItem(page, label) {
  return page.locator('.el-form-item').filter({ hasText: label }).first();
}

async function openCreatePrice(page) {
  await page.goto(config.BASE + '/m/product-prices', { waitUntil: 'domcontentloaded' });
  await sleep(2000);
  await page.locator('.page-toolbar button, .crud-toolbar button').filter({ hasText: '新增' }).first().click();
  await page.locator('.el-drawer.open').waitFor({ state: 'visible', timeout: 10000 });
  await sleep(500);
}

async function selectPriceType(page, label) {
  await formItem(page, '价格类型').locator('.el-select').first().click();
  await sleep(300);
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: label }).first().click();
  await sleep(300);
}

async function choosePicker(page, label, searchText, rowText) {
  await formItem(page, label).locator('input').first().click();
  await sleep(500);
  const dialog = page.locator('.el-dialog:visible').last();
  await dialog.locator('input').first().fill(searchText);
  await sleep(700);
  const row = dialog.locator('.el-table__body-wrapper tbody tr').filter({ hasText: rowText }).first();
  await row.waitFor({ state: 'visible', timeout: 10000 });
  await row.click();
  await sleep(800);
}

async function priceInputsVisible(page) {
  const item = formItem(page, '含税价');
  return (await item.count()) > 0 && await item.first().isVisible().catch(() => false);
}

async function componentPriceRows(page) {
  return page.locator('.component-price-panel .el-table__body-wrapper tbody tr');
}

async function findReturnableShipment(page) {
  const token = await page.evaluate(() => localStorage.getItem('dms_access_token'));
  const headers = { Authorization: 'Bearer ' + token };
  const listResp = await page.request.get(config.BASE + '/api/sales-outs?size=20&status=COMPLETED', { headers });
  const list = await listResp.json();
  for (const item of list.data.list || []) {
    const detailResp = await page.request.get(config.BASE + '/api/sales-outs/' + item.id + '/detail', { headers });
    const detail = await detailResp.json();
    const line = (detail.data.lines || []).find(l => (Number(l.shippedQty || l.qty || 0) - Number(l.returnedQty || 0) - Number(l.returnLockedQty || 0)) > 0);
    if (line) {
      return {
        id: item.id,
        code: item.code,
        productCode: line.productCode,
        batchNo: line.batchNo || '',
        dealerName: item.dealerName
      };
    }
  }
  throw new Error('No returnable completed sales-out found');
}

(async () => {
  const browser = await chromium.launch({ headless: config.HEADLESS });
  const runner = createRunner('10-bugfix-pricing-returns');
  const { ctx, page, ok, error } = await loginPC(browser, runner.errorCollector);
  runner.assert('PC login', ok, error || page.url());

  if (ok) {
    await runner.step('Stable product price selection orders and BOM component panel', async () => {
      await openCreatePrice(page);
      await selectPriceType(page, '销售价');
      await choosePicker(page, 'SKU', 'PRD-T001', 'PRD-T001');
      await choosePicker(page, '经销商', 'DLR-001', 'DLR-001');
      await sleep(800);
      const singleVisibleAfterSkuFirst = await priceInputsVisible(page);
      runner.assert('single SKU price input remains editable after SKU first', singleVisibleAfterSkuFirst, page.url());

      await page.locator('.el-drawer.open button').filter({ hasText: '取消' }).first().click();
      await sleep(500);
      await openCreatePrice(page);
      await selectPriceType(page, '销售价');
      await choosePicker(page, '经销商', 'DLR-001', 'DLR-001');
      await choosePicker(page, 'SKU', 'PRD-T001', 'PRD-T001');
      await sleep(800);
      const singleVisibleAfterDealerFirst = await priceInputsVisible(page);
      runner.assert('single SKU price input editable after dealer first', singleVisibleAfterDealerFirst, page.url());

      await page.locator('.el-drawer.open button').filter({ hasText: '取消' }).first().click();
      await sleep(500);
      await openCreatePrice(page);
      await selectPriceType(page, '销售价');
      await choosePicker(page, '经销商', 'DLR-001', 'DLR-001');
      await choosePicker(page, 'SKU', 'PRD-J001', 'PRD-J001');
      await page.locator('.component-price-panel .el-table__body-wrapper tbody tr:first-child').waitFor({ state: 'visible', timeout: 15000 });
      const rows = await componentPriceRows(page);
      const rowCount = await rows.count();
      runner.assert('BOM component prices load for PRD-J001', rowCount >= 3, 'rows=' + rowCount);
      const codes = await rows.locator('td').allInnerTexts().then(cells => cells.join('|'));
      runner.assert('BOM component rows show current seeded components', /PRD-J00[1-4]/.test(codes), codes.slice(0, 300));

      page.locator('.el-drawer.open button').filter({ hasText: '取消' }).first().click();
      const errs = runner.errorCollector.format('pc-pricing');
      runner.assert('pricing flow has no console/JS/500 errors', !errs, errs || 'clean');
      await screenshot(page, 'stable-pricing-selection');
    });

    await runner.step('Sales return new form clears cached shipment between entries', async () => {
      const shipment = await findReturnableShipment(page);
      await page.goto(config.BASE + '/m/sales-returns', { waitUntil: 'domcontentloaded' });
      await sleep(2000);
      await page.locator('button').filter({ hasText: '新增' }).first().click();
      await page.waitForURL('**/sales-return-edit', { timeout: 10000 });
      await sleep(1000);
      const emptyAtStart = await page.locator('.source-empty').count();
      runner.assert('new sales return starts empty', emptyAtStart > 0, 'empty count=' + emptyAtStart);

      await page.locator('button').filter({ hasText: '选择发货单' }).first().click();
      await sleep(800);
      const dialog = page.locator('.el-dialog:visible').last();
      await dialog.locator('.el-form-item').filter({ hasText: '产品' }).locator('input').fill(shipment.productCode);
      await dialog.locator('button').filter({ hasText: '查询' }).first().click();
      await sleep(1200);
      const targetRow = dialog.locator('.el-table__body-wrapper tbody tr').filter({ hasText: shipment.code }).first();
      await targetRow.waitFor({ state: 'visible', timeout: 15000 });
      await targetRow.getByRole('button', { name: '选择' }).click();
      await sleep(1200);
      const sourceText = await page.locator('.source-card').innerText();
      const filled = await page.locator('.lines-card tbody tr').count();
      runner.assert('selecting a current shipment adds return lines', sourceText.includes(shipment.code) && filled > 0, 'rows=' + filled + ', source=' + sourceText.slice(0, 120));

      await page.goto(config.BASE + '/sales-return-edit/1', { waitUntil: 'domcontentloaded' });
      await sleep(800);
      await page.goto(config.BASE + '/sales-return-edit', { waitUntil: 'domcontentloaded' });
      await sleep(1500);
      const sourceAfterReset = await page.locator('.source-card').innerText().catch(() => '');
      const rowsAfterReset = await page.locator('.lines-card tbody tr').count();
      const cacheGone = rowsAfterReset === 0 && sourceAfterReset.includes('选择发货单') && !sourceAfterReset.includes(shipment.code);
      runner.assert('reopening new sales return clears cached shipment/lines', cacheGone, sourceAfterReset.slice(0, 200));

      const errs = runner.errorCollector.format('pc-return-cache');
      runner.assert('return new flow has no console/JS/500 errors', !errs, errs || 'clean');
      await screenshot(page, 'stable-return-cache');
    });
  }

  const s = runner.summary();
  runner.saveReport();
  console.log('\n=== ' + s.suite + ' ===');
  console.log('Total:', s.total, 'Passed:', s.passed, 'Failed:', s.failed);
  if (s.failed > 0) s.failures.forEach(f => console.log('  FAIL:', f.name, '-', f.detail));
  await ctx.close();
  await browser.close();
  process.exit(s.failed > 0 ? 1 : 0);
})();
