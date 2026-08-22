// Surgery reports & promotions E2E tests
const { chromium } = require("playwright");
const { loginPC } = require("../helpers/auth");
const { createRunner } = require("../helpers/runner");
const { testCrudModule } = require("../helpers/crud");
const { gotoModule, sleep } = require("../helpers/page");
const config = require("../config");

(async () => {
  const browser = await chromium.launch({ headless: config.HEADLESS });
  const runner = createRunner("04-surgery-marketing");
  const { ctx, page, ok, error } = await loginPC(browser, runner.errorCollector);
  runner.assert("PC login", ok, error || page.url());

  if (ok) {
    await testCrudModule(page, runner, { moduleKey: "surgery-reports", label: "PC-surgery-reports", expectCreate: true });
    await testCrudModule(page, runner, { moduleKey: "promotions", label: "PC-promotions", expectCreate: true });

    // Mobile surgery report create form
    await runner.step("Mobile surgery create form", async () => {
      const mctx = await browser.newContext({
        viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true,
        userAgent: "Mozilla/5.0 (iPhone) Mobile/15E148", ignoreHTTPSErrors: true,
      });
      const mp = await mctx.newPage();
      runner.errorCollector.attach(mp, "mobile-surgery");
      await mp.goto(config.BASE + "/mobile/login", { waitUntil: "domcontentloaded" });
      await sleep(1500);
      await mp.fill('input[placeholder*="\u8d26\u53f7"], input[placeholder*="\u7528\u6237\u540d"]', config.mobile.username).catch(()=>{});
      await mp.fill('input[type="password"]', config.mobile.password).catch(()=>{});
      await mp.keyboard.press("Enter");
      await mp.waitForURL(u => !u.pathname.includes("/login"), { timeout: 15000 }).catch(()=>{});
      await sleep(3000);
      await mp.goto(config.BASE + "/mobile/surgery-reports/create", { waitUntil: "domcontentloaded" });
      await sleep(2500);
      const uploader = await mp.locator(".van-uploader, input[type=file]").count();
      const formItem = await mp.locator(".van-cell, .van-field, form").count();
      runner.assert("mobile surgery create form renders", formItem > 0 || uploader > 0, "fields=" + formItem + " uploaders=" + uploader);
      const errs = runner.errorCollector.format("mobile-surgery");
      runner.assert("mobile surgery no errors", !errs, errs || "clean");
      await mctx.close();
    });
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
