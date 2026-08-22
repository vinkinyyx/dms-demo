// Contracts & authorizations E2E tests
const { chromium } = require("playwright");
const { loginPC } = require("../helpers/auth");
const { createRunner } = require("../helpers/runner");
const { testCrudModule } = require("../helpers/crud");
const { gotoModule, sleep } = require("../helpers/page");
const config = require("../config");

(async () => {
  const browser = await chromium.launch({ headless: config.HEADLESS });
  const runner = createRunner("05-contracts-auth");
  const { ctx, page, ok, error } = await loginPC(browser, runner.errorCollector);
  runner.assert("PC login", ok, error || page.url());

  if (ok) {
    await testCrudModule(page, runner, { route: "/contracts", label: "PC-contracts", expectCreate: false });
    await testCrudModule(page, runner, { route: "/contracts/templates", label: "PC-contract-templates", expectCreate: true });
    await testCrudModule(page, runner, { moduleKey: "authorizations", label: "PC-authorizations", expectCreate: true });
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
