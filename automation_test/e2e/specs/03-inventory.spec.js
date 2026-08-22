// Inventory module E2E tests
const { chromium } = require("playwright");
const { loginPC } = require("../helpers/auth");
const { createRunner } = require("../helpers/runner");
const { testCrudModule } = require("../helpers/crud");
const config = require("../config");

const modules = [
  { moduleKey: "inventory", label: "PC-inventory", skipCreate: true, expectSearch: false },
  { moduleKey: "sales-outs", label: "PC-sales-outs", skipCreate: true },
  { moduleKey: "receipts", label: "PC-receipts", skipCreate: true },
  { moduleKey: "stock-moves", label: "PC-stock-moves", expectCreate: true },
  { moduleKey: "inventory-adjustments", label: "PC-inventory-adjustments", expectCreate: true },
  { route: "/expiry-alerts", label: "PC-expiry-alerts", skipCreate: true, expectSearch: false },
  { route: "/stocktakes", label: "PC-stocktakes", expectCreate: false },
  { route: "/traceability", label: "PC-traceability", skipCreate: true, expectSearch: false },
];

(async () => {
  const browser = await chromium.launch({ headless: config.HEADLESS });
  const runner = createRunner("03-inventory");
  const { ctx, page, ok, error } = await loginPC(browser, runner.errorCollector);
  runner.assert("PC login", ok, error || page.url());

  if (ok) {
    for (const m of modules) {
      await testCrudModule(page, runner, m);
    }
  }

  const s = runner.summary();
  runner.saveReport();
  console.log("\n=== " + s.suite + " ===");
  console.log("Total:", s.total, "Passed:", s.passed, "Failed:", s.failed);
  if (s.failed > 0) s.failures.forEach(f => console.log("  FAIL:", f.name, "-", f.detail));
  await ctx.close();
  await browser.close();
  process.exit(s.failed > 0 ? 1 : 0);
})();
