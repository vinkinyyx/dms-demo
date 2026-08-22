// Mobile H5 E2E tests
const { chromium } = require("playwright");
const { createRunner } = require("../helpers/runner");
const { gotoModule, sleep } = require("../helpers/page");
const config = require("../config");

const mobilePages = [
  { path: "/mobile/home", label: "MB-home" },
  { path: "/mobile/approvals", label: "MB-approvals" },
  { path: "/mobile/surgery-reports", label: "MB-surgery-reports" },
  { path: "/mobile/surgery-reports/create", label: "MB-surgery-create" },
  { path: "/mobile/messages", label: "MB-messages" },
];

(async () => {
  const browser = await chromium.launch({ headless: config.HEADLESS });
  const runner = createRunner("09-mobile");

  const ctx = await browser.newContext({
    viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true,
    userAgent: "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148",
    ignoreHTTPSErrors: true,
  });
  const page = await ctx.newPage();
  runner.errorCollector.attach(page, "mobile");

  // Login
  await page.goto(config.BASE + "/mobile/login", { waitUntil: "domcontentloaded", timeout: 25000 });
  await sleep(1500);
  await page.fill('input[placeholder*="\u8d26\u53f7"], input[placeholder*="\u7528\u6237\u540d"]', config.mobile.username).catch(()=>{});
  await page.fill('input[type="password"]', config.mobile.password).catch(()=>{});
  await page.keyboard.press("Enter");
  await page.waitForURL(u => !u.pathname.includes("/login"), { timeout: 15000 }).catch(()=>{});
  await sleep(3000);
  runner.assert("Mobile login", !page.url().includes("/login"), page.url());

  if (!page.url().includes("/login")) {
    for (const p of mobilePages) {
      await runner.step(p.label, async () => {
        const res = await gotoModule(page, p.path, p.label);
        runner.assert(p.label + " loads", res.ok, res.error || "content visible");
        await sleep(500);
        const errs = runner.errorCollector.format(p.label);
        runner.assert(p.label + " no errors", !errs, errs || "clean");
      });
    }

    // Uploader on surgery create
    await runner.step("MB surgery uploader", async () => {
      await page.goto(config.BASE + "/mobile/surgery-reports/create", { waitUntil: "domcontentloaded" });
      await sleep(2000);
      const up = await page.locator(".van-uploader, input[type=file]").count();
      runner.assert("mobile surgery uploader present", up > 0, "count=" + up);
    });
  }

  const s = runner.summary();
  runner.saveReport();
  console.log("\n=== " + s.suite + " ===");
  console.log("Total:", s.total, "Passed:", s.passed, "Failed:", s.failed);
  if (s.failed > 0) s.failures.forEach(f => console.log("  FAIL:", f.name, "-", f.detail));
  await browser.close();
  process.exit(s.failed > 0 ? 1 : 0);
})();
