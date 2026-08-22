// Authentication helpers for PC, Admin, and Mobile
const config = require("../config");

async function fillAndSubmit(page, selectors) {
  for (const [sel, value] of selectors) {
    const loc = page.locator(sel).first();
    if (await loc.count()) {
      try { await loc.fill(value); } catch(e) { await loc.click(); await loc.fill(value); }
    }
  }
  // Find and click login button
  const btn = page.locator('button:has-text("\u767b\u5f55"), button[type="submit"], .login-btn').first();
  if (await btn.count()) {
    await btn.click();
  } else {
    await page.keyboard.press("Enter");
  }
}

async function loginPC(browser, errorCollector) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, ignoreHTTPSErrors: true });
  const page = await ctx.newPage();
  if (errorCollector) errorCollector.attach(page, "pc-login");
  await page.goto(config.BASE + config.pc.loginPath, { waitUntil: "domcontentloaded", timeout: 25000 });
  await page.waitForTimeout(1500);
  // Tenant field may exist
  const tenantInput = page.locator('input[placeholder*="\u79df\u6237"], input[placeholder*="tenant" i]').first();
  if (await tenantInput.count()) { try { await tenantInput.fill(config.pc.tenant); } catch(e) {} }
  await fillAndSubmit(page, [
    ['input[placeholder*="\u8d26\u53f7"], input[placeholder*="\u7528\u6237\u540d"]', config.pc.username],
    ['input[type="password"]', config.pc.password],
  ]);
  await page.waitForURL((u) => !u.pathname.includes("/login"), { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(2500);
  return { ctx, page, ok: !page.url().includes("/login") };
}

async function loginAdmin(browser, errorCollector) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, ignoreHTTPSErrors: true });
  const page = await ctx.newPage();
  if (errorCollector) errorCollector.attach(page, "admin-login");
  const resp = await page.goto(config.BASE + config.admin.loginPath, { waitUntil: "domcontentloaded", timeout: 25000 });
  await page.waitForTimeout(2000);
  if (resp && resp.status() >= 500) {
    return { ctx, page, ok: false, error: "admin frontend HTTP " + resp.status() };
  }
  const inputCount = await page.locator("input").count();
  if (inputCount === 0) {
    return { ctx, page, ok: false, error: "admin login page has no inputs" };
  }
  await fillAndSubmit(page, [
    ['input[placeholder="\u7528\u6237\u540d"]', config.admin.username],
    ['input[type="password"]', config.admin.password],
  ]);
  await page.waitForURL((u) => !u.pathname.includes("/login"), { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(3000);
  return { ctx, page, ok: !page.url().includes("/admin/login") };
}

async function loginMobile(browser, errorCollector) {
  const ctx = await browser.newContext({
    viewport: { width: 390, height: 844 },
    isMobile: true, hasTouch: true,
    userAgent: "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148",
    ignoreHTTPSErrors: true,
  });
  const page = await ctx.newPage();
  if (errorCollector) errorCollector.attach(page, "mobile-login");
  await page.goto(config.BASE + config.mobile.loginPath, { waitUntil: "domcontentloaded", timeout: 25000 });
  await page.waitForTimeout(1500);
  await fillAndSubmit(page, [
    ['input[placeholder*="\u8d26\u53f7"], input[placeholder*="\u7528\u6237\u540d"]', config.mobile.username],
    ['input[type="password"]', config.mobile.password],
  ]);
  await page.waitForURL((u) => !u.pathname.includes("/login"), { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(3000);
  return { ctx, page, ok: !page.url().includes("/mobile/login") };
}

module.exports = { loginPC, loginAdmin, loginMobile };
