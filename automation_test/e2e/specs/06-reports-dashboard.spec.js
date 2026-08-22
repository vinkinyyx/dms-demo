// Reports and dashboard E2E tests
const { chromium } = require("playwright");
const { loginPC } = require("../helpers/auth");
const { createRunner } = require("../helpers/runner");
const { gotoModule, sleep } = require("../helpers/page");
const config = require("../config");

const reportPages = [
  { route: "/dashboard", label: "PC-dashboard" },
  { route: "/reports", label: "PC-reports-center" },
  { route: "/reports?key=sales-ranking", label: "PC-report-sales-ranking" },
  { route: "/reports?key=product-top10", label: "PC-report-product-top10" },
  { route: "/reports?key=inventory-turnover", label: "PC-report-inventory-turnover" },
  { route: "/reports?key=surgery-stats", label: "PC-report-surgery-stats" },
  { route: "/reports?key=receivables", label: "PC-report-receivables" },
  { route: "/reports?key=order-trace", label: "PC-report-order-trace" },
  { route: "/report-subscriptions", label: "PC-report-subscriptions" },
  { route: "/dealers/profile", label: "PC-dealer-profile" },
];

(async () => {
  const browser = await chromium.launch({ headless: config.HEADLESS });
  const runner = createRunner("06-reports-dashboard");
  const { ctx, page, ok, error } = await loginPC(browser, runner.errorCollector);
  runner.assert("PC login", ok, error || page.url());

  if (ok) {
    for (const p of reportPages) {
      await runner.step(p.label + " load", async () => {
        const res = await gotoModule(page, p.route, p.label);
        runner.assert(p.label + " loads", res.ok, res.error || "content visible");
        await sleep(500);
        const errs = runner.errorCollector.format(p.label);
        runner.assert(p.label + " no errors", !errs, errs || "clean");
      });
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
