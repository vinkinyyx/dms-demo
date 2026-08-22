// Targeted verification of 8 DMS bug fixes
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

const BASE = "http://43.128.145.141";
const RESULT_DIR = path.join(__dirname, "..", "automation_test", "v4-browser-results", "verify-" + Date.now());
fs.mkdirSync(RESULT_DIR, { recursive: true });

const results = [];
const consoleErrors = [];
const networkErrors = [];
function check(name, ok, detail) {
  const row = { name, pass: !!ok, detail: String(detail || "").slice(0, 300) };
  results.push(row);
  console.log((ok ? "PASS" : "FAIL") + " | " + name + (detail ? " | " + row.detail : ""));
}
const sleep = (ms) => new Promise(r => setTimeout(r, ms));

function attach(page, label) {
  page.on("console", msg => {
    if (msg.type() === "error") {
      const t = msg.text();
      if (/favicon|ResizeObserver|Download is prohibited|net::ERR_ABORTED/i.test(t)) return;
      consoleErrors.push({ page: label, text: t.slice(0, 300) });
    }
  });
  page.on("pageerror", err => consoleErrors.push({ page: label, text: ("PAGEERROR: " + err.message).slice(0, 300) }));
  page.on("response", res => {
    if (res.status() >= 500) networkErrors.push({ page: label, url: res.url().slice(0, 150), status: res.status() });
    if (res.status() === 404 && !/operation-log/.test(res.url())) networkErrors.push({ page: label, url: res.url().slice(0, 150), status: 404 });
  });
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  attach(page, "main");

  // Login
  await page.goto(BASE + "/login", { waitUntil: "domcontentloaded" });
  await sleep(1500);
  await page.screenshot({ path: path.join(RESULT_DIR, "01-login.png") });
  const tenantInput = page.locator('input[placeholder*="租户"], input[placeholder*="tenant" i]').first();
  if (await tenantInput.count()) { try { await tenantInput.fill("default"); } catch(e){} }
  await page.fill('input[placeholder*="账号"], input[placeholder*="用户名"]', "admin").catch(()=>{});
  await page.fill('input[type="password"]', "Welcomeyyx0616").catch(()=>{});
  await page.screenshot({ path: path.join(RESULT_DIR, "02-login-filled.png") });
  await page.click('button:has-text("登录")').catch(()=>{});
  await sleep(3000);
  await page.screenshot({ path: path.join(RESULT_DIR, "03-after-login.png") });
  check("Login success", !page.url().includes("/login"), "url=" + page.url());

  // Issue 1: global scroll - verify a long page is scrollable
  await page.goto(BASE + "/m/products", { waitUntil: "networkidle" }).catch(()=>{});
  await sleep(1500);
  const scrollBefore = await page.evaluate(() => window.scrollY || document.querySelector(".el-main.main")?.scrollTop || 0);
  await page.evaluate(() => {
    const el = document.querySelector(".el-main.main");
    if (el) el.scrollTop = 400;
    window.scrollTo(0, 400);
  });
  await sleep(500);
  const scrollAfter = await page.evaluate(() => window.scrollY || document.querySelector(".el-main.main")?.scrollTop || 0);
  await page.screenshot({ path: path.join(RESULT_DIR, "04-scroll.png") });
  check("Issue1 global scroll works", scrollAfter > 0, "before=" + scrollBefore + " after=" + scrollAfter);

  // Issue 4: white screen - navigate between sales order menu multiple times
  let whiteScreen = false;
  for (let i = 0; i < 3; i++) {
    await page.goto(BASE + "/m/orders", { waitUntil: "domcontentloaded" }).catch(()=>{});
    await sleep(1200);
    await page.goto(BASE + "/order-create/sales", { waitUntil: "domcontentloaded" }).catch(()=>{});
    await sleep(1500);
    const bodyText = await page.locator("body").innerText().catch(()=>"");
    const hasLoader = await page.locator(".full-screen-loader:visible").count();
    // page should contain order form elements
    const hasForm = await page.locator('text=经销商').first().isVisible().catch(()=>false);
    if (!hasForm && hasLoader) whiteScreen = true;
  }
  await page.screenshot({ path: path.join(RESULT_DIR, "05-sales-order.png") });
  check("Issue4 no white screen on sales order", !whiteScreen, "" );

  // Issue 2: BOM price + Issue 3: form reset
  await page.goto(BASE + "/order-create/sales", { waitUntil: "networkidle" }).catch(()=>{});
  await sleep(1500);
  // select a dealer first
  await page.locator('input[readonly]').first().click().catch(()=>{});
  await sleep(800);
  // pick first dealer in dialog
  const dealerRow = page.locator('.el-dialog .el-table__row').first();
  if (await dealerRow.count()) {
    await dealerRow.click();
    await sleep(800);
  }
  await page.screenshot({ path: path.join(RESULT_DIR, "06-dealer-selected.png") });

  // add line and pick BOM PRD-J001
  await page.click('button:has-text("添加行")').catch(()=>{});
  await sleep(500);
  // click the product picker in the first line
  const productInput = page.locator('.order-create-page .el-table__row').first().locator('input[readonly]').first();
  if (await productInput.count()) {
    await productInput.click();
    await sleep(800);
    // search for PRD-J001
    const searchInput = page.locator('.el-dialog input').first();
    await searchInput.fill("PRD-J001").catch(()=>{});
    await sleep(800);
    const j001Row = page.locator('.el-dialog .el-table__row', { hasText: "PRD-J001" }).first();
    if (await j001Row.count()) {
      await j001Row.click();
      await sleep(2000);
    }
  }
  await page.screenshot({ path: path.join(RESULT_DIR, "07-bom-selected.png") });
  // read parent unit price from table
  const parentPriceText = await page.locator('.order-create-page .el-table__row').first().locator('td').nth(4).innerText().catch(()=>"");
  console.log("Parent unit price cell:", parentPriceText);
  const parentPrice = parseFloat(parentPriceText.replace(/[^\d.]/g, ""));
  check("Issue2 BOM parent price aggregates children (9600)", parentPrice >= 9600, "price=" + parentPriceText);

  // Issue 3: navigate away and back, form should be cleared
  await page.goto(BASE + "/m/products", { waitUntil: "domcontentloaded" }).catch(()=>{});
  await sleep(1000);
  await page.goto(BASE + "/order-create/sales", { waitUntil: "domcontentloaded" }).catch(()=>{});
  await sleep(1500);
  const dealerInputValue = await page.locator('.order-create-page input[readonly]').first().inputValue().catch(()=>"");
  await page.screenshot({ path: path.join(RESULT_DIR, "08-after-reset.png") });
  check("Issue3 form reset clears dealer on new", dealerInputValue === "", "dealer field='" + dealerInputValue + "'");

  // Issue 3b: promotion save (promotions page)
  await page.goto(BASE + "/m/promotions", { waitUntil: "networkidle" }).catch(()=>{});
  await sleep(1500);
  await page.screenshot({ path: path.join(RESULT_DIR, "09-promotions.png") });
  check("Issue promotions page loads", page.url().includes("promotions"), page.url());

  // Issue 5/6/7/8: sales return list and view (first view shouldn't 404)
  await page.goto(BASE + "/m/sales-returns", { waitUntil: "networkidle" }).catch(()=>{});
  await sleep(1500);
  // capture URL before
  const urlBefore = page.url();
  // click first row view button (the first inline action should be 查看/详情)
  const firstViewBtn = page.locator('.el-table__row').first().locator('button:has-text("查看"), button:has-text("详情")').first();
  if (await firstViewBtn.count()) {
    await firstViewBtn.click();
    await sleep(2500);
  }
  await page.screenshot({ path: path.join(RESULT_DIR, "10-sales-return-view.png") });
  const afterViewUrl = page.url();
  const is404 = afterViewUrl.includes("/404") || afterViewUrl.includes("/error/404");
  check("Issue5 first view of sales return no 404", !is404, "url=" + afterViewUrl);

  // Issue 6: picker dialog compact
  // navigate to a new sales return
  await page.goto(BASE + "/sales-return-edit", { waitUntil: "domcontentloaded" }).catch(()=>{});
  await sleep(1500);
  const pickerBtn = page.locator('button:has-text("选择发货单")').first();
  if (await pickerBtn.count()) {
    await pickerBtn.click();
    await sleep(1000);
    const dialogWidth = await page.locator('.el-dialog:visible').first().evaluate(el => el.offsetWidth).catch(()=>0);
    await page.screenshot({ path: path.join(RESULT_DIR, "11-shipment-picker.png") });
    check("Issue6 shipment picker compact (<=860px)", dialogWidth <= 860, "width=" + dialogWidth);
  } else {
    check("Issue6 shipment picker compact", false, "picker button not found");
  }

  console.log("\n=== Console errors ===");
  consoleErrors.forEach(e => console.log(e.page + ": " + e.text));
  console.log("\n=== Network errors (5xx/404) ===");
  networkErrors.forEach(e => console.log(e.page + ": " + e.status + " " + e.url));

  const passed = results.filter(r => r.pass).length;
  console.log(`\n${passed}/${results.length} checks passed`);
  fs.writeFileSync(path.join(RESULT_DIR, "results.json"), JSON.stringify({ results, consoleErrors, networkErrors }, null, 2));
  await browser.close();
  process.exit(passed === results.length ? 0 : 1);
})();
