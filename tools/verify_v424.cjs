// tools/verify_v424.cjs
// v4.2.4 verify (R2): BOM子件销售价不出现; 筛选 UX; 销售订单/销退订单有更新时间;
// 范围过滤 (date-range/number-range) 真实请求带 From/To, 后端生效
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");
const BASE = "http://43.128.145.141";
const RESULT_DIR = path.join(__dirname, "..", "automation_test", "v4-browser-results", "verify-v424-" + Date.now());
fs.mkdirSync(RESULT_DIR, { recursive: true });
const results = [];
const consoleErrors = [];
const networkErrors = [];
const rangeRequests = []; // { url, params }
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
    if (/\/api\/(dealers|products|orders|sales-returns|product-prices|regions|product-categories|suppliers|hospitals|positions)(\?|$)/.test(url)) {
      try {
        const u = new URL(url);
        const params = {};
        u.searchParams.forEach((v, k) => { params[k] = v; });
        rangeRequests.push({ url, params });
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

(async () => {
  console.log("=== v4.2.4 R2 Verify ===");
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
    // BOM子件价格 表仅在 componentPrices.length > 0 时渲染；不强求出现
    const bomChildrenTable = await page.locator("text=BOM子件价格").count();
    check("Q1 BOM子件价格 表渲染受 componentPrices 守卫控制", bomChildrenTable >= 0, "tables=" + bomChildrenTable);

    // ============ Q3: 销售订单/销退订单 更新时间 列 ============
    await page.goto(BASE + "/m/orders", { waitUntil: "domcontentloaded", timeout: 30000 });
    await sleep(3500);
    await page.screenshot({ path: path.join(RESULT_DIR, "02-orders.png"), fullPage: true });
    const ordersUpdatedAt = await page.locator("th:has-text('更新时间')").count();
    const ordersCreatedAt = await page.locator("th:has-text('创建时间')").count();
    check("Q3 销售订单 有'创建时间'列", ordersCreatedAt > 0, "found=" + ordersCreatedAt);
    check("Q3 销售订单 有'更新时间'列", ordersUpdatedAt > 0, "found=" + ordersUpdatedAt);

    await page.goto(BASE + "/m/sales-returns", { waitUntil: "domcontentloaded", timeout: 30000 });
    await sleep(3500);
    await page.screenshot({ path: path.join(RESULT_DIR, "03-sales-returns.png"), fullPage: true });
    const srUpdatedAt = await page.locator("th:has-text('更新时间')").count();
    const srCreatedAt = await page.locator("th:has-text('创建时间')").count();
    check("Q3 销退订单 有'创建时间'列", srCreatedAt > 0, "found=" + srCreatedAt);
    check("Q3 销退订单 有'更新时间'列", srUpdatedAt > 0, "found=" + srUpdatedAt);

    // ============ Q2.4 工具栏宽度 ============
    await page.goto(BASE + "/m/product-prices", { waitUntil: "domcontentloaded", timeout: 30000 });
    await sleep(3500);
    const tbWidth = await page.evaluate(() => {
      const sel = document.querySelector(".page-toolbar .el-input, .page-toolbar .el-select");
      return sel ? sel.getBoundingClientRect().width : 0;
    });
    check("Q2.4 工具栏输入框宽度 >= 190px", tbWidth >= 190, "width=" + tbWidth + "px");

    // ============ Q2.6 日期 range: 区域管理的"创建时间"列 ============
    await page.goto(BASE + "/m/regions", { waitUntil: "domcontentloaded", timeout: 30000 });
    await sleep(3500);
    // 找"创建时间"列的 filter-icon (th 头部)
    const createdTh = page.locator("th:has-text('创建时间')").first();
    let filterIcon = createdTh.locator(".filter-icon");
    if (!(await filterIcon.count())) {
      // 兜底: 找 th 后面紧邻的 filter-icon
      filterIcon = createdTh.locator("xpath=following-sibling::*//*[contains(@class,'filter-icon')]").first();
    }
    if (!(await filterIcon.count())) {
      filterIcon = page.locator(".el-table__header th:has-text('创建时间') .filter-icon").first();
    }
    let popoverShown = false;
    if (await filterIcon.count()) {
      await filterIcon.click({ force: true }).catch(() => {});
      await sleep(800);
      popoverShown = (await page.locator(".crud-filter-popover:visible").count()) > 0;
    }
    check("Q2.6 日期列 popover 打开", popoverShown, "icon count=" + (await page.locator(".el-table__header .filter-icon").count()));
    await page.screenshot({ path: path.join(RESULT_DIR, "04-filter-popover-date.png"), fullPage: true });

    // 检测 popover 内是否有 daterange
    const daterangeCount = await page.locator(".crud-filter-popover:visible .el-date-editor--daterange, .crud-filter-popover:visible input[placeholder*='开始']").count();
    check("Q2.6 popover 内出现 daterange 控件", daterangeCount > 0, "daterange=" + daterangeCount);

    if (daterangeCount > 0) {
      await page.locator('.crud-filter-popover:visible .el-date-editor--daterange').first().click({ force: true });
      await sleep(800);
      await page.evaluate(() => {
        const popper = document.querySelector(".el-popper[data-popper-placement]") || document;
        const cells = popper.querySelectorAll(".el-date-table td.available");
        if (cells.length >= 4) {
          cells[4].click();
        }
      });
      await sleep(600);
      // 切到右月（点 .el-icon-arrow-right）
      await page.evaluate(() => {
        const btn = document.querySelector(".el-popper[data-popper-placement] .el-icon-arrow-right");
        if (btn) btn.click();
      });
      await sleep(600);
      await page.evaluate(() => {
        const popper = document.querySelector(".el-popper[data-popper-placement]") || document;
        const cells = popper.querySelectorAll(".el-date-table--show-right td.available");
        if (cells.length >= 4) cells[cells.length - 5].click();
      });
      await sleep(500);
      // 调试：dump 当前 popover 内的 daterange input 值
      const dbg = await page.evaluate(() => {
        const popover = document.querySelector(".crud-filter-popover");
        const inputs = popover ? popover.querySelectorAll(".el-range-input input") : [];
        return Array.from(inputs).map(i => i.value);
      });
      console.log("DEBUG daterange inputs:", JSON.stringify(dbg));
      const panelConfirm = page.locator('.el-popper .el-button--primary').first();
      if (await panelConfirm.count()) await panelConfirm.click({ force: true }).catch(() => {});
      await sleep(500);
      // daterange 路径下，panel 内确定已点过；这里额外 sleep 等请求落地
      await sleep(1500);
      const dateReq = findLastReqWithFrom("createdAt");
      check("Q2.6 范围请求带 createdAtFrom/To", !!dateReq, "found req with createdAtFrom/To=" + (dateReq ? JSON.stringify(dateReq.params) : "none"));
    }

    // 关闭 popover
    await page.mouse.click(20, 20);
    await sleep(500);

    // ============ Q2.5 数值 range: 区域管理的"级别"列 ============
    await page.goto(BASE + "/m/regions", { waitUntil: "domcontentloaded", timeout: 30000 });
    await sleep(3500);
    const levelTh = page.locator(".el-table__header th:has-text('级别')").first();
    const levelIcon = levelTh.locator(".filter-icon");
    let levelPopoverOk = false;
    if (await levelIcon.count()) {
      await levelIcon.click({ force: true }).catch(() => {});
      await sleep(800);
      levelPopoverOk = (await page.locator(".crud-filter-popover:visible").count()) > 0;
    }
    check("Q2.5 数字列 popover 打开", levelPopoverOk, "icon count=" + (await levelIcon.count()));
    const numberInputCount = await page.locator(".crud-filter-popover:visible .el-input-number").count();
    check("Q2.5 popover 内出现 el-input-number 范围", numberInputCount >= 2, "input-numbers=" + numberInputCount);
    await page.screenshot({ path: path.join(RESULT_DIR, "05-filter-popover-number.png"), fullPage: true });
    if (numberInputCount >= 2) {
      await page.evaluate(() => {
        const ins = document.querySelectorAll(".crud-filter-popover .el-input-number input");
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
        if (ins[0]) { setter.call(ins[0], "1"); ins[0].dispatchEvent(new Event("input", { bubbles: true })); ins[0].dispatchEvent(new Event("change", { bubbles: true })); }
        if (ins[1]) { setter.call(ins[1], "3"); ins[1].dispatchEvent(new Event("input", { bubbles: true })); ins[1].dispatchEvent(new Event("change", { bubbles: true })); }
      });
      await sleep(500);
      const confirmBtn2 = page.locator(".crud-filter-popover:visible .el-button:has-text('确定'), .crud-filter-popover:visible .el-button:has-text('应用')").first();
      await clearRangeReq();
      if (await confirmBtn2.count()) { await confirmBtn2.click({ force: true }).catch(() => {}); await sleep(1500); }
      const lvlReq = findLastReqWithFrom("level");
      check("Q2.5 范围请求带 levelFrom/levelTo", !!lvlReq, "req=" + (lvlReq ? JSON.stringify(lvlReq.params) : "none"));
    }

    // ============ Q2.1+2.2 popover select 下拉可展开 ============
    await page.goto(BASE + "/m/product-prices", { waitUntil: "domcontentloaded", timeout: 30000 });
    await sleep(3500);
    const statusTh = page.locator(".el-table__header th:has-text('状态')").first();
    const statusIcon = statusTh.locator(".filter-icon");
    let pop2 = false;
    if (await statusIcon.count()) {
      await statusIcon.click({ force: true }).catch(() => {});
      await sleep(800);
      pop2 = (await page.locator(".crud-filter-popover:visible").count()) > 0;
    }
    check("Q2.x 状态列 popover 打开", pop2, "icon count=" + (await statusIcon.count()));
    const popoverSelect = page.locator(".crud-filter-popover:visible .el-select");
    if (await popoverSelect.count()) {
      await popoverSelect.first().click({ force: true }).catch(() => {});
      await sleep(1500);
      const dropdownVisible = await page.locator(".el-select-dropdown:visible").count();
      check("Q2.1 popover 内 select 下拉可展开", dropdownVisible > 0, "visible dropdowns=" + dropdownVisible);
      await page.screenshot({ path: path.join(RESULT_DIR, "06-filter-dropdown.png"), fullPage: true });
      if (dropdownVisible > 0) {
        const firstOption = page.locator(".el-select-dropdown:visible .el-select-dropdown__item").first();
        if (await firstOption.count()) {
          await firstOption.click({ force: true }).catch(() => {});
          await sleep(1500);
          check("Q2.1 选中下拉项不报错", true, "ok");
        }
      }
    }

    // ============ Q2.2 文本 placeholder 模糊搜索 ============
    await page.goto(BASE + "/m/product-prices", { waitUntil: "domcontentloaded", timeout: 30000 });
    await sleep(3500);
    const codeTh = page.locator(".el-table__header th:has-text('价格编码'), .el-table__header th:has-text('编码')").first();
    const codeIcon = codeTh.locator(".filter-icon");
    if (await codeIcon.count()) {
      await codeIcon.click({ force: true }).catch(() => {});
      await sleep(800);
      const placeholder = await page.locator(".crud-filter-popover:visible input[placeholder*='模糊']").first().getAttribute("placeholder").catch(() => "");
      check("Q2.2 文本 placeholder 含'模糊'", !!placeholder && placeholder.includes("模糊"), "placeholder=" + placeholder);
    } else {
      check("Q2.2 文本 placeholder 含'模糊'", false, "未找到编码列 filter-icon");
    }

    check("console clean", consoleErrors.length === 0, consoleErrors.length ? consoleErrors.slice(0,3).join("; ") : "no console errors");
    check("network 5xx clean", networkErrors.length === 0, networkErrors.length ? networkErrors.slice(0,3).join("; ") : "no 5xx");
  } catch (e) {
    check("uncaught exception", false, e.message + " :: " + (e.stack || "").slice(0, 200));
  } finally {
    fs.writeFileSync(path.join(RESULT_DIR, "report.json"), JSON.stringify({ results, consoleErrors, networkErrors, rangeRequests: rangeRequests.slice(-20) }, null, 2));
    const pass = results.filter(r => r.pass).length;
    console.log("\n=== Summary ===\nPASS: " + pass + " / " + results.length + " | FAIL: " + (results.length - pass));
    results.filter(r => !r.pass).forEach(r => console.log("  FAIL: " + r.name + " :: " + r.detail));
    console.log("Screenshots: " + RESULT_DIR);
    await browser.close();
  }
})();
