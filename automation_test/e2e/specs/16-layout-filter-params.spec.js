// Regression: platform layout filter keys must be mapped to backend query params.
const { chromium } = require("playwright");
const { loginPC } = require("../helpers/auth");
const { createRunner } = require("../helpers/runner");
const { gotoModule, sleep } = require("../helpers/page");
const config = require("../config");

(async () => {
  const browser = await chromium.launch({ headless: config.HEADLESS });
  const runner = createRunner("16-layout-filter-params");
  const { ctx, page, ok, error } = await loginPC(browser, runner.errorCollector);
  runner.assert("PC login", ok, error || page.url());

  if (ok) {
    await runner.step("orders dealer layout filter", async () => {
      let lastOrderUrl = "";
      page.on("response", res => {
        const url = res.url();
        if (url.includes("/api/sales-orders?")) lastOrderUrl = url;
      });

      const res = await gotoModule(page, "orders", "PC-sales-orders-layout-filter");
      runner.assert("orders page loads", res.ok, res.error || page.url());
      await sleep(1500);

      const dealerSelect = page.locator(".page-toolbar .el-select").nth(1);
      await dealerSelect.click();
      await sleep(500);
      await page.locator(".el-select-dropdown__item:has-text(\"DLR-002\")").last().click();
      await sleep(500);
      await page.locator(".page-toolbar .el-button--primary:has-text(\"查询\")").first().click();
      await sleep(2500);

      runner.assert("request uses dealerId", lastOrderUrl.includes("dealerId=2"), lastOrderUrl);
      runner.assert("request does not use legacy dealer param", !/[?&]dealer=/.test(lastOrderUrl), lastOrderUrl);
      const rowText = await page.locator(".el-table__body-wrapper tbody tr").first().innerText();
      runner.assert("visible rows are DLR-002 dealer", rowText.includes("苏州康宁"), rowText.slice(0, 200));
      const errs = runner.errorCollector.format("PC-sales-orders-layout-filter");
      runner.assert("no console/network errors", !errs, errs || "clean");
    });
  }

  const reportFile = runner.saveReport();
  const s = runner.summary();
  console.log("\n=== " + s.suite + " ===");
  console.log("Total:", s.total, "Passed:", s.passed, "Failed:", s.failed);
  if (s.failed > 0) s.failures.forEach(f => console.log("  FAIL:", f.name, "-", f.detail));
  await ctx.close();
  await browser.close();
  process.exit(s.failed > 0 ? 1 : 0);
})();
