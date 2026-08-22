// Page interaction helpers for list pages (CrudView-based)
const fs = require("fs");
const path = require("path");
const config = require("../config");

function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }

async function screenshot(page, name) {
  try {
    if (!fs.existsSync(config.RESULT_DIR)) fs.mkdirSync(config.RESULT_DIR, { recursive: true });
    const safe = name.replace(/[^a-z0-9_-]/gi, "_").slice(0, 80);
    await page.screenshot({ path: path.join(config.RESULT_DIR, safe + ".png"), fullPage: false });
  } catch(e) {}
}

// Navigate to a module page via /m/{key} or a direct route
async function gotoModule(page, routeOrKey, label) {
  const route = routeOrKey.startsWith("/") ? routeOrKey : "/m/" + routeOrKey;
  const fullUrl = config.BASE + route;
  try {
    await page.goto(fullUrl, { waitUntil: "domcontentloaded", timeout: 25000 });
  } catch(e) {
    return { ok: false, error: "navigation timeout/error: " + e.message.slice(0, 100) };
  }
  await sleep(2500);
  try {
    await page.waitForSelector(".el-table,.el-card,.el-empty,.el-pagination,.van-cell,.van-empty,.dashboard", { timeout: 8000 });
  } catch(e) {}

  // Collect text from best container
  let bodyText = "";
  let bestLen = 0;
  for (const sel of [".el-main", "main", ".page-content", ".app-main", ".el-card", "body"]) {
    const loc = page.locator(sel).first();
    if (await loc.count()) {
      try {
        const t = await loc.innerText();
        if (t && t.replace(/\s/g, "").length > bestLen) { bodyText = t; bestLen = t.replace(/\s/g, "").length; }
      } catch(e) {}
    }
  }
  const tableRows = await page.locator(".el-table__body-wrapper tbody tr").count().catch(() => 0);
  const tableExists = await page.locator(".el-table").count().catch(() => 0);
  const paginationExists = await page.locator(".el-pagination").count().catch(() => 0);
  const cardsExists = await page.locator(".el-card").count().catch(() => 0);
  const toolbarExists = await page.locator(".page-toolbar, .el-toolbar").count().catch(() => 0);
  const errorPath = await page.evaluate(() => window.location.pathname).catch(() => "");
  const errorTitle = await page.locator(".error-page, .el-result").first().innerText().catch(() => "");
  const noRoute = errorPath.includes("/error/404") ||
    /\u9875\u9762\u4e0d\u5b58\u5728|\u627e\u4e0d\u5230\u8be5\u9875\u9762/.test(errorTitle);

  await screenshot(page, label || route);

  // Page has content if it has visible text, table rows, a table element, cards, or toolbar
  const hasStructure = tableExists > 0 || paginationExists > 0 || cardsExists > 0 || toolbarExists > 0;
  return {
    ok: !noRoute && (bestLen > 5 || tableRows > 0 || hasStructure),
    route,
    tableRows,
    bodyText: bodyText.slice(0, 300),
    error: noRoute ? "404/not found" : (bestLen <= 10 && tableRows === 0 ? "blank/no content" : null),
  };
}

// Get first row action button (view/detail)
async function clickFirstRowAction(page) {
  await sleep(500);
  const rows = page.locator(".el-table__body-wrapper tbody tr");
  const count = await rows.count();
  if (count === 0) return { clicked: false, reason: "no rows" };

  const firstRow = rows.first();
  // Target only the action column (last column, or fixed-right column)
  const actionSelectors = [
    ".el-table__fixed-right tbody tr:first-child .el-button",
    ".el-table__fixed-right tbody tr:first-child .el-link",
    "tbody tr:first-child td:last-child .el-button",
    "tbody tr:first-child td:last-child .el-link",
  ];
  // Filter: only buttons that look like actions (not ID links)
  const actionLabels = /查看|详情|编辑|删除|审批|提交|更多|View|Edit|Delete/;
  for (const sel of actionSelectors) {
    const candidates = firstRow.locator(sel);
    const cnt = await candidates.count();
    for (let i = 0; i < cnt; i++) {
      const btn = candidates.nth(i);
      let text = "";
      try { text = (await btn.innerText()).trim(); } catch(e) { continue; }
      if (actionLabels.test(text) || text.length <= 4) {
        try {
          await btn.click({ timeout: 5000 });
          await sleep(2000);
          const dialog = page.locator(".el-dialog:visible, .el-drawer:visible, .el-message-box:visible").first();
          const detail = page.locator(".el-descriptions:visible, .detail-content:visible").first();
          // Also detect if URL navigated to a detail page
          const url = page.url();
          const navigated = /\/\d+$|\/view|\/detail/.test(url);
          const opened = (await dialog.count()) > 0 || (await detail.count()) > 0 || navigated;
          return { clicked: true, opened, dialogOrDetail: opened, url };
        } catch(e) { continue; }
      }
    }
  }
  return { clicked: false, reason: "no clickable action" };
}

async function closeDialogs(page) {
  await page.keyboard.press("Escape").catch(() => {});
  const closeBtn = page.locator(".el-dialog__headerbtn:visible, .el-drawer__close-btn:visible, .el-message-box__headerbtn:visible").first();
  if (await closeBtn.count()) { await closeBtn.click().catch(() => {}); }
  await sleep(400);
}

// Click the primary Create button in the toolbar
async function clickCreateButton(page) {
  await sleep(500);
  const containers = [".page-toolbar", ".el-card", ".toolbar", ".page-header", ".crud-toolbar", ".list-toolbar"];
  let btn = null;
  for (const cSel of containers) {
    const c = page.locator(cSel).first();
    if (!(await c.count())) continue;
    const candidates = c.locator(".el-button--primary, .el-button--success, .el-button");
    const cnt = await candidates.count();
    for (let i = 0; i < cnt; i++) {
      const b = candidates.nth(i);
      let text = "";
      try { text = (await b.innerText()).trim(); } catch(e) {}
      if (/\u65b0\u589e|\u6dfb\u52a0|\u521b\u5efa|\u65b0\u5efa|Create|Add/.test(text)) { btn = b; break; }
    }
    if (btn) break;
  }
  if (!btn) return { clicked: false, reason: "no create button" };
  try {
    await btn.click({ timeout: 5000 });
    await sleep(2000);
    const dlg = page.locator(".el-dialog:visible, .el-drawer:visible").first();
    const form = page.locator(".el-form:visible").first();
    const opened = (await dlg.count()) > 0 || (await form.count()) > 0;
    return { clicked: true, opened };
  } catch(e) {
    return { clicked: false, reason: e.message.slice(0, 100) };
  }
}

// Fill a basic form with test data. Accepts a map of fieldKey -> value.
async function fillForm(page, data) {
  for (const [key, value] of Object.entries(data)) {
    // el-form-item with prop="key"
    const formItem = page.locator('.el-form-item:has(label[for*="' + key + '"]), .el-form-item:has(.el-form-item__label:has-text("' + value.label + '"))').first();
    // Try by label text
    const label = value.label || key;
    const item = page.locator(".el-form-item").filter({ hasText: label }).first();
    if (!(await item.count())) continue;

    const input = item.locator("input").first();
    const textarea = item.locator("textarea").first();
    const select = item.locator(".el-select").first();

    if (value.type === "select" && await select.count()) {
      await select.click();
      await sleep(500);
      const opt = page.locator(".el-select-dropdown:visible .el-select-dropdown__item").filter({ hasText: value.optionLabel || value.value }).first();
      if (await opt.count()) await opt.click();
    } else if (await textarea.count()) {
      await textarea.fill(String(value.value));
    } else if (await input.count()) {
      const inputType = await input.getAttribute("type").catch(() => "text");
      if (inputType === "number") {
        await input.fill(String(value.value));
      } else {
        await input.fill(String(value.value));
      }
    }
    await sleep(200);
  }
}

module.exports = { sleep, screenshot, gotoModule, clickFirstRowAction, closeDialogs, clickCreateButton, fillForm };
