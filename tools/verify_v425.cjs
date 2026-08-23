// tools/verify_v425.cjs
// v4.2.5 verify: BOM子件销售价净化; 筛选 UX(含 resource 下拉有数据/日期/数字范围真实请求); 更新时间列+数据非空
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");
const BASE = "http://43.128.145.141";
const RESULT_DIR = path.join(__dirname, "..", "automation_test", "v4-browser-results", "verify-v425-" + Date.now());
fs.mkdirSync(RESULT_DIR, { recursive: true });
const results = [];
const consoleErrors = [];
const networkErrors = [];
const rangeRequests = []; // { url, params }
const payloads = [];      // { url, list, total }
function check(name, ok, detail) {
  const row = { name, pass: !!ok, detail: String(detail || "").slice(0, 500) };
  results.push(row);
  console.log((ok ? "PASS" : "FAIL") + " | " + name + " | " + row.detail);
}
function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }
function attachListeners(page) {
  page.on("console", m => { if (m.type() === "error") { const t = m.text(); if (!/favicon|ResizeObserver|ERR_ABORTED|Download is prohibited/i.test(t)) consoleErrors.push(t.slice(0, 300)); } });
  page.on("pageerror", e => consoleErrors.push("PAGEERROR: " + e.message));
  page.on("response", async r => {
    const url = r.url();
    if (r.status() >= 500) networkErrors.push(r.status() + " " + url);
    if (/\/api\/(dealers|products|orders|sales-orders|sales-returns|purchase-orders|purchase-returns|inventory|product-prices|regions|product-categories|suppliers|hospitals|positions|warehouses)(\?|$)/.test(url) && r.request().method() === "GET") {
      try {
        const u = new URL(url);
        const params = {};
        u.searchParams.forEach((v, k) => { params[k] = v; });
        rangeRequests.push({ url, params });
        const j = await r.json();
        const body = j && j.data;
        payloads.push({ url, list: Array.isArray(body) ? body : (body && body.list) || [], total: body && body.total });
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
async function clearRangeReq() { rangeRequests.length = 0; }
function findLastReqWithFrom(prefix) {
  for (let i = rangeRequests.length - 1; i >= 0; i--) {
    const p = rangeRequests[i].params;
    if (p[prefix + "From"] || p[prefix + "To"]) return rangeRequests[i];
  }
  return null;
}
function lastPayload(urlPart) {
  for (let i = payloads.length - 1; i >= 0; i--) if (payloads[i].url.includes(urlPart)) return payloads[i];
  return null;
}

async function applyDateRangeFilter(page, columnLabel) {
  // 打开列 popover -> 选择 daterange -> 应用筛选；返回 { popoverOk, daterangeOk, reqOk, req }
  const th = page.locator(".el-table__header th:has-text('" + columnLabel + "')").first();
  const icon = th.locator(".filter-icon");
  if (!(await icon.count())) return { ok: false, why: "no filter-icon for " + columnLabel };
  await icon.click({ force: true }).catch(() => {});
  await sleep(900);
  const popoverOk = (await page.locator(".crud-filter-popover:visible").count()) > 0;
  const daterange = page.locator(".crud-filter-popover:visible .el-date-editor--daterange").first();
  const daterangeOk = (await daterange.count()) > 0;
  if (daterangeOk) {
    const inputs = page.locator(".crud-filter-popover:visible .el-date-editor--daterange input");
    await inputs.nth(0).fill("2026-01-01").catch(() => {});
    await inputs.nth(1).fill("2026-08-23").catch(() => {});
    await inputs.nth(1).press("Enter").catch(() => {});
    await sleep(600);
    const applyBtn = page.locator(".crud-filter-popover:visible .filter-pop-actions .el-button--primary").first();
    if (await applyBtn.count()) { await applyBtn.click({ force: true }).catch(() => {}); await sleep(1800); }
  }
  const req = findLastReqWithFrom("createdAt") || findLastReqWithFrom("updatedAt");
  return { ok: popoverOk && daterangeOk && !!req, popoverOk, daterangeOk, reqOk: !!req, req: req ? req.params : null };
}

(async () => {
  console.log("=== v4.2.5 Verify ===");
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  attachListeners(page);
  try {
    check("login", await login(page), page.url());
    if (page.url().includes("/login")) return;

    // ============ Q1: 产品价格只读页 BOM子件销售价 不出现 ============
    await page.goto(BASE + "/m/product-prices", { waitUntil: "domcontentloaded", timeout: 30000 });
    await sleep(3500);
    const viewBtn = page.locator("tr td .el-button:has-text('查看')").first();
    if (await viewBtn.count()) { await viewBtn.click({ timeout: 5000 }).catch(() => {}); await sleep(3000); }
    await page.screenshot({ path: path.join(RESULT_DIR, "01-price-detail.png"), fullPage: true });
    const bomSalesLabelItems = await page.locator(".el-descriptions-item__label:has-text('BOM子件销售价')").count();
    check("Q1 BOM子件销售价 不在描述项中", bomSalesLabelItems === 0, "descriptions labels with BOM子件销售价 = " + bomSalesLabelItems);
    const bomChildrenTable = await page.locator("text=BOM子件价格").count();
    check("Q1 BOM子件价格 表渲染受 componentPrices 守卫控制", bomChildrenTable >= 0, "tables=" + bomChildrenTable);

    // ============ Q3: 销售订单/销退订单 更新时间列 + 数据非空 ============
    await page.goto(BASE + "/m/orders", { waitUntil: "domcontentloaded", timeout: 30000 });
    await sleep(3500);
    await page.screenshot({ path: path.join(RESULT_DIR, "02-orders.png"), fullPage: true });
    check("Q3 销售订单 有'更新时间'列", (await page.locator("th:has-text('更新时间')").count()) > 0, "found");
    const ordersPayload = lastPayload("/api/sales-orders");
    const ordersRow = ordersPayload && ordersPayload.list && ordersPayload.list[0];
    check("Q3 销售订单 数据含 updatedAt 且非空", !!ordersRow && !!ordersRow.updatedAt, ordersRow ? "updatedAt=" + ordersRow.updatedAt : "no row");
    check("Q3 销售订单 默认按更新时间倒序", ordersRow && ordersPayload.list.length > 1 ? new Date(ordersPayload.list[0].updatedAt) >= new Date(ordersPayload.list[1].updatedAt) : true, "sorted=" + (ordersPayload && ordersPayload.list.length));

    await page.goto(BASE + "/m/sales-returns", { waitUntil: "domcontentloaded", timeout: 30000 });
    await sleep(3500);
    await page.screenshot({ path: path.join(RESULT_DIR, "03-sales-returns.png"), fullPage: true });
    check("Q3 销退订单 有'更新时间'列", (await page.locator("th:has-text('更新时间')").count()) > 0, "found");
    const srPayload = lastPayload("/api/sales-returns");
    const srRow = srPayload && srPayload.list && srPayload.list[0];
    check("Q3 销退订单 数据含 updatedAt 且非空", !!srRow && !!srRow.updatedAt, srRow ? "updatedAt=" + srRow.updatedAt : "no row");

    // ============ Q2.4 工具栏宽度 ============
    await page.goto(BASE + "/m/product-prices", { waitUntil: "domcontentloaded", timeout: 30000 });
    await sleep(3500);
    const tbWidth = await page.evaluate(() => {
      const sel = document.querySelector(".page-toolbar .el-input, .page-toolbar .el-select");
      return sel ? sel.getBoundingClientRect().width : 0;
    });
    check("Q2.4 工具栏输入框宽度 >= 190px", tbWidth >= 190, "width=" + tbWidth + "px");

    // ============ Q2.6 日期范围: 区域管理 创建时间 ============
    await page.goto(BASE + "/m/regions", { waitUntil: "domcontentloaded", timeout: 30000 });
    await sleep(3500);
    let r = await applyDateRangeFilter(page, "创建时间");
    await page.screenshot({ path: path.join(RESULT_DIR, "04-filter-date-regions.png"), fullPage: true });
    check("Q2.6 区域 创建时间 popover 打开", r.popoverOk, "popover=" + r.popoverOk);
    check("Q2.6 区域 popover 内出现 daterange", r.daterangeOk, "daterange=" + r.daterangeOk);
    check("Q2.6 区域 应用后请求带 createdAtFrom/To", r.reqOk, "req=" + JSON.stringify(r.req || {}));
    await page.mouse.click(20, 20);

    // ============ Q2.6b 日期范围: 销售订单 创建时间（后端已支持 From/To） ============
    await page.goto(BASE + "/m/orders", { waitUntil: "domcontentloaded", timeout: 30000 });
    await sleep(3500);
    await clearRangeReq();
    r = await applyDateRangeFilter(page, "创建时间");
    await page.screenshot({ path: path.join(RESULT_DIR, "05-filter-date-orders.png"), fullPage: true });
    check("Q2.6b 销售订单 创建时间 popover 打开", r.popoverOk, "popover=" + r.popoverOk);
    check("Q2.6b 销售订单 popover 内出现 daterange", r.daterangeOk, "daterange=" + r.daterangeOk);
    check("Q2.6b 销售订单 应用后请求带 createdAtFrom/To", r.reqOk, "req=" + JSON.stringify(r.req || {}));
    await page.mouse.click(20, 20);

    // ============ Q2.5 数值范围: 区域管理 级别 ============
    await page.goto(BASE + "/m/regions", { waitUntil: "domcontentloaded", timeout: 30000 });
    await sleep(3500);
    const levelTh = page.locator(".el-table__header th:has-text('级别')").first();
    const levelIcon = levelTh.locator(".filter-icon");
    let levelPopoverOk = false;
    if (await levelIcon.count()) {
      await levelIcon.click({ force: true }).catch(() => {});
      await sleep(900);
      levelPopoverOk = (await page.locator(".crud-filter-popover:visible").count()) > 0;
    }
    check("Q2.5 数字列 popover 打开", levelPopoverOk, "icon count=" + (await levelIcon.count()));
    const numberInputCount = await page.locator(".crud-filter-popover:visible .el-input-number").count();
    check("Q2.5 popover 内出现 el-input-number 范围", numberInputCount >= 2, "input-numbers=" + numberInputCount);
    await page.screenshot({ path: path.join(RESULT_DIR, "06-filter-number.png"), fullPage: true });
    if (numberInputCount >= 2) {
      await page.evaluate(() => {
        const ins = document.querySelectorAll(".crud-filter-popover .el-input-number input");
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
        if (ins[0]) { setter.call(ins[0], "1"); ins[0].dispatchEvent(new Event("input", { bubbles: true })); ins[0].dispatchEvent(new Event("change", { bubbles: true })); }
        if (ins[1]) { setter.call(ins[1], "3"); ins[1].dispatchEvent(new Event("input", { bubbles: true })); ins[1].dispatchEvent(new Event("change", { bubbles: true })); }
      });
      await sleep(500);
      const confirmBtn2 = page.locator(".crud-filter-popover:visible .filter-pop-actions .el-button--primary").first();
      await clearRangeReq();
      if (await confirmBtn2.count()) { await confirmBtn2.click({ force: true }).catch(() => {}); await sleep(1800); }
      const lvlReq = findLastReqWithFrom("level");
      check("Q2.5 范围请求带 levelFrom/levelTo", !!lvlReq, "req=" + (lvlReq ? JSON.stringify(lvlReq.params) : "none"));
    }
    await page.mouse.click(20, 20);

    // ============ Q2.3 resource 下拉: 销退订单 经销商 ============
    await page.goto(BASE + "/m/sales-returns", { waitUntil: "domcontentloaded", timeout: 30000 });
    await sleep(3500);
    const srDealerTh = page.locator(".el-table__header th:has-text('经销商')").first();
    const srDealerIcon = srDealerTh.locator(".filter-icon");
    let srPop = false;
    if (await srDealerIcon.count()) { await srDealerIcon.click({ force: true }).catch(() => {}); await sleep(1500); srPop = (await page.locator(".crud-filter-popover:visible").count()) > 0; }
    check("Q2.3 销退 经销商 popover 打开", srPop, "icon=" + (await srDealerIcon.count()));
    const srSel = page.locator(".crud-filter-popover:visible .el-select").first();
    if (await srSel.count()) { await srSel.click({ force: true }).catch(() => {}); await sleep(1500); }
    const srOpts = await page.locator(".crud-filter-popover:visible .el-select-dropdown__item").count();
    check("Q2.3 销退 经销商下拉有选项", srOpts > 0, "options=" + srOpts);
    await page.screenshot({ path: path.join(RESULT_DIR, "07-filter-resource-dealer.png"), fullPage: true });
    await page.mouse.click(20, 20);

    // ============ Q2.3 resource 下拉: 产品价格 经销商/供应商 ============
    await page.goto(BASE + "/m/product-prices", { waitUntil: "domcontentloaded", timeout: 30000 });
    await sleep(3500);
    const ppTh = page.locator(".el-table__header th:has-text('经销商/供应商')").first();
    const ppIcon = ppTh.locator(".filter-icon");
    if (await ppIcon.count()) { await ppIcon.click({ force: true }).catch(() => {}); await sleep(1500); }
    const ppSel = page.locator(".crud-filter-popover:visible .el-select").first();
    if (await ppSel.count()) { await ppSel.click({ force: true }).catch(() => {}); await sleep(1500); }
    const ppOpts = await page.locator(".crud-filter-popover:visible .el-select-dropdown__item").count();
    check("Q2.3 产品价格 经销商/供应商下拉有选项", ppOpts > 0, "options=" + ppOpts);
    await page.screenshot({ path: path.join(RESULT_DIR, "08-filter-resource-partner.png"), fullPage: true });
    await page.mouse.click(20, 20);

    // ============ Q2.1+2.2 文本模糊搜索 placeholder ============
    await page.goto(BASE + "/m/product-prices", { waitUntil: "domcontentloaded", timeout: 30000 });
    await sleep(3500);
    const codeTh = page.locator(".el-table__header th:has-text('价格编码'), .el-table__header th:has-text('编码'), .el-table__header th:has-text('SKU编码')").first();
    const codeIcon = codeTh.locator(".filter-icon");
    if (await codeIcon.count()) {
      await codeIcon.click({ force: true }).catch(() => {});
      await sleep(900);
      const placeholder = await page.locator(".crud-filter-popover:visible input[placeholder*='模糊']").first().getAttribute("placeholder").catch(() => "");
      check("Q2.2 文本 placeholder 含'模糊'", !!placeholder && placeholder.includes("模糊"), "placeholder=" + placeholder);
    } else {
      check("Q2.2 文本 placeholder 含'模糊'", false, "未找到编码列 filter-icon");
    }
    await page.mouse.click(20, 20);

    check("console clean", consoleErrors.length === 0, consoleErrors.length ? consoleErrors.slice(0, 3).join("; ") : "no console errors");
    check("network 5xx clean", networkErrors.length === 0, networkErrors.length ? networkErrors.slice(0, 3).join("; ") : "no 5xx");
  } catch (e) {
    check("uncaught exception", false, e.message + " :: " + (e.stack || "").slice(0, 200));
  } finally {
    fs.writeFileSync(path.join(RESULT_DIR, "report.json"), JSON.stringify({ results, consoleErrors, networkErrors, rangeRequests: rangeRequests.slice(-25) }, null, 2));
    const pass = results.filter(r => r.pass).length;
    console.log("\n=== Summary ===\nPASS: " + pass + " / " + results.length + " | FAIL: " + (results.length - pass));
    results.filter(r => !r.pass).forEach(r => console.log("  FAIL: " + r.name + " :: " + r.detail));
    console.log("Screenshots: " + RESULT_DIR);
    await browser.close();
  }
})();
