// tools/smoke-test.cjs
// DMS Deep Smoke Test - actually clicks buttons and checks for errors
// Usage: node tools/smoke-test.cjs [--base=http://43.128.145.141] [--module=products] [--headed]
// Env: E2E_BASE, E2E_USER, E2E_PASS, E2E_TENANT

const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

const args = process.argv.slice(2);
function arg(name, def) {
  const hit = args.find(a => a.startsWith("--" + name + "="));
  return hit ? hit.split("=").slice(1).join("=") : def;
}
const BASE = (arg("base") || process.env.E2E_BASE || "http://43.128.145.141").replace(/\/$/, "");
const ONLY_MODULE = arg("module", "");
const HEADLESS = !args.includes("--headed");
// 分段执行：--target=pc|admin|mobile|all（默认 all；分段避免总超时只跑得到 PC）
const TARGET = (arg("target", "all") || "all").toLowerCase();
const RUN_PC = TARGET === "all" || TARGET === "pc";
const RUN_ADMIN = TARGET === "all" || TARGET === "admin";
const RUN_MOBILE = TARGET === "all" || TARGET === "mobile";

const RESULT_DIR = path.join(__dirname, "..", "automation_test", "v4-browser-results", "smoke-" + Date.now());
fs.mkdirSync(RESULT_DIR, { recursive: true });

const results = [];
const allConsoleErrors = [];
const allNetworkErrors = [];

function check(name, ok, detail) {
  const row = { name, pass: !!ok, detail: String(detail || "").slice(0, 250) };
  results.push(row);
  console.log((ok ? "PASS" : "FAIL") + " | " + name + (detail ? " | " + row.detail : ""));
}

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }
function withTimeout(promise, ms, label) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("step timeout " + ms + "ms: " + label)), ms);
    promise.then(v => { clearTimeout(timer); resolve(v); }, e => { clearTimeout(timer); reject(e); });
  });
}

function attachListeners(page, label) {
  page.on("console", msg => {
    if (msg.type() === "error") {
      const text = msg.text();
      if (/favicon|ResizeObserver|Download is prohibited|net::ERR_ABORTED/i.test(text)) return;
      allConsoleErrors.push({ page: label, text: text.slice(0, 300) });
    }
  });
  page.on("pageerror", err => {
    allConsoleErrors.push({ page: label, text: ("PAGEERROR: " + err.message).slice(0, 300) });
  });
  page.on("requestfailed", req => {
    allNetworkErrors.push({ page: label, url: req.url().slice(0, 150), err: (req.failure() || {}).errorText || "" });
  });
  page.on("response", res => {
    if (res.status() >= 500) {
      allNetworkErrors.push({ page: label, url: res.url().slice(0, 150), status: res.status() });
    }
  });
}

function errSummary(label) {
  return {
    consoleErrors: allConsoleErrors.filter(e => e.page === label),
    serverErrors: allNetworkErrors.filter(e => e.page === label && e.status >= 500),
  };
}

// ---------- login ----------
async function loginPC(page) {
  await page.goto(BASE + "/login", { waitUntil: "domcontentloaded", timeout: 25000 });
  await sleep(1000);
  const tenantInput = page.locator('input[placeholder*="\u79df\u6237"], input[placeholder*="tenant" i]').first();
  if (await tenantInput.count()) { try { await tenantInput.fill("default"); } catch(e) {} }
  await page.fill('input[placeholder*="\u8d26\u53f7"], input[placeholder*="\u7528\u6237\u540d"]', "admin").catch(()=>{});
  await page.fill('input[type="password"]', "Sh123456").catch(()=>{});
  await page.keyboard.press("Enter");
  await page.waitForURL(u => !u.pathname.includes("/login"), { timeout: 15000 }).catch(()=>{});
  await sleep(2500);
  return !page.url().includes("/login");
}

async function loginAdmin(page) {
  const resp = await page.goto(BASE + "/admin/login", { waitUntil: "domcontentloaded", timeout: 25000 });
  await sleep(1500);
  if (resp && resp.status() >= 500) {
    check("Admin login", false, "admin frontend HTTP " + resp.status() + " (server error, check nginx/container)");
    return false;
  }
  const inputCount = await page.locator("input").count();
  if (inputCount === 0) {
    check("Admin login", false, "no input fields rendered (frontend not loading)");
    return false;
  }
  const userInput = page.locator('input[placeholder="\u7528\u6237\u540d"]').first();
  if (await userInput.count()) { await userInput.fill("admin"); }
  const passInput = page.locator('input[type="password"]').first();
  if (await passInput.count()) { await passInput.fill("Sh123456"); }
  const loginBtn = page.locator('button:has-text("\u767b\u5f55"), button[type="submit"]').first();
  if (await loginBtn.count()) { await loginBtn.click(); }
  else { await page.keyboard.press("Enter"); }
  await page.waitForURL(u => !u.pathname.includes("/login"), { timeout: 15000 }).catch(()=>{});
  await sleep(3000);
  const ok = !page.url().includes("/admin/login");
  if (!ok) check("Admin login", false, "stayed on login page (wrong credentials or API error)");
  return ok;
}

async function loginMobile(page) {
  await page.goto(BASE + "/mobile/login", { waitUntil: "domcontentloaded", timeout: 25000 });
  await sleep(1000);
  await page.fill('input[placeholder*="\u8d26\u53f7"], input[placeholder*="\u7528\u6237\u540d"]', "admin").catch(()=>{});
  await page.fill('input[type="password"]', "Sh123456").catch(()=>{});
  await page.keyboard.press("Enter");
  await page.waitForURL(u => !u.pathname.includes("/login"), { timeout: 15000 }).catch(()=>{});
  await sleep(3000);
  return !page.url().includes("/mobile/login");
}

// ---------- per page step ----------
async function processOnePage(page, p) {
  const ok = await visitPage(page, p.path, p.label);
  if (ok) {
    await clickFirstRowAction(page, p.label);
    // 重新访问路径以清掉 row action 残留的 drawer / overlay / 路由状态，
    // 避免影响 clickCreateButton 的"新增"按钮 click 与表单弹窗可见性判断。
    await page.goto(BASE + p.path, { waitUntil: "domcontentloaded", timeout: 25000 }).catch(()=>{});
    await sleep(800);
    await clickCreateButton(page, p.label);
  }
}

// ---------- visit page ----------
async function visitPage(page, routePath, label) {
  attachListeners(page, label);
  try {
    await page.goto(BASE + routePath, { waitUntil: "domcontentloaded", timeout: 25000 });
  } catch(e) {
    check(label + " - load", false, "navigation error: " + e.message.slice(0,100));
    return false;
  }
  await sleep(2500);
  try { await page.waitForSelector(".el-table,.el-card,.el-empty,.el-pagination,.van-cell,.van-empty", { timeout: 8000 }); } catch(e) {}

  const safeName = label.replace(/[^a-z0-9_-]/gi, "_").slice(0, 60);
  await page.screenshot({ path: path.join(RESULT_DIR, safeName + ".png"), fullPage: false }).catch(()=>{});

  // Collect visible text from multiple possible content containers; pick the one with the most text
  let bodyText = "";
  const contentSelectors = [".el-main", "main", ".page-content", ".app-main", ".layout-content", ".el-card", "body"];
  let bestLen = 0;
  for (const cs of contentSelectors) {
    const loc = page.locator(cs).first();
    if (await loc.count()) {
      try {
        const t = await loc.innerText();
        if (t && t.replace(/\s/g, "").length > bestLen) {
          bodyText = t;
          bestLen = t.replace(/\s/g, "").length;
        }
      } catch(e) {}
    }
  }
  // Also count table rows as a content signal (some pages have minimal text but a data table)
  const tableRows = await page.locator(".el-table__body-wrapper tbody tr").count().catch(()=>0);
  const noRoute = /404|\u9875\u9762\u4e0d\u5b58\u5728|\u627e\u4e0d\u5230\u8be5\u9875\u9762|Not Found/.test(bodyText);
  const hasContent = bestLen > 10 || tableRows > 0;

  check(label + " - loads", !noRoute && hasContent, noRoute ? "404/not found" : (hasContent ? "content visible" : "blank"));

  const errs = errSummary(label);
  check(label + " - console", errs.consoleErrors.length === 0, errs.consoleErrors.length ? errs.consoleErrors.map(e=>e.text).join("; ").slice(0,200) : "clean");
  check(label + " - network", errs.serverErrors.length === 0, errs.serverErrors.length ? errs.serverErrors.map(e=>e.status+" "+e.url).join("; ").slice(0,200) : "no 5xx");

  return !noRoute && hasContent;
}

// ---------- click first row action ----------
async function clickFirstRowAction(page, label) {
  const clickLabel = label + "-row";
  attachListeners(page, clickLabel);
  await sleep(800);
  const rows = page.locator(".el-table__body-wrapper tbody tr");
  const rowCount = await rows.count();
  if (rowCount === 0) { check(label + " - row action", true, "no rows"); return; }

  const firstRow = rows.first();
  const selectors = [
    ".el-table__fixed-right .el-button",
    ".el-table__fixed-right .el-link",
    "td:last-child .el-button",
    "td:last-child .el-link",
    ".el-button--text",
  ];

  let clicked = false;
  for (const sel of selectors) {
    const btn = firstRow.locator(sel).first();
    if (await btn.count()) {
      try { await btn.click({ timeout: 5000 }); clicked = true; break; } catch(e) { continue; }
    }
  }

  if (!clicked) { check(label + " - row action", true, "no clickable action"); return; }
  await sleep(2000);

  const dialog = page.locator(".el-dialog:visible, .el-drawer:visible, .el-message-box:visible").first();
  const detail = page.locator(".el-descriptions:visible, .detail-content:visible").first();
  const opened = (await dialog.count()) > 0 || (await detail.count()) > 0;

  const errs = errSummary(clickLabel);
  check(label + " - row action", true, opened ? "dialog/detail opened" : "clicked");
  if (errs.consoleErrors.length) check(label + " - row console", false, errs.consoleErrors.map(e=>e.text).join("; ").slice(0,200));
  if (errs.serverErrors.length) check(label + " - row network", false, errs.serverErrors.map(e=>e.status+" "+e.url).join("; ").slice(0,200));

  // close dialogs
  await forceCloseOverlays(page);
}

async function forceCloseOverlays(page) {
  // 同时兼容 element-plus 新版（el-overlay 容器下挂 dialog/drawer/message-box）
  // 和旧版（直接挂 __wrapper），以及 vant。
  const oldWrappers = ".el-dialog__wrapper:visible, .el-drawer__wrapper:visible, .el-message-box__wrapper:visible, .el-overlay-dialog:visible";
  const newPanels    = ".el-overlay:visible .el-dialog, .el-overlay:visible .el-drawer, .el-overlay:visible .el-message-box";
  const vantPanels   = ".van-popup:visible, .van-dialog:visible, .van-action-sheet:visible";
  const closeBtnSel  = ".el-dialog__headerbtn, .el-drawer__close-btn, .el-message-box__headerbtn, .van-popup__close-icon, .van-dialog__close-icon, .van-action-sheet__close-icon";
  try {
    for (let i = 0; i < 8; i++) {
      const sels = [oldWrappers, newPanels, vantPanels].join(",");
      const panels = page.locator(sels);
      const cnt = await panels.count();
      if (cnt === 0) break;
      let closed = false;
      const closeBtn = panels.first().locator(closeBtnSel).first();
      if (await closeBtn.count()) {
        try { await closeBtn.click({ timeout: 2000, force: true }); closed = true; } catch(e) {}
      }
      if (!closed) {
        // 兜底：直接点 overlay 蒙层点击关闭（点击非 panel 区域）
        try { await page.mouse.click(8, 8); } catch(e) {}
      }
      if (!closed) {
        try { await page.keyboard.press("Escape"); } catch(e) {}
      }
      await sleep(350);
    }
  } catch(e) {}
}

// ---------- click create button ----------
async function clickCreateButton(page, label) {
  const createLabel = label + "-create";
  attachListeners(page, createLabel);
  await sleep(500);

  // 1) 先尝试在工具区容器内按文本定位（更稳，不混入表格行内按钮）
  const toolbarSelectors = [".el-card .toolbar", ".toolbar", ".crud-toolbar", ".list-toolbar", ".page-header", ".el-card"];
  let createBtn = null;
  const createRe = /\u65b0\u589e|\u65b0\u5efa|\u6dfb\u52a0|\u521b\u5efa|\u521b\u5efa\u6a21\u677f|\u65b0\u5efa\u5ba1\u6279\u6d41|\u65b0\u5efa\u9001\u8d27\u5355|\u65b0\u5efa\u8ba2\u5355|\u65b0\u5efa\u91c7\u8d2d|\u65b0\u5efa\u4ea7\u54c1|\u65b0\u5efa\u5ba2\u6237|\u65b0\u5efa\u4f9b\u5e94\u5546|Create|Add/;
  for (const containerSel of toolbarSelectors) {
    const containers = page.locator(containerSel);
    const cc = await containers.count();
    for (let k = 0; k < cc; k++) {
      const container = containers.nth(k);
      if (!(await container.isVisible().catch(()=>false))) continue;
      const btns = container.locator("button.el-button--primary, button.el-button--success, button.el-button");
      const cnt = await btns.count();
      for (let i = 0; i < cnt; i++) {
        const b = btns.nth(i);
        const text = (await b.innerText().catch(()=>"")).trim();
        // 必须是工具区按钮（不在表格行内、文本里含"新/创/添"等）
        if (createRe.test(text) && !text.includes("\u66f4\u591a")) { createBtn = b; break; }
      }
      if (createBtn) break;
    }
    if (createBtn) break;
  }

  // 2) Fallback：在整个页面（除表格行）按文本找
  if (!createBtn) {
    const candidates = page.locator("button:visible, a:visible");
    const cnt = await candidates.count();
    for (let i = 0; i < cnt; i++) {
      const b = candidates.nth(i);
      // 排除表格行内
      const inRow = await b.evaluate(el => !!el.closest(".el-table__body-wrapper")).catch(()=>false);
      if (inRow) continue;
      const text = (await b.innerText().catch(()=>"")).trim();
      if (createRe.test(text) && !text.includes("\u66f4\u591a")) { createBtn = b; break; }
    }
  }

  if (!createBtn) { check(label + " - create btn", true, "no create button"); return; }
  let clickOk = false;
  try { await withTimeout(createBtn.click({ timeout: 5000, force: true }), 8000, label + " create-click"); clickOk = true; } catch(e) { check(label + " - create btn", false, e.message.slice(0,120)); }
  if (!clickOk) return;
  await sleep(1500);

  const dlg = page.locator(".el-dialog:visible, .el-drawer:visible").first();
  const form = page.locator(".el-form:visible").first();
  const opened = (await dlg.count()) > 0 || (await form.count()) > 0;
  check(label + " - create btn", opened, opened ? "form opened" : "clicked no dialog");

  const errs = errSummary(createLabel);
  if (errs.consoleErrors.length) check(label + " - create console", false, errs.consoleErrors.map(e=>e.text).join("; ").slice(0,150));

  await page.keyboard.press("Escape").catch(()=>{});
  const closeBtn = page.locator(".el-dialog__headerbtn:visible, .el-drawer__close-btn:visible").first();
  if (await closeBtn.count()) { await closeBtn.click().catch(()=>{}); }
  await sleep(500);
}

// ---------- page definitions ----------
const PC_PAGES = [
  { path: "/home", label: "PC-home" },
  { path: "/dashboard", label: "PC-dashboard" },
  { path: "/m/products", label: "PC-products" },
  { path: "/m/categories", label: "PC-categories" },
  { path: "/m/product-lines", label: "PC-product-lines" },
  { path: "/m/product-bundles", label: "PC-product-bundles" },
  { path: "/m/dealers", label: "PC-dealers" },
  { path: "/m/hospitals", label: "PC-hospitals" },
  { path: "/m/warehouses", label: "PC-warehouses" },
  { path: "/m/regions", label: "PC-regions" },
  { path: "/m/suppliers", label: "PC-suppliers" },
  { path: "/m/product-prices", label: "PC-product-prices" },
  { path: "/contracts", label: "PC-contracts" },
  { path: "/contracts/templates", label: "PC-contract-templates" },
  { path: "/m/authorizations", label: "PC-authorizations" },
  { path: "/m/orders", label: "PC-orders" },
  { path: "/m/sales-returns", label: "PC-sales-returns" },
  { path: "/m/purchase-orders", label: "PC-purchase-orders" },
  { path: "/m/purchase-returns", label: "PC-purchase-returns" },
  { path: "/m/inventory", label: "PC-inventory" },
  { path: "/m/sales-outs", label: "PC-sales-outs" },
  { path: "/m/receipts", label: "PC-receipts" },
  { path: "/m/stock-moves", label: "PC-stock-moves" },
  { path: "/m/inventory-adjustments", label: "PC-inventory-adjustments" },
  { path: "/expiry-alerts", label: "PC-expiry-alerts" },
  { path: "/stocktakes", label: "PC-stocktakes" },
  { path: "/traceability", label: "PC-traceability" },
  { path: "/m/surgery-reports", label: "PC-surgery-reports" },
  { path: "/m/promotions", label: "PC-promotions" },
  { path: "/reports", label: "PC-reports" },
  { path: "/dealers/profile", label: "PC-dealer-profile" },
  { path: "/product-mappings", label: "PC-product-mappings" },
  { path: "/approval/todo", label: "PC-approval-todo" },
  { path: "/approval/templates", label: "PC-approval-templates" },
  { path: "/approval/delegations", label: "PC-approval-delegations" },
  { path: "/approval/admin", label: "PC-approval-admin" },
  { path: "/positions", label: "PC-positions" },
  { path: "/m/users", label: "PC-users" },
  { path: "/roles-manage", label: "PC-roles-manage" },
  { path: "/tenant-page-configs", label: "PC-tenant-page-configs" },
  { path: "/log-center", label: "PC-log-center" },
  { path: "/notifications", label: "PC-notifications" },
  { path: "/login-logs", label: "PC-login-logs" },
  { path: "/async-tasks", label: "PC-async-tasks" },
];

const ADMIN_PAGES = [
  { path: "/admin/tenants/manufacturers", label: "ADM-manufacturers" },
  { path: "/admin/tenants/dealers", label: "ADM-dealers" },
  { path: "/admin/role-templates", label: "ADM-role-templates" },
  { path: "/admin/menus", label: "ADM-menus" },
  { path: "/admin/dicts", label: "ADM-dicts" },
  { path: "/admin/logs/api", label: "ADM-api-logs" },
  { path: "/admin/logs/audits", label: "ADM-audit-logs" },
];

const MOBILE_PAGES = [
  { path: "/mobile/home", label: "MB-home" },
  { path: "/mobile/approvals", label: "MB-approvals" },
  { path: "/mobile/surgery-reports", label: "MB-surgery-reports" },
  { path: "/mobile/surgery-reports/create", label: "MB-surgery-create" },
  { path: "/mobile/messages", label: "MB-messages" },
];

// ---------- main ----------
const _SEG = (RUN_PC?1:0)+(RUN_ADMIN?1:0)+(RUN_MOBILE?1:0);
const PROCESS_TIMEOUT_MS = Math.max(8 * 60 * 1000, _SEG * 8 * 60 * 1000);
const PROCESS_TIMER = setTimeout(() => {
  console.error("FATAL: process exceeded " + PROCESS_TIMEOUT_MS + "ms, force-exiting");
  process.exit(2);
}, PROCESS_TIMEOUT_MS);
PROCESS_TIMER.unref();

(async () => {
  console.log("=== DMS Deep Smoke Test ===");
  console.log("Base:", BASE);
  console.log("Results dir:", RESULT_DIR);
  console.log("");

  const browser = await chromium.launch({ headless: HEADLESS });
  console.log("Target segments:", TARGET, "(pc/admin/mobile)");

  // ---- PC ----
  if (!RUN_PC) console.log("(skipped PC: target=" + TARGET + ")");
  const pcCtx = RUN_PC ? await browser.newContext({ viewport: { width: 1440, height: 900 }, ignoreHTTPSErrors: true }) : null;
  const pcPage = RUN_PC ? await pcCtx.newPage() : null;
  const pcLoginOk = RUN_PC ? await loginPC(pcPage) : false;
  if (RUN_PC) check("PC login", pcLoginOk, pcPage.url());

  if (pcLoginOk) {
    for (const p of PC_PAGES) {
      if (ONLY_MODULE && !p.path.includes(ONLY_MODULE) && !p.label.toLowerCase().includes(ONLY_MODULE.toLowerCase())) continue;
      try { await withTimeout(processOnePage(pcPage, p), 60000, p.label); }
      catch(e) { check(p.label + " - step", false, e.message.slice(0,150)); }
      await forceCloseOverlays(pcPage);
    }
  }

  // ---- Admin ----
  if (!RUN_ADMIN) console.log("(skipped Admin: target=" + TARGET + ")");
  const adCtx = RUN_ADMIN ? await browser.newContext({ viewport: { width: 1440, height: 900 }, ignoreHTTPSErrors: true }) : null;
  const adPage = RUN_ADMIN ? await adCtx.newPage() : null;
  const adLoginOk = RUN_ADMIN ? await loginAdmin(adPage) : false;
  if (!RUN_ADMIN) check("Admin login", true, "skipped (target=" + TARGET + ")");

  if (adLoginOk) {
    for (const p of ADMIN_PAGES) {
      if (ONLY_MODULE && !p.path.includes(ONLY_MODULE)) continue;
      try { await withTimeout(processOnePage(adPage, p), 60000, p.label); }
      catch(e) { check(p.label + " - step", false, e.message.slice(0,150)); }
      await forceCloseOverlays(adPage);
    }
  }

  // ---- Mobile ----
  if (!RUN_MOBILE) console.log("(skipped Mobile: target=" + TARGET + ")");
  const mCtx = RUN_MOBILE ? await browser.newContext({
    viewport: { width: 390, height: 844 },
    isMobile: true, hasTouch: true,
    userAgent: "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148",
    ignoreHTTPSErrors: true
  }) : null;
  const mPage = RUN_MOBILE ? await mCtx.newPage() : null;
  const mLoginOk = RUN_MOBILE ? await loginMobile(mPage) : false;
  if (RUN_MOBILE) check("Mobile login", mLoginOk, mPage.url()); else check("Mobile login", true, "skipped (target=" + TARGET + ")");

  if (mLoginOk) {
    for (const p of MOBILE_PAGES) {
      if (ONLY_MODULE && !p.path.includes(ONLY_MODULE)) continue;
      try { await withTimeout(visitPage(mPage, p.path, p.label), 30000, p.label); }
      catch(e) { check(p.label + " - step", false, e.message.slice(0,150)); }
    }
  }

  // ---- summary ----
  const failed = results.filter(r => !r.pass);
  const passed = results.length - failed.length;
  const summary = {
    base: BASE,
    timestamp: new Date().toISOString(),
    total: results.length, passed, failed: failed.length,
    failures: failed,
    resultDir: RESULT_DIR
  };

  const reportPath = path.join(RESULT_DIR, "report.json");
  fs.writeFileSync(reportPath, JSON.stringify(summary, null, 2));

  console.log("");
  console.log("=== Summary ===");
  console.log("Total:", results.length, "| Passed:", passed, "| Failed:", failed.length);
  if (failed.length) {
    console.log("\nFailed items:");
    failed.forEach(f => console.log("  FAIL:", f.name, "-", f.detail));
  }
  console.log("\nReport:", reportPath);
  console.log("Screenshots:", RESULT_DIR);

  await browser.close();
  process.exit(failed.length ? 1 : 0);
})();
