// Basic data module E2E tests
const { chromium } = require("playwright");
const { loginPC } = require("../helpers/auth");
const { createRunner } = require("../helpers/runner");
const { testCrudModule } = require("../helpers/crud");
const config = require("../config");

const modules = [
  { moduleKey: "products", label: "PC-products", expectCreate: true },
  { moduleKey: "categories", label: "PC-categories", expectCreate: true },
  { moduleKey: "product-lines", label: "PC-product-lines", expectCreate: true },
  { moduleKey: "product-bundles", label: "PC-product-bundles", expectCreate: true },
  { moduleKey: "dealers", label: "PC-dealers", expectCreate: true },
  { moduleKey: "hospitals", label: "PC-hospitals", expectCreate: true },
  { moduleKey: "warehouses", label: "PC-warehouses", expectCreate: true },
  { moduleKey: "regions", label: "PC-regions", expectCreate: true },
  { moduleKey: "suppliers", label: "PC-suppliers", expectCreate: true },
  { moduleKey: "product-prices", label: "PC-product-prices", expectCreate: false },
];

(async () => {
  const browser = await chromium.launch({ headless: config.HEADLESS });
  const runner = createRunner("01-basic-data");
  const { ctx, page, ok, error } = await loginPC(browser, runner.errorCollector);
  runner.assert("PC login", ok, error || page.url());

  if (ok) {
    for (const m of modules) {
      await testCrudModule(page, runner, m);
    }
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
