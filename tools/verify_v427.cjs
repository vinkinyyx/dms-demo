// tools/verify_v427.cjs
// v4.2.7 verify:
//   Q1 创建时间筛选支持选到时分秒（datetimerange）
//   Q2 选完结束日期后 popover 不再自动消失（受控模式）
//   Q3 主数据页 createdAt datetime 筛选生效（SpecUtil）
//   回归：经销商 resource 筛选 / 最终金额数字范围（v4.2.6 保留）
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");
const BASE = "http://43.128.145.141";
const RESULT_DIR = path.join(__dirname, "..", "automation_test", "v4-browser-results", "verify-v427-" + Date.now());
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
    if (/\/api\/(sales-orders|sales-returns|products)(\?|$)/.test(url) && r.request().method() === "GET") {
      try {
        const u = new URL(url);
        const params = {};
        u.searchParams.forEach((v, k) => { params[k] = v; });
        reqLog.push({ url: u.pathname, params });
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
function closePopover(page) { return page.mouse.click(20, 20).catch(() => {}); }

(async () => {
  console.log("=== v4.2.7 Verify ===");
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  attachListeners(page);
  try {
    check("login", await login(page), page.url());
    if (page.url().includes("/login")) return;

    // ============ Q1+Q2 创建时间 datetime 筛选 ============
    await openOrders(page);
    const dtTh = page.locator(".el-table__header th:has-text('创建时间')").first();
    const dtIcon = dtTh.locator(".filter-icon");
    let dtIconOk = (await dtIcon.count()) > 0;
    if (dtIconOk) { await dtIcon.click({ force: true }).catch(() => {}); await sleep(900); }
    const pop = () => page.locator(".crud-filter-popover:visible");
    const picker = () => pop().locator(".el-date-editor").first();
    let timeSpinners = 0;
    let panelOpened = false;
    if (await picker().count()) {
      await picker().click({ force: true }).catch(() => {});
      await sleep(1100);
      panelOpened = (await pop().locator(".el-picker-panel").count()) > 0;
      timeSpinners = await pop().locator(".el-date-range-picker__time-header, .el-time-spinner, .el-time-panel").count();
    }
    await page.screenshot({ path: path.join(RESULT_DIR, "01-datetime-panel.png"), fullPage: false });
    // 选开始/结束日期
    const cells = pop().locator(".el-picker-panel .el-date-table td.available:not(.prev-month):not(.next-month)");
    const c0 = await cells.nth(0).boundingBox().catch(() => null);
    if (c0) { await page.mouse.click(c0.x + 10, c0.y + 12); await sleep(500); }
    const c1 = await cells.nth(2).boundingBox().catch(() => null);
    if (c1) { await page.mouse.click(c1.x + 10, c1.y + 12); await sleep(500); }
    // datetimerange 可能有确定按钮
    const okBtn = pop().locator(".el-time-panel__btn, .el-date-range-picker__time-header .el-time-panel__btn, .el-picker-panel__footer .el-button--primary").first();
    if (await okBtn.count()) { await okBtn.click({ force: true }).catch(() => {}); await sleep(600); }
    let popAfterEnd = await pop().count();
    let vals = "";
    if (popAfterEnd) { vals = (await picker().locator("input").evaluateAll(ins => ins.map(i => i.value))).join(" | "); }
    await page.screenshot({ path: path.join(RESULT_DIR, "02-after-end-date.png"), fullPage: false });
    // 鼠标移到应用按钮，确认不消失（v4.2.6 的复现路径）
    const applyBtn = pop().locator(".filter-pop-actions .el-button--primary").first();
    const ab = await applyBtn.boundingBox().catch(() => null);
    if (ab) { await page.mouse.move(ab.x + 30, ab.y + 10); await sleep(700); }
    let popAfterMove = await pop().count();
    check("Q1 创建时间 筛选打开 datetime 面板（含时间选择）", panelOpened && timeSpinners > 0, "panel=" + panelOpened + " timeControls=" + timeSpinners);
    check("Q1 输入值带时分秒", /[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}/.test(vals), "vals=" + vals);
    check("Q2 选完结束日期后 popover 仍打开", popAfterEnd > 0, "pop=" + popAfterEnd);
    check("Q2 鼠标移到应用按钮后 popover 不消失", popAfterMove > 0, "pop=" + popAfterMove);
    await clearReq();
    if (await applyBtn.count()) { await applyBtn.click({ force: true }).catch(() => {}); await sleep(2000); }
    const dtReq = lastReqWithParam("createdAtFrom");
    const dtReq2 = lastReqWithParam("createdAtTo");
    check("Q1 应用后请求带 createdAtFrom/To（含时间）", !!dtReq && !!dtReq2 && /[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}/.test(dtReq.params.createdAtFrom || ""), "req=" + JSON.stringify(dtReq ? dtReq.params : null));
    await closePopover(page); await sleep(300);

    // ============ 回归：经销商 resource 筛选 ============
    await openOrders(page);
    const dlTh = page.locator(".el-table__header th:has-text('经销商')").first();
    const dlIcon = dlTh.locator(".filter-icon");
    let dlIconOk = (await dlIcon.count()) > 0;
    if (dlIconOk) { await dlIcon.click({ force: true }).catch(() => {}); await sleep(1500); }
    const dlSel = pop().locator(".el-select").first();
    let optCount = 0, pickedText = "";
    if (await dlSel.count()) {
      await dlSel.click({ force: true }).catch(() => {}); await sleep(1500);
      const opts = pop().locator(".el-select-dropdown__item");
      optCount = await opts.count();
      if (optCount > 0) { pickedText = ((await opts.first().textContent().catch(() => "")) || "").trim(); await opts.first().click({ force: true }).catch(() => {}); await sleep(700); }
    }
    await clearReq();
    const applyBtn2 = pop().locator(".filter-pop-actions .el-button--primary").first();
    if (await applyBtn2.count()) { await applyBtn2.click({ force: true }).catch(() => {}); await sleep(2000); }
    const dealerReq = lastReqWithParam("dealerId");
    check("回归 经销商筛选 下拉选项 + 请求带 dealerId", optCount > 0 && !!dealerReq, "options=" + optCount + " req=" + (dealerReq ? JSON.stringify(dealerReq.params) : "none"));
    await closePopover(page); await sleep(300);

    // ============ 回归：最终金额数字范围 ============
    await openOrders(page);
    const amtTh = page.locator(".el-table__header th:has-text('最终金额')").first();
    const amtIcon = amtTh.locator(".filter-icon");
    let amtIconOk = (await amtIcon.count()) > 0;
    if (amtIconOk) { await amtIcon.click({ force: true }).catch(() => {}); await sleep(900); }
    const numInputs = await pop().locator(".el-input-number").count();
    await page.screenshot({ path: path.join(RESULT_DIR, "03-amount-filter.png"), fullPage: false });
    if (numInputs >= 2) {
      await page.evaluate(() => {
        const ins = document.querySelectorAll(".crud-filter-popover .el-input-number input");
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
        if (ins[0]) { setter.call(ins[0], "1"); ins[0].dispatchEvent(new Event("input", { bubbles: true })); ins[0].dispatchEvent(new Event("change", { bubbles: true })); }
        if (ins[1]) { setter.call(ins[1], "99999999"); ins[1].dispatchEvent(new Event("input", { bubbles: true })); ins[1].dispatchEvent(new Event("change", { bubbles: true })); }
      });
      await sleep(600);
      await clearReq();
      const applyBtn3 = pop().locator(".filter-pop-actions .el-button--primary").first();
      if (await applyBtn3.count()) { await applyBtn3.click({ force: true }).catch(() => {}); await sleep(2000); }
    }
    const amountReq = lastReqWithFrom("finalAmount");
    check("回归 最终金额 数字范围筛选生效", numInputs >= 2 && !!amountReq, "inputs=" + numInputs + " req=" + (amountReq ? JSON.stringify(amountReq.params) : "none"));
    await closePopover(page); await sleep(300);

    // ============ Q3 主数据产品 createdAt datetime 筛选 ============
    await page.goto(BASE + "/m/products", { waitUntil: "domcontentloaded", timeout: 30000 });
    await sleep(3500);
    const pdTh = page.locator(".el-table__header th:has-text('创建时间')").first();
    const pdIcon = pdTh.locator(".filter-icon");
    let pdIconOk = (await pdIcon.count()) > 0;
    if (pdIconOk) { await pdIcon.click({ force: true }).catch(() => {}); await sleep(900); }
    let pdPopOpen = await pop().count();
    const pdPicker = pop().locator(".el-date-editor").first();
    let pdTime = false;
    let pdPanel = false;
    if (pdPopOpen && (await pdPicker.count())) {
      await pdPicker.click({ force: true }).catch(() => {});
      await sleep(1100);
      pdPanel = (await pop().locator(".el-picker-panel").count()) > 0;
      pdTime = (await pop().locator(".el-date-range-picker__time-header, .el-date-range-picker__time-header, .el-time-spinner, .el-time-panel").count()) > 0;
      const pc = pop().locator(".el-picker-panel .el-date-table td.available:not(.prev-month):not(.next-month)");
      const b0 = await pc.nth(0).boundingBox().catch(() => null);
      if (b0) { await page.mouse.click(b0.x + 10, b0.y + 12); await sleep(400); }
      const b1 = await pc.nth(2).boundingBox().catch(() => null);
      if (b1) { await page.mouse.click(b1.x + 10, b1.y + 12); await sleep(400); }
      const okP = pop().locator(".el-time-panel__btn, .el-picker-panel__footer .el-button--primary").first();
      if (await okP.count()) { await okP.click({ force: true }).catch(() => {}); await sleep(500); }
    }
    await page.screenshot({ path: path.join(RESULT_DIR, "04-product-datetime.png"), fullPage: false });
    await clearReq();
    const pApply = pop().locator(".filter-pop-actions .el-button--primary").first();
    if (await pApply.count()) { await pApply.click({ force: true }).catch(() => {}); await sleep(2500); }
    const pdReq = lastReqWithParam("createdAtFrom");
    check("Q3 产品列表 创建时间 datetime 筛选（SpecUtil）", pdIconOk && pdPopOpen && pdPanel && pdTime && !!pdReq, "icon=" + pdIconOk + " pop=" + pdPopOpen + " panel=" + pdPanel + " time=" + pdTime + " req=" + (pdReq ? JSON.stringify(pdReq.params) : "none"));
    await closePopover(page); await sleep(300);

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
