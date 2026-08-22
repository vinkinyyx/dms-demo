// Order business module E2E tests
const { chromium } = require("playwright");
const { loginPC } = require("../helpers/auth");
const { createRunner } = require("../helpers/runner");
const { testCrudModule } = require("../helpers/crud");
const { gotoModule, sleep, clickFirstRowAction, closeDialogs } = require("../helpers/page");
const config = require("../config");

(async () => {
  const browser = await chromium.launch({ headless: config.HEADLESS });
  const runner = createRunner("02-orders");
  const { ctx, page, ok, error } = await loginPC(browser, runner.errorCollector);
  runner.assert("PC login", ok, error || page.url());

  if (ok) {
    // Sales orders - has special wizard create
    await testCrudModule(page, runner, { moduleKey: "orders", label: "PC-sales-orders", skipCreate: true });
    // Try create via dedicated route
    await runner.step("PC-sales-orders - create wizard", async () => {
      await page.goto(config.BASE + "/order-create/sales", { waitUntil: "domcontentloaded" });
      await sleep(3000);
      const form = page.locator(".el-form, .order-create, .wizard").first();
      const hasForm = await form.count();
      runner.assert("sales order create wizard loads", hasForm > 0, hasForm ? "form visible" : "no form found");
      const errs = runner.errorCollector.format("PC-sales-orders-wizard");
      runner.assert("sales order wizard no errors", !errs, errs || "clean");
    });

    await testCrudModule(page, runner, { moduleKey: "sales-returns", label: "PC-sales-returns", expectCreate: false });
    await testCrudModule(page, runner, { moduleKey: "purchase-orders", label: "PC-purchase-orders", skipCreate: true });
    await testCrudModule(page, runner, { moduleKey: "purchase-returns", label: "PC-purchase-returns", expectCreate: false });
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
