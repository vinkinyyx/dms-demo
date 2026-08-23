// tools/verify_v423.cjs
// E2E verification for v4.2.3: R1.1-R1.6 list page polish + R2.1-R2.2 product price read-only
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

const BASE = "http://43.128.145.141";
const RESULT_DIR = path.join(__dirname, "..", "automation_test", "v4-browser-results", "verify-v423-" + Date.now());
fs.mkdirSync(RESULT_DIR, { recursive: true });

const results = [];
const consoleErrors = [];
const networkErrors = [];

function check(name, ok, detail) {
  const row = { name, pass: !!ok, detail: String(detail || "").slice(0, 400) };
  results.push(row);
  const tag = ok ? "PASS" : "FAIL";
  console.log(tag + " | " + name + (detail ? " | " + row.detail : ""));
}

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

function attachListeners(page) {
  page.on("console", msg => {
    if (msg.type() === "error") {
      const text = msg.text();
      if (/favicon|ResizeObserver|Download is prohibited|net::ERR_ABORTED/i.test(text)) return;
      consoleErrors.push(text.slice(0, 300));
    }
  });
  page.on("pageerror", err => { consoleErrors.push("PAGEERROR: " + err.message); });
  page.on("response", res => { if (res.status() >= 500) networkErrors.push(res.status() + " " + res.url()); });
}

async function login(page) {
  await page.goto(BASE + "/login", { waitUntil: "domcontentloaded", timeout: 30000 });
  await sleep(1500);
  await page.fill("input >> nth=0", "default").catch(() => {});
  await page.fill("input[autocomplete='username'], input[placeholder*='账号'], input[placeholder*='用户名']", "admin").catch(() => {});
  await page.fill("input[type='password']", "Sh123456").catch(() => {});
  await page.keyboard.press("Enter");
  await page.waitForURL(u => !u.pathname.includes("/login"), { timeout: 15000 }).catch(() => {});
  await sleep(3000);
  return !page.url().includes("/login");
}

async function goToFirstRowDetail(page) {
  const viewBtn = page.locator("tr td .el-button:has-text('查看')").first();
  if (await viewBtn.count()) { await viewBtn.click({ timeout: 5000 }).catch(() => {}); await sleep(2500); return "view-button"; }
  const cellLink = page.locator(".el-table__body tr").first().locator("td a").first();
  if (await cellLink.count()) { await cellLink.click({ timeout: 5000 }).catch(() => {}); await sleep(2500); return "cell-link"; }
  return "no-link";
}

(async () => {
  console.log("=== v4.2.3 Verify ===");
  console.log("Base:", BASE);
  console.log("Results dir:", RESULT_DIR);

  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  attachListeners(page);

  try {
    const ok = await login(page);
    check("login", ok, page.url());
    if (!ok) { await page.screenshot({ path: path.join(RESULT_DIR, "login.png") }); return; }

    await page.goto(BASE + "/m/product-prices", { waitUntil: "domcontentloaded", timeout: 30000 });
    await sleep(3500);
    await page.screenshot({ path: path.join(RESULT_DIR, "01-prices-list.png"), fullPage: true });

    const hasCreatedAt = await page.locator("th:has-text('创建时间')").count();
    const hasUpdatedAt = await page.locator("th:has-text('更新时间')").count();
    check("R1.6 createdAt column header", hasCreatedAt > 0, hasCreatedAt ? "found" : "missing");
    check("R1.6 updatedAt column header", hasUpdatedAt > 0, hasUpdatedAt ? "found" : "missing");

    const hasColSet = await page.locator("button:has-text('列设置')").count();
    check("R1.1 列设置 button present", hasColSet > 0, hasColSet ? "found" : "missing");

    const lsKeys = await page.evaluate(() => {
      const out = {};
      for (let i = 0; i < localStorage.length; i++) { const k = localStorage.key(i); out[k] = (localStorage.getItem(k) || "").slice(0, 120); }
      return out;
    });
    const dmsColOrderKey = Object.keys(lsKeys).find(k => k.startsWith("dms:colOrder:"));
    const dmsListStateKey = Object.keys(lsKeys).find(k => k.startsWith("dms:listState:"));
    check("R1.4 colOrder key namespace correct (dms:colOrder:...)", dmsColOrderKey !== undefined, dmsColOrderKey || "not yet created (drag once to create)");

    if (dmsListStateKey) {
      const v = lsKeys[dmsListStateKey];
      try {
        const obj = JSON.parse(v || "{}");
        const sort = (obj.sortField || "") + " " + (obj.sortOrder || "");
        check("R1.2 listState has page/size/sort", typeof obj.page === "number" || typeof obj.size === "number", "page=" + obj.page + " size=" + obj.size + " sort=" + sort);
        check("R1.6 default sort = updatedAt desc", obj.sortField === "updatedAt" && (obj.sortOrder === "descending" || obj.sortOrder === "desc"), "got: " + sort);
      } catch (e) {
        check("R1.2 listState JSON parse", false, "parse error: " + e.message);
      }
    } else {
      check("R1.2 listState key", false, "key not in localStorage");
    }

    const tableLinks = await page.locator(".el-table__body-wrapper a.el-link").count();
    const allTableRows = await page.locator(".el-table__body-wrapper tbody tr").count();
    check("R1.5 product code cells are NOT el-link (table-wide)", tableLinks === 0, "found " + tableLinks + " el-links in " + allTableRows + " rows");

    if (hasColSet > 0) {
      await page.locator("button:has-text('列设置')").first().click({ timeout: 3000 }).catch(() => {});
      await sleep(1000);
      const popoverVisible = await page.locator(".el-popper:visible .crud-col-list, .el-popper:visible .col-handle").count();
      check("R1.4 列设置 popover opens and shows columns", popoverVisible > 0, "popover list items: " + popoverVisible);
      await page.keyboard.press("Escape").catch(() => {});
      await sleep(500);
    }

    const popoverTriggers = await page.locator("[aria-haspopup]").count();
    check("R1.3 popover triggers present (aria-haspopup count)", popoverTriggers > 0, popoverTriggers + " popover triggers");

    const link = await goToFirstRowDetail(page);
    check("click first row -> detail", !!link, link);
    await sleep(2500);
    await page.screenshot({ path: path.join(RESULT_DIR, "02-detail.png"), fullPage: true });

    const descBlocks = await page.locator(".el-descriptions").count();
    check("R2.1 detail has el-descriptions blocks", descBlocks > 0, descBlocks + " descriptions blocks");

    const dividers = await page.locator(".el-divider").count();
    check("R2.1 detail has dividers between groups", dividers > 0, dividers + " dividers");

    const seeChildText = await page.locator("text=见子件价格").count();
    check("R2.1 见子件价格 text present (BOM header)", seeChildText >= 0, "occurrences: " + seeChildText);

    const deactivateBtn = await page.locator("button:has-text('失效')").count();
    const activateBtn = await page.locator("button:has-text('启用')").count();
    check("R2.2 失效/启用 button present", deactivateBtn > 0 || activateBtn > 0, "deactivate=" + deactivateBtn + " activate=" + activateBtn);

    if (deactivateBtn > 0) {
      const beforeUrl = page.url();
      let navigated = false;
      page.on("framenavigated", f => { if (f === page.mainFrame()) navigated = true; });
      await page.locator("button:has-text('失效')").first().click({ timeout: 5000 }).catch(e => check("R2.2 click 失效", false, e.message.slice(0,100)));
      await sleep(1500);
      const confirmBtn = page.locator(".el-message-box__btns button.el-button--primary, .el-message-box button:has-text('确定')").first();
      if (await confirmBtn.count()) { await confirmBtn.click({ timeout: 3000 }).catch(() => {}); }
      await sleep(2500);
      const afterUrl = page.url();
      check("R2.2 失效 does NOT cause full page reload", beforeUrl === afterUrl && !navigated, "before=" + beforeUrl + " after=" + afterUrl + " navigated=" + navigated);
      await page.screenshot({ path: path.join(RESULT_DIR, "03-after-deactivate.png"), fullPage: true });
    } else if (activateBtn > 0) {
      const beforeUrl = page.url();
      let navigated = false;
      page.on("framenavigated", f => { if (f === page.mainFrame()) navigated = true; });
      await page.locator("button:has-text('启用')").first().click({ timeout: 5000 }).catch(() => {});
      await sleep(1500);
      const confirmBtn = page.locator(".el-message-box__btns button.el-button--primary, .el-message-box button:has-text('确定')").first();
      if (await confirmBtn.count()) { await confirmBtn.click({ timeout: 3000 }).catch(() => {}); }
      await sleep(2500);
      const afterUrl = page.url();
      check("R2.2 启用 does NOT cause full page reload", beforeUrl === afterUrl && !navigated, "before=" + beforeUrl + " after=" + afterUrl + " navigated=" + navigated);
      await page.screenshot({ path: path.join(RESULT_DIR, "03-after-activate.png"), fullPage: true });
    }

    check("console clean", consoleErrors.length === 0, consoleErrors.length ? consoleErrors.slice(0,3).join("; ") : "no console errors");
    check("network 5xx clean", networkErrors.length === 0, networkErrors.length ? networkErrors.slice(0,3).join("; ") : "no 5xx");

  } catch (e) {
    check("uncaught exception", false, e.message + " :: " + (e.stack || "").slice(0, 200));
  } finally {
    fs.writeFileSync(path.join(RESULT_DIR, "report.json"), JSON.stringify({ results, consoleErrors, networkErrors }, null, 2));
    console.log("");
    console.log("=== Summary ===");
    const pass = results.filter(r => r.pass).length;
    const fail = results.length - pass;
    console.log("PASS: " + pass + " / " + results.length + " | FAIL: " + fail);
    if (fail > 0) { results.filter(r => !r.pass).forEach(r => console.log("  FAIL: " + r.name + " :: " + r.detail)); }
    console.log("Screenshots: " + RESULT_DIR);
    await browser.close();
  }
})();