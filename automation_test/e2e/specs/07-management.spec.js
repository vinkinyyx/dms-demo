// Management functions: approvals, permissions, users, logs
const { chromium } = require("playwright");
const { loginPC } = require("../helpers/auth");
const { createRunner } = require("../helpers/runner");
const { testCrudModule } = require("../helpers/crud");
const { gotoModule, sleep } = require("../helpers/page");
const config = require("../config");

(async () => {
  const browser = await chromium.launch({ headless: config.HEADLESS });
  const runner = createRunner("07-management");
  const { ctx, page, ok, error } = await loginPC(browser, runner.errorCollector);
  runner.assert("PC login", ok, error || page.url());

  if (ok) {
    // Approval flow
    await runner.step("approval pages load", async () => {
      for (const r of [
        { path: "/approval/todo", label: "PC-approval-todo" },
        { path: "/approval/templates", label: "PC-approval-templates" },
        { path: "/approval/delegations", label: "PC-approval-delegations" },
        { path: "/approval/admin", label: "PC-approval-admin" },
      ]) {
        const res = await gotoModule(page, r.path, r.label);
        runner.assert(r.label + " loads", res.ok, res.error || "ok");
        const errs = runner.errorCollector.format(r.label);
        runner.assert(r.label + " no errors", !errs, errs || "clean");
      }
    });

    // Users / positions / roles
    await testCrudModule(page, runner, { moduleKey: "users", label: "PC-users", expectCreate: true });
    await testCrudModule(page, runner, { route: "/positions", label: "PC-positions", expectCreate: true });
    await testCrudModule(page, runner, { route: "/roles-manage", label: "PC-roles-manage", expectCreate: true });

    // Tenant page configs
    await runner.step("tenant page configs", async () => {
      const res = await gotoModule(page, "/tenant-page-configs", "PC-tenant-page-configs");
      runner.assert("tenant page configs loads", res.ok, res.error || "ok");
    });

    // Logs
    await testCrudModule(page, runner, { route: "/log-center", label: "PC-log-center", skipCreate: true });
    await testCrudModule(page, runner, { route: "/login-logs", label: "PC-login-logs", skipCreate: true, skipRowClick: true });
    await testCrudModule(page, runner, { route: "/notifications", label: "PC-notifications", skipCreate: true, skipRowClick: true });
    await testCrudModule(page, runner, { route: "/async-tasks", label: "PC-async-tasks", skipCreate: true, skipRowClick: true });

    // Product mappings
    await testCrudModule(page, runner, { route: "/product-mappings", label: "PC-product-mappings", expectCreate: false });
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
