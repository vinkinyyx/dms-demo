// Platform admin backend E2E tests
const { chromium } = require("playwright");
const { loginAdmin } = require("../helpers/auth");
const { createRunner } = require("../helpers/runner");
const { gotoModule, sleep, clickFirstRowAction, closeDialogs } = require("../helpers/page");
const config = require("../config");

const adminPages = [
  { path: "/admin/tenants/manufacturers", label: "ADM-manufacturers" },
  { path: "/admin/tenants/dealers", label: "ADM-dealers" },
  { path: "/admin/role-templates", label: "ADM-role-templates" },
  { path: "/admin/menus", label: "ADM-menus" },
  { path: "/admin/dicts", label: "ADM-dicts" },
  { path: "/admin/logs/api", label: "ADM-api-logs" },
  { path: "/admin/logs/audits", label: "ADM-audit-logs" },
];

(async () => {
  const browser = await chromium.launch({ headless: config.HEADLESS });
  const runner = createRunner("08-admin-backend");
  const { ctx, page, ok, error } = await loginAdmin(browser, runner.errorCollector);
  runner.assert("Admin login", ok, error || page.url());

  if (ok) {
    for (const p of adminPages) {
      await runner.step(p.label, async () => {
        const res = await gotoModule(page, p.path, p.label);
        runner.assert(p.label + " loads", res.ok, res.error || "rows=" + res.tableRows);
        await sleep(500);
        const errs = runner.errorCollector.format(p.label);
        runner.assert(p.label + " no errors", !errs, errs || "clean");
        // Click first row action
        const clickRes = await clickFirstRowAction(page);
        if (clickRes.clicked) {
          runner.assert(p.label + " row action works", true, clickRes.opened ? "dialog opened" : "clicked");
          await closeDialogs(page);
        } else {
          runner.assert(p.label + " row action", true, clickRes.reason);
        }
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
