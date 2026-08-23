// tools/verify_v426.cjs
// v4.2.6 verify:
//   Q1 日期筛选面板 teleported 进 popover，鼠标移入面板不消失
//   Q2 销售订单经销商 resource 下拉选择后请求带 dealerId
//   Q3 销售订单最终金额数字范围筛选，请求带 finalAmountFrom/To
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");
const BASE = "http://43.128.145.141";
const RESULT_DIR = path.join(__dirname, "..", "automation_test", "v4-browser-results", "verify-v426-" + Date.now());
fs.mkdirSync(RESULT_DIR, { recursive: true });
const results = [];
const consoleErrors = [];
const networkErrors = [];
const reqLog = [];
function check(name, ok, detail) {
  results.push({ name, pass: !!ok, detail: String(detail || "").slice(0, 500) });
  console.log((ok ? "PASS" : "FAIL") + " | " + name + " | " + String(detail || "").slice(0, 500));
}
function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }
function attachListeners(page) {
  page.on("console", m => { if (m.type() === "error") { const t = m.text(); if (!/favicon|ResizeObserver|ERR_ABORTED|Download is prohibited/i.test(t)) consoleErrors.push(t.slice(0, 300)); } });
  page.on("pageerror", e => consoleErrors.push("PAGEERROR: " + e.message));
  page.on("response", async r => {
    const url = r.url();
    if (r.status() >= 500) networkErrors.push(r.status() + " " + url);
    if (/\/api\/(sales-orders|sales-returns|orders)(\?|$)/.test(url) && r.request().method() === "GET") {
      try {
        const u = new URL(url);
        const params = {};
        u.searchParams.forEach((v, k) => { params[k] = v; });
        reqLog.push({ url, params });
      } catch (e) {}
    }
  });
}
async function login(page) {
  await page.goto(BASE + "/login", { waitUntil: "domcontentloaded", timeout: 30000 });
  await sleep(1500);
  await page.fill("input[autocomplete='username'], input[placeholder*='账号'], input[placeholder*='用户名']", "admin").catch(() => {});
  await page.fill("input[type='password']", "Sh123456").catch(() => {});
  await page.keyboard.press("Enter");
  await page.waitForURL(u => !u.pathname.includes("/login"), { timeout: 15000 }).catch(() => {});
  await sleep(3000);
  return !page.url().includes("/login");
}
function clearReq() { reqLog.length = 0; }
function lastReqWithParam(key) {
  for (let i = reqLog.length - 1; i >= 0; i--) {
    const p = reqLog[i].params;
    if (p[key] !== undefined && p[key] !== "") return reqLog[i];
  }
  return null;
}
function lastReqWithFrom(prefix) {
  for (let i = reqLog.length - 1; i >= 0; i--) {
    const p = reqLog[i].params;
    if (p[prefix + "From"] !== undefined || p[prefix + "To"] !== undefined) return reqLog[i];
  }
  return null;
}
async function openOrders(page) {
  await page.goto(BASE + "/m/orders", { waitUntil: "domcontentloaded", timeout: 30000 });
  await sleep(3500);
}
(async () => {
  console.log("=== v4.2.6 Verify ===");
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  attachListeners(page);
  try {
    check("login", await login(page), page.url());
    if (page.url().includes("/login")) return;

    // ============ Q1 日期筛选面板不消失 ============
    await openOrders(page);
    const dtTh = page.locator(".el-table__header th:has-text('创建时间')").first();
    const dtIcon = dtTh.locator(".filter-icon");
    let dtIconOk = (await dtIcon.count()) > 0;
    if (dtIconOk) { await dtIcon.click({ force: true }).catch(() => {}); await sleep(900); }
    const popoverOk = (await page.locator(".crud-filter-popover:visible").count()) > 0;
    const daterange = page.locator(".crud-filter-popover:visible .el-date-editor--daterange").first();
    let panelOpened = false;
    if (await daterange.count()) {
      await daterange.click({ force: true }).catch(() => {});
      await sleep(1000);
      panelOpened = (await page.locator(".crud-filter-popover:visible .el-picker-panel, .crud-filter-popover:visible .el-date-range-picker").count()) > 0;
    }
    await page.screenshot({ path: path.join(RESULT_DIR, "01-date-panel-opened.png"), fullPage: false });
    // 鼠标移入日期面板中心，等待，确认不消失
    let panelStill = 0;
    let popoverStill = 0;
    const panelLoc = page.locator(".crud-filter-popover:visible .el-picker-panel, .crud-filter-popover:visible .el-date-range-picker").first();
    const box = await panelLoc.boundingBox().catch(() => null);
    if (box) { await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2); }
    await sleep(1200);
    panelStill = await page.locator(".crud-filter-popover:visible .el-picker-panel, .crud-filter-popover:visible .el-date-range-picker").count();
    popoverStill = await page.locator(".crud-filter-popover:visible").count();
    await page.screenshot({ path: path.join(RESULT_DIR, "02-date-panel-hover-kept.png"), fullPage: false });
    check("Q1 创建时间 列有筛选图标", dtIconOk, "icon=" + dtIconOk);
    check("Q1 点击输入框弹出日期面板（渲染在 popover 内）", panelOpened, "panel=" + panelOpened);
    check("Q1 鼠标移入日期面板后 面板不消失", panelStill > 0, "panelStill=" + panelStill);
    check("Q1 鼠标移入日期面板后 popover 不关闭", popoverStill > 0, "popoverStill=" + popoverStill);
    await page.mouse.click(20, 20).catch(() => {});
    await sleep(300);

    // ============ Q2 销售订单经销商筛选带 dealerId ============
    await openOrders(page);
    const dlTh = page.locator(".el-table__header th:has-text('经销商')").first();
    const dlIcon = dlTh.locator(".filter-icon");
    let dlIconOk = (await dlIcon.count()) > 0;
    if (dlIconOk) { await dlIcon.click({ force: true }).catch(() => {}); await sleep(1500); }
    const dlSel = page.locator(".crud-filter-popover:visible .el-select").first();
    let optCount = 0;
    let pickedText = "";
    if (await dlSel.count()) {
      await dlSel.click({ force: true }).catch(() => {});
      await sleep(1500);
      const opts = page.locator(".crud-filter-popover:visible .el-select-dropdown__item");
      optCount = await opts.count();
      if (optCount > 0) {
        pickedText = ((await opts.first().textContent().catch(() => "")) || "").trim();
        await opts.first().click({ force: true }).catch(() => {});
        await sleep(700);
      }
    }
    await page.screenshot({ path: path.join(RESULT_DIR, "03-dealer-filter.png"), fullPage: false });
    await clearReq();
    const applyBtn = page.locator(".crud-filter-popover:visible .filter-pop-actions .el-button--primary").first();
    if (await applyBtn.count()) { await applyBtn.click({ force: true }).catch(() => {}); await sleep(2000); }
    const dealerReq = lastReqWithParam("dealerId");
    check("Q2 销售订单 经销商筛选有下拉选项", optCount > 0, "options=" + optCount + " picked=" + pickedText);
    check("Q2 应用后请求带 dealerId", !!dealerReq, "req=" + (dealerReq ? JSON.stringify(dealerReq.params) : "none"));
    await page.mouse.click(20, 20).catch(() => {});
    await sleep(300);

    // ============ Q3 销售订单最终金额数字范围 ============
    await openOrders(page);
    const amtTh = page.locator(".el-table__header th:has-text('最终金额')").first();
    const amtIcon = amtTh.locator(".filter-icon");
    let amtIconOk = (await amtIcon.count()) > 0;
    if (amtIconOk) { await amtIcon.click({ force: true }).catch(() => {}); await sleep(900); }
    const numInputs = await page.locator(".crud-filter-popover:visible .el-input-number").count();
    await page.screenshot({ path: path.join(RESULT_DIR, "04-amount-filter.png"), fullPage: false });
    if (numInputs >= 2) {
      await page.evaluate(() => {
        const ins = document.querySelectorAll(".crud-filter-popover .el-input-number input");
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
        if (ins[0]) { setter.call(ins[0], "1"); ins[0].dispatchEvent(new Event("input", { bubbles: true })); ins[0].dispatchEvent(new Event("change", { bubbles: true })); }
        if (ins[1]) { setter.call(ins[1], "99999999"); ins[1].dispatchEvent(new Event("input", { bubbles: true })); ins[1].dispatchEvent(new Event("change", { bubbles: true })); }
      });
      await sleep(600);
      await clearReq();
      const applyBtn2 = page.locator(".crud-filter-popover:visible .filter-pop-actions .el-button--primary").first();
      if (await applyBtn2.count()) { await applyBtn2.click({ force: true }).catch(() => {}); await sleep(2000); }
    }
    const amountReq = lastReqWithFrom("finalAmount");
    check("Q3 最终金额 列有筛选图标", amtIconOk, "icon=" + amtIconOk);
    check("Q3 筛选 popover 内出现数字范围输入框", numInputs >= 2, "input-numbers=" + numInputs);
    check("Q3 应用后请求带 finalAmountFrom/To", !!amountReq, "req=" + (amountReq ? JSON.stringify(amountReq.params) : "none"));
    await page.mouse.click(20, 20).catch(() => {});
    await sleep(300);

    check("console clean", consoleErrors.length === 0, consoleErrors.length ? consoleErrors.slice(0, 3).join("; ") : "no console errors");
    check("network 5xx clean", networkErrors.length === 0, networkErrors.length ? networkErrors.slice(0, 3).join("; ") : "no 5xx");
  } catch (e) {
    check("uncaught exception", false, e.message + " :: " + (e.stack || "").slice(0, 200));
  } finally {
    fs.writeFileSync(path.join(RESULT_DIR, "report.json"), JSON.stringify({ results, consoleErrors, networkErrors, reqLog: reqLog.slice(-30) }, null, 2));
    const pass = results.filter(r => r.pass).length;
    console.log("\n=== Summary ===\nPASS: " + pass + " / " + results.length + " | FAIL: " + (results.length - pass));
    results.filter(r => !r.pass).forEach(r => console.log("  FAIL: " + r.name + " :: " + r.detail));
    console.log("Screenshots: " + RESULT_DIR);
    await browser.close();
  }
})();
