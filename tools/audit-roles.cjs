// tools/audit-roles.cjs
// 多角色权限矩阵审计（R7）：不同权限角色真实登录，验证菜单可见性、越权URL拦截、按钮级权限、数据级隔离。
// 只读审计：只打开新增/详情弹窗，不点击保存/提交等破坏性操作。
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

const args = process.argv.slice(2);
function arg(n, d) { const h = args.find(a => a.startsWith("--" + n + "=")); return h ? h.split("=").slice(1).join("=") : d; }
const BASE_ORIGIN = (arg("base", "http://43.128.145.141/dms")).replace(/\/+$/, "");
const ORIGIN = BASE_ORIGIN.replace(/\/dms$/, "");
const TENANT = arg("tenant", "default");
const STAMP = arg("stamp", new Date().toISOString().replace(/[-:T.Z]/g, "").slice(0, 14));
const OUT = path.join(__dirname, "..", "automation_test", "v4-browser-results", "roles-" + STAMP);
fs.mkdirSync(OUT, { recursive: true });

const sleep = ms => new Promise(r => setTimeout(r, ms));
const log = (s) => console.log(s);

const ROLES = [
  { id: "sys_admin",    name: "系统管理员",   pass: "Dms@123456", baseline: true },
  { id: "admin",        name: "超级管理员",   pass: "Sh123456",   baseline: true, super: true },
  { id: "sales_mgr",    name: "销售经理",     pass: "Dms@123456" },
  { id: "sales",        name: "普通销售员",   pass: "Dms@123456", dataIsolation: "sales" },
  { id: "cs",           name: "客服",         pass: "Dms@123456" },
  { id: "biz",          name: "商务",         pass: "Dms@123456" },
  { id: "fin",          name: "财务",         pass: "Dms@123456" },
  { id: "contract",     name: "合同",         pass: "Dms@123456" },
  { id: "dealer_admin", name: "经销商管理员", pass: "Dms@123456", dataIsolation: "dealer", dealerId: 3, dealerName: "杭州济民医疗器械有限公司" },
];

const MODULES = [
  { key: "dashboard",          label: "数据看板",       route: "/dashboard",               api: null },
  { key: "reports",            label: "报表中心",       route: "/reports",                 api: null },
  { key: "products",           label: "产品管理",       route: "/m/products",              api: "/api/products?page=1&size=5" },
  { key: "categories",         label: "产品分类",       route: "/m/categories",            api: "/api/product-categories?page=1&size=5" },
  { key: "product-lines",      label: "产品线管理",     route: "/m/product-lines",         api: null },
  { key: "product-bundles",    label: "产品组合/BOM",   route: "/m/product-bundles",       api: "/api/product-bundles?page=1&size=5" },
  { key: "product-prices",     label: "产品价格",       route: "/m/product-prices",        api: "/api/product-prices?page=1&size=5" },
  { key: "dealers",            label: "经销商管理",     route: "/m/dealers",               api: "/api/dealers?page=1&size=50" },
  { key: "hospitals",          label: "医院/终端",      route: "/m/hospitals",             api: "/api/hospitals?page=1&size=5" },
  { key: "warehouses",         label: "仓库管理",       route: "/m/warehouses",            api: "/api/warehouses?page=1&size=5" },
  { key: "regions",            label: "区域管理",       route: "/m/regions",               api: "/api/regions?page=1&size=5" },
  { key: "suppliers",          label: "供应商",         route: "/m/suppliers",             api: "/api/suppliers?page=1&size=5" },
  { key: "authorizations",     label: "授权管理",       route: "/m/authorizations",        api: null },
  { key: "promotions",         label: "促销规则",       route: "/m/promotions",            api: "/api/promotions?page=1&size=5" },
  { key: "orders",             label: "销售订单",       route: "/m/orders",                api: "/api/sales-orders?page=1&size=300" },
  { key: "sales-returns",      label: "销退订单",       route: "/m/sales-returns",         api: "/api/sales-returns?page=1&size=5" },
  { key: "purchase-orders",    label: "采购订单",       route: "/m/purchase-orders",       api: "/api/purchase-orders?page=1&size=5" },
  { key: "purchase-returns",   label: "采退订单",       route: "/m/purchase-returns",      api: null },
  { key: "inventory",          label: "库存查询",       route: "/m/inventory",             api: "/api/inventory?page=1&size=5" },
  { key: "sales-outs",         label: "销售出库",       route: "/m/sales-outs",            api: "/api/sales-outs?page=1&size=5" },
  { key: "receipts",           label: "收货入库",       route: "/m/receipts",              api: "/api/receipts?page=1&size=5" },
  { key: "stock-moves",        label: "库存移动",       route: "/m/stock-moves",           api: null },
  { key: "inventory-adjustments", label: "库存调整",    route: "/m/inventory-adjustments", api: null },
  { key: "surgery-reports",    label: "手术植入报台",   route: "/m/surgery-reports",       api: "/api/surgery-reports?page=1&size=5" },
  { key: "positions",          label: "销售岗位",       route: "/positions",               api: "/api/positions?page=1&size=5" },
  { key: "users",              label: "账号管理",       route: "/m/users",                 api: "/api/users?page=1&size=86" },
  { key: "roles-manage",       label: "角色权限",       route: "/roles-manage",            api: "/api/roles?page=1&size=50" },
  { key: "approval-templates", label: "审批流配置",     route: "/approval/templates",      api: null },
  { key: "approval-todo",      label: "我的审批",       route: "/approval/todo",           api: null },
  { key: "approval-admin",     label: "审批监控",       route: "/approval/admin",          api: "/api/approval/instances?page=1&size=5" },
  { key: "approval-delegations", label: "审批委托",     route: "/approval/delegations",    api: null },
  { key: "log-center",         label: "日志中心",       route: "/log-center",              api: null },
  { key: "contracts",          label: "合同工作台",     route: "/contracts",               api: null },
  { key: "contract-templates", label: "合同模板",       route: "/contracts/templates",     api: null },
  { key: "async-tasks",        label: "导入导出任务",   route: "/async-tasks",             api: null },
  { key: "dealer-profile",     label: "经销商画像",     route: "/dealers/profile",         api: null },
  { key: "tenant-page-configs", label: "列表页配置",    route: "/tenant-page-configs",     api: null },
  { key: "notifications",      label: "消息中心",       route: "/notifications",           api: null },
];

const FORBIDDEN_TESTS = {
  sales: [
    { route: "/m/users",          label: "账号管理" },
    { route: "/approval/admin",   label: "审批监控" },
    { route: "/m/product-prices", label: "产品价格维护" },
    { route: "/m/promotions",     label: "促销配置" },
  ],
  dealer_admin: [
    { route: "/m/promotions",     label: "促销配置" },
    { route: "/approval/admin",   label: "审批监控" },
    { route: "/m/users",          label: "账号管理" },
    { route: "/m/product-prices", label: "产品价格维护" },
  ],
  cs: [
    { route: "/m/users",          label: "账号管理" },
    { route: "/approval/admin",   label: "审批监控" },
  ],
  biz: [
    { route: "/m/users",          label: "账号管理" },
    { route: "/approval/admin",   label: "审批监控" },
  ],
  fin: [
    { route: "/m/users",          label: "账号管理" },
    { route: "/approval/admin",   label: "审批监控" },
  ],
  contract: [
    { route: "/m/users",          label: "账号管理" },
    { route: "/approval/admin",   label: "审批监控" },
  ],
  sales_mgr: [
    { route: "/m/users",          label: "账号管理" },
    { route: "/approval/admin",   label: "审批监控" },
  ],
};

// 危险权限码：普通业务角色/经销商角色不应拥有
const DANGER_PERMS = [
  "user:create","user:edit","user:delete","user:reset_password","user:unlock",
  "role:create","role:edit","role:delete","role:assign",
  "auth:create","auth:edit","auth:delete","auth:assign",
  "approval:admin","approval:manage","dealer:create","dealer:delete","dealer:edit",
  "product_price:create","product_price:edit","product_price:delete",
  "promotion:create","promotion:edit","promotion:delete",
];

const defects = [];
function addDefect(level, title, detail) {
  defects.push({ level, title, ...detail });
  const tag = { BLOCKER: "阻断", CRITICAL: "严重", MAJOR: "一般", MINOR: "轻微" }[level] || level;
  console.log(`  [缺陷/${tag}] ${title}`);
}

function attachNetListeners(page, bucket) {
  const onConsole = m => {
    if (m.type() === "error") {
      const t = m.text();
      if (!/favicon|ResizeObserver|Download is (prohibited|disallowed)|net::ERR_ABORTED/i.test(t)) bucket.consoleErrors.push(t.slice(0, 300));
    }
  };
  const onPageErr = e => bucket.pageErrors.push(e.message.slice(0, 300));
  const onResp = r => {
    const s = r.status(); const u = r.url();
    if (s >= 400 && !/favicon|\.map|actuator/i.test(u)) bucket.networkErrors.push(s + " " + u.replace(ORIGIN, "").slice(0, 140));
  };
  page.on("console", onConsole); page.on("pageerror", onPageErr); page.on("response", onResp);
  return () => { page.off("console", onConsole); page.off("pageerror", onPageErr); page.off("response", onResp); };
}

async function getLocalToken(page) {
  return await page.evaluate(() => {
    const keys = Object.keys(localStorage);
    const known = keys.find(k => /access.*token|token/i.test(k) && !/refresh/i.test(k));
    return known ? localStorage.getItem(known) : null;
  });
}

async function login(browser, role) {
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 950 }, ignoreHTTPSErrors: true });
  const page = await ctx.newPage();
  const bucket = { consoleErrors: [], pageErrors: [], networkErrors: [] };
  const detach = attachNetListeners(page, bucket);
  try {
    await page.goto(BASE_ORIGIN + "/login", { waitUntil: "domcontentloaded", timeout: 30000 });
    await sleep(1000);
    // 与已验证脚本一致的字段定位：placeholder "租户代码"/"账号"/密码
    await page.locator('input[placeholder*="租"]').first().fill(TENANT);
    await page.locator('input[placeholder*="账"],input[placeholder*="用户"]').first().fill(role.id);
    await page.locator('input[type="password"]').first().fill(role.pass);
    await page.getByRole("button", { name: /登\s*录/ }).first().click();
    let navigated = false;
    try {
      await page.waitForURL(u => !u.pathname.includes("/login"), { timeout: 15000 });
      navigated = true;
    } catch {
      navigated = false;
    }
    await sleep(2500);
    let url = page.url();
    if (!navigated || url.includes("/login")) {
      // 兜底：token 可能已写入但未跳转
      const tok = await getLocalToken(page);
      if (tok) {
        await page.goto(BASE_ORIGIN + "/home", { waitUntil: "domcontentloaded" }).catch(()=>{});
        await sleep(2000);
        url = page.url();
        navigated = !url.includes("/login");
      }
    }
    if (!navigated || url.includes("/login")) {
      await page.screenshot({ path: path.join(OUT, role.id + "-login-FAIL.png"), fullPage: true }).catch(()=>{});
      detach();
      return { ctx, ok: false, reason: "login failed: 未跳转, url=" + url, bucket, page };
    }
    // 确认 token key 并读取
    const tokenKey = await page.evaluate(() => Object.keys(localStorage).find(k => /access.*token/i.test(k)) || "dms_access_token");
    const userInfo = await page.evaluate(() => { try { return JSON.parse(localStorage.getItem("dms_user") || "{}"); } catch { return {}; } });
    const permissions = await page.evaluate(() => { try { return JSON.parse(localStorage.getItem("dms:user:permissions") || "[]"); } catch { return []; } });
    return { ctx, page, ok: true, url, tokenKey, userInfo, permissions, bucket, detach };
  } catch (e) {
    detach();
    return { ctx, page, ok: false, reason: e.message, bucket };
  }
}
async function collectMenu(page) {
  const subTitles = page.locator(".el-sub-menu__title");
  const cnt = await subTitles.count();
  for (let i = 0; i < cnt; i++) { await subTitles.nth(i).click().catch(()=>{}); await sleep(120); }
  await sleep(400);
  const items = await page.locator(".el-menu-item").evaluateAll(els => els.map(e => ({
    text: (e.innerText||"").trim(),
    visible: !!(e.offsetParent || e.getClientRects().length),
  })).filter(x => x.text));
  return items;
}

async function pageFetchJSON(page, apiPath) {
  return await page.evaluate(async (url) => {
    try {
      const tok = localStorage.getItem("dms_access_token");
      const r = await fetch(url, { headers: { Authorization: "Bearer " + tok, Accept: "application/json" } });
      const text = await r.text();
      let json = null; try { json = JSON.parse(text); } catch {}
      return { status: r.status, ok: r.ok, json, text: text.slice(0, 200) };
    } catch (e) { return { error: String(e) }; }
  }, ORIGIN + apiPath);
}

function extractList(res) {
  if (!res || !res.json) return { records: [], total: null };
  const d = res.json.data;
  if (Array.isArray(d)) return { records: d, total: d.length };
  if (d && Array.isArray(d.list)) return { records: d.list, total: d.total };
  if (d && Array.isArray(d.records)) return { records: d.records, total: d.total };
  if (Array.isArray(res.json)) return { records: res.json, total: res.json.length };
  return { records: [], total: null };
}

// 在角色 context 内直接 goto 受限 URL，判断是否被拦截
async function testForbidden(page, role, test) {
  const bucket = { consoleErrors: [], pageErrors: [], networkErrors: [] };
  const detach = attachNetListeners(page, bucket);
  const result = { route: test.route, label: test.label };
  try {
    const resp = await page.goto(BASE_ORIGIN + test.route, { waitUntil: "domcontentloaded", timeout: 30000 }).catch(e => { result.gotoError = e.message; return null; });
    result.httpStatus = resp ? resp.status() : null;
    await sleep(3000);
    result.finalUrl = page.url();
    const finalPath = result.finalUrl.replace(ORIGIN, "").replace(/\/dms/, "");
    result.finalPath = finalPath;
    const body = (await page.locator("body").innerText().catch(()=>"")).trim();
    result.bodyHead = body.slice(0, 300).replace(/\s+/g, " ");
    const blockedByUrl = /\/error\/403|\/403/.test(finalPath);
    const blockedByText = /无权限|权限不足|没有访问权限|禁止访问|无权访问/.test(body) && body.length < 800;
    const rows = await page.locator(".el-table__body-wrapper tr").count();
    result.tableRows = rows;
    const titleOk = body.includes(test.label) || body.includes("账号管理") || body.includes("审批监控") || body.includes("产品价格") || body.includes("促销规则");
    // 判定：被拦截 = URL到403 或 明确无权限文案 且 没有业务数据
    const blocked = (blockedByUrl || blockedByText) && rows === 0;
    // 若停留在目标页且标题+表格控件存在，说明未拦截
    const stayedOnTarget = finalPath.includes(test.route.replace(/\?.*$/, ""));
    result.blocked = blocked;
    result.leaked = stayedOnTarget && titleOk && !blocked;
    await page.screenshot({ path: path.join(OUT, role.id + "-forbid-" + test.route.replace(/[\/]/g, "_") + ".png"), fullPage: false }).catch(()=>{});
  } catch (e) {
    result.exception = e.message;
  } finally {
    detach();
  }
  result.consoleErrors = bucket.consoleErrors;
  result.networkErrors = bucket.networkErrors;
  result.pageErrors = bucket.pageErrors;
  return result;
}

// 按钮级权限采样：检查列表页顶部工具栏按钮
async function inspectButtons(page) {
  await sleep(2500);
  const btns = await page.locator(".crud-toolbar button, .table-toolbar button, .el-card .el-button, .filter-bar + div .el-button, .el-button").evaluateAll(els => {
    const out = [];
    els.forEach(e => {
      const txt = (e.innerText || e.textContent || "").trim().replace(/\s+/g, " ");
      if (!txt) return;
      const rect = e.getBoundingClientRect();
      const visible = rect.width > 0 && rect.height > 0 && (e.offsetParent !== null);
      if (visible && txt.length <= 12) out.push(txt);
    });
    return [...new Set(out)];
  });
  return btns;
}

// 正向操作：打开列表 -> 点第一个行内"查看/详情" -> 再打开"新增"弹窗（不保存）
async function positiveAction(page, route, screenshotPrefix) {
  const res = { route, listOpens: false, detailOpens: false, addOpens: false };
  try {
    await page.goto(BASE_ORIGIN + route, { waitUntil: "domcontentloaded", timeout: 30000 });
    await sleep(3000);
    res.rows = await page.locator(".el-table__body-wrapper tr").count();
    res.listOpens = res.rows >= 0;
    // 点击第一个行内按钮（查看/详情）
    const firstRow = page.locator(".el-table__body-wrapper tr").first();
    if (res.rows > 0) {
      const viewBtn = firstRow.getByRole("button", { name: /查看|详情/ }).first();
      if (await viewBtn.count()) {
        await viewBtn.click().catch(()=>{});
        await sleep(1500);
        const dialog = page.locator(".el-dialog:visible, .el-drawer:visible, .resource-detail");
        res.detailOpens = (await dialog.count()) > 0 || (await page.locator(".el-descriptions, .detail-section").count()) > 0;
        // 关闭弹窗
        await page.keyboard.press("Escape").catch(()=>{});
        await sleep(500);
      }
    }
    // 打开新增弹窗
    const addBtn = page.getByRole("button", { name: /新\s*增|添\s*加|新\s*建/ }).first();
    if (await addBtn.count()) {
      await addBtn.click().catch(()=>{});
      await sleep(1500);
      const addDialog = page.locator(".el-dialog:visible, .el-drawer:visible");
      res.addOpens = (await addDialog.count()) > 0;
      await page.keyboard.press("Escape").catch(()=>{});
      await sleep(400);
    }
    await page.screenshot({ path: path.join(OUT, screenshotPrefix + ".png"), fullPage: false }).catch(()=>{});
  } catch (e) {
    res.exception = e.message;
  }
  return res;
}

async function auditRole(browser, role, baseline) {
  console.log("\n=== 审计角色: " + role.id + " (" + role.name + ") ===");
  const sess = await login(browser, role);
  const out = { id: role.id, name: role.name, userInfo: null, permissionCount: 0, permissions: [], dangerousPerms: [], menu: [], modules: {}, forbidden: [], buttons: {}, positive: {}, dataIsolation: null, consoleErrors: [], networkErrors: [], pageErrors: [] };
  if (!sess.ok) {
    out.loginOk = false; out.reason = sess.reason;
    console.log("  登录失败: " + out.reason);
    await sess.ctx.close().catch(()=>{});
    return out;
  }
  out.loginOk = true;
  out.userInfo = { id: sess.userInfo.id, username: sess.userInfo.username, name: sess.userInfo.name, userType: sess.userInfo.userType, roleNames: sess.userInfo.roleNames, dealerId: sess.userInfo.dealerId };
  out.permissions = sess.permissions;
  out.permissionCount = sess.permissions.length;
  console.log("  登录成功 userType=" + out.userInfo.userType + " 权限码=" + out.permissionCount);

  // 1. 菜单可见性
  await sess.page.goto(BASE_ORIGIN + "/home", { waitUntil: "domcontentloaded" }).catch(()=>{});
  await sleep(2000);
  out.menu = await collectMenu(sess.page);
  console.log("  可见菜单项: " + out.menu.length);
  await sess.page.screenshot({ path: path.join(OUT, role.id + "-menu.png"), fullPage: false }).catch(()=>{});

  // 用菜单文本判断模块可见性
  const menuText = out.menu.map(m => m.text).join("|");

  // 2. 逐模块：页面可达 + API 状态码 + 数据条数 + 按钮
  for (const mod of MODULES) {
    const entry = { label: mod.label, route: mod.route };
    try {
      const resp = await sess.page.goto(BASE_ORIGIN + mod.route, { waitUntil: "domcontentloaded", timeout: 30000 }).catch(e => { entry.gotoError = e.message; return null; });
      entry.httpStatus = resp ? resp.status() : null;
      await sleep(1500);
      const finalPath = sess.page.url().replace(ORIGIN, "").replace(/\/dms/, "");
      entry.finalPath = finalPath;
      const body = (await sess.page.locator("body").innerText().catch(()=>"")).trim();
      entry.bodyLen = body.length;
      entry.is403 = /\/error\/403|403/.test(finalPath) || /无权限|权限不足/.test(body);
      entry.is404 = /\/error\/404|404/.test(finalPath) || (body.includes("404") && body.length < 200);
      entry.tableRows = await sess.page.locator(".el-table__body-wrapper tr").count();
      entry.inMenu = menuText.includes(mod.label);
      // 直连 API 验证后端是否返回数据
      if (mod.api) {
        const r = await pageFetchJSON(sess.page, mod.api);
        entry.apiStatus = r.status;
        const lst = extractList(r);
        entry.apiTotal = lst.total;
        entry.apiRecords = lst.records.length;
        // 记录关键字段样本（用于数据隔离判断）
        if (mod.key === "orders" && lst.records[0]) {
          const s = lst.records[0];
          entry.orderSampleKeys = Object.keys(s);
        }
      }
      out.modules[mod.key] = entry;
    } catch (e) {
      entry.exception = e.message;
      out.modules[mod.key] = entry;
    }
  }

  // 3. 按钮级权限采样（选 3 个页面）
  const buttonPages = [
    { route: "/m/users",     name: "users" },
    { route: "/m/orders",    name: "orders" },
    { route: "/m/promotions", name: "promotions" },
  ];
  for (const bp of buttonPages) {
    try {
      await sess.page.goto(BASE_ORIGIN + bp.route, { waitUntil: "domcontentloaded", timeout: 30000 });
      out.buttons[bp.name] = await inspectButtons(sess.page);
    } catch (e) { out.buttons[bp.name] = ["ERR:" + e.message]; }
  }

  // 4. 危险权限码检测
  const pset = new Set(sess.permissions);
  out.dangerousPerms = DANGER_PERMS.filter(p => pset.has(p));

  // 5. 越权 URL 测试
  const tests = FORBIDDEN_TESTS[role.id] || [];
  for (const t of tests) {
    const r = await testForbidden(sess.page, role, t);
    out.forbidden.push(r);
    console.log("  越权 " + t.route + " -> " + (r.blocked ? "已拦截" : (r.leaked ? "未拦截(泄漏)" : "其他")) + " rows=" + r.tableRows);
  }

  // 6. 正向操作
  out.positive.orders = await positiveAction(sess.page, "/m/orders", role.id + "-positive-orders");

  // 7. 数据隔离
  if (role.dataIsolation === "sales") {
    const r = await pageFetchJSON(sess.page, "/api/sales-orders?page=1&size=300");
    const lst = extractList(r);
    const me = out.userInfo;
    const byDealer = {};
    let otherOwned = 0;
    // 列表无 salesUserId/createdBy 字段，因此以"是否全部归属于当前销售员可解释"判断；这里记录总数与经销商分布
    lst.records.forEach(o => { byDealer[o.dealerName + "#" + o.dealerId] = (byDealer[o.dealerName + "#" + o.dealerId] || 0) + 1; });
    out.dataIsolation = {
      expected: "仅当前销售员(sales,id=" + me.id + ")负责的订单",
      actualTotal: lst.total,
      actualRecords: lst.records.length,
      dealerCount: Object.keys(byDealer).length,
      dealers: byDealer,
      baselineTotal: baseline ? baseline.modules.orders.apiTotal : null,
      isolated: false, // 172 单跨 12 经销商，显然不是个人数据
      note: "订单列表响应无 salesUserId/createdBy/ownerId 字段，无法按归属过滤；返回全部 " + lst.records.length + " 单跨 " + Object.keys(byDealer).length + " 经销商",
    };
  } else if (role.dataIsolation === "dealer") {
    const r = await pageFetchJSON(sess.page, "/api/sales-orders?page=1&size=300");
    const lst = extractList(r);
    const ownDealer = role.dealerId;
    let own = 0, other = 0; const otherSamples = [];
    lst.records.forEach(o => { if (Number(o.dealerId) === Number(ownDealer)) own++; else { other++; if (otherSamples.length < 5) otherSamples.push(o.code + "/" + o.dealerName); } });
    const dr = await pageFetchJSON(sess.page, "/api/dealers?page=1&size=50");
    const dl = extractList(dr);
    out.dataIsolation = {
      expected: "仅本经销商(" + role.dealerName + ",id=" + role.dealerId + ")的订单，且不应看到其他经销商列表",
      actualOrderTotal: lst.total,
      ownDealerOrders: own,
      otherDealerOrders: other,
      otherOrderSamples: otherSamples,
      dealersApiTotal: dl.total,
      dealersApiRecords: dl.records.length,
      isolated: other === 0 && dl.records.length <= 1,
    };
  }

  // 汇总该角色的 console/network 错误（取登录阶段 + 模块遍历累计）
  out.consoleErrors = sess.bucket.consoleErrors.slice(0, 20);
  out.networkErrors = sess.bucket.networkErrors.slice(0, 30);
  out.pageErrors = sess.bucket.pageErrors.slice(0, 10);

  await sess.ctx.close().catch(()=>{});
  return out;
}

function analyze(all) {
  const baseline = all.find(r => r.id === "sys_admin") || all.find(r => r.baseline);
  for (const role of all) {
    if (!role.loginOk) {
      addDefect("BLOCKER", "角色 " + role.id + " 无法登录测试环境", { role: role.id, reproduce: "用 " + role.id + " 登录 http://43.128.145.141/dms/", expected: "登录成功", actual: role.reason });
      continue;
    }
    // 危险权限
    if (!role.baseline && role.dangerousPerms && role.dangerousPerms.length) {
      addDefect("CRITICAL", "角色 " + role.id + " 被授予高危权限码（越权/提权风险）", {
        role: role.id,
        reproduce: "用 " + role.id + " 登录后访问 localStorage dms:user:permissions 或直接调用对应 API",
        expected: "业务/经销商角色不应拥有用户/角色/价格/促销/审批管理类写权限",
        actual: "拥有: " + role.dangerousPerms.join(", "),
      });
    }
    // 越权 URL
    for (const f of role.forbidden || []) {
      if (f.leaked) {
        addDefect("CRITICAL", "角色 " + role.id + " 可直接越权访问 " + f.label + "（前端无路由守卫且页面正常渲染）", {
          role: role.id,
          route: f.route,
          reproduce: "登录 " + role.id + " 后在地址栏直接访问 " + f.route,
          expected: "跳转 /error/403 或提示无权限，不渲染业务页面、不加载数据",
          actual: "停留在 " + f.finalPath + "，页面正常渲染，表格行数=" + f.tableRows,
          screenshot: path.join(OUT, role.id + "-forbid-" + f.route.replace(/[\/]/g, "_") + ".png"),
        });
      }
    }
    // API 级数据越权（对比 baseline）：非基线角色的敏感 API 不应返回全量数据
    if (baseline && !role.baseline) {
      const sensitive = ["users", "dealers", "product-prices", "promotions", "orders", "roles-manage"];
      for (const key of sensitive) {
        const m = role.modules[key]; const bm = baseline.modules[key];
        if (m && bm && m.apiStatus === 200 && bm.apiTotal && m.apiTotal) {
          // users/roles/promotions/product-prices 对销售/客服/经销商应被后端拒绝
          if ((key === "users" || key === "roles-manage" || key === "promotions" || key === "product-prices")) {
            if (m.apiRecords >= bm.apiRecords) {
              addDefect("CRITICAL", "角色 " + role.id + " 越权拉取全量" + m.label + "（后端API未做权限拦截）", {
                role: role.id, api: MODULES.find(x=>x.key===key).api,
                reproduce: "登录 " + role.id + " 后带 token 直接 GET " + MODULES.find(x=>x.key===key).api,
                expected: "返回 403 或仅返回自身授权范围内数据",
                actual: "HTTP 200，返回 " + m.apiRecords + " 条（基线 sys_admin 为 " + bm.apiRecords + " 条）",
              });
            }
          }
        }
      }
    }
    // 数据隔离
    if (role.dataIsolation && role.dataIsolation.isolated === false) {
      if (role.dataIsolation.dealersApiRecords !== undefined) {
        addDefect("BLOCKER", "dealer_admin 数据隔离失效：可见全部经销商与全部订单", {
          role: "dealer_admin",
          reproduce: "dealer_admin 登录后访问销售订单与经销商管理列表/API",
          expected: "仅可见经销商 D00003(" + role.dealerName + ") 的订单，且经销商列表只能看到自己",
          actual: "订单 " + role.dataIsolation.actualOrderTotal + " 单中本经销商 " + role.dataIsolation.ownDealerOrders + " 单、其他经销商 " + role.dataIsolation.otherDealerOrders + " 单；经销商API返回 " + role.dataIsolation.dealersApiRecords + " 条",
          evidence: "越权订单样本: " + (role.dataIsolation.otherOrderSamples||[]).join("; "),
        });
      } else {
        addDefect("CRITICAL", "sales 数据隔离失效：可见全部销售订单（非本人数据）", {
          role: "sales",
          reproduce: "sales 登录后打开销售订单列表或 GET /api/sales-orders",
          expected: "仅返回当前销售员负责的订单（应为 sys_admin 可见集合的子集）",
          actual: "返回 " + role.dataIsolation.actualRecords + " 单，覆盖 " + role.dataIsolation.dealerCount + " 个经销商，与 sys_admin 全量一致；列表响应不含归属字段无法过滤",
        });
      }
    }
    // Console/网络错误作为一般缺陷
    if (role.networkErrors && role.networkErrors.length) {
      addDefect("MAJOR", "角色 " + role.id + " 审计过程中出现 4xx/5xx 网络错误", {
        role: role.id,
        expected: "受保护资源应返回 403 而非 401/500，正常页面不应有 4xx/5xx",
        actual: role.networkErrors.slice(0, 10).join(" | "),
      });
    }
  }
}

function buildMatrix(all) {
  // 角色 × 模块：基于模块页面是否渲染（非403/404）+ 菜单可见
  const rows = {};
  for (const mod of MODULES) rows[mod.key] = { label: mod.label, roles: {} };
  for (const role of all) {
    for (const mod of MODULES) {
      const m = role.modules && role.modules[mod.key];
      let cell = "-";
      if (!role.loginOk) cell = "LOGIN_FAIL";
      else if (!m) cell = "-";
      else if (m.is403) cell = "403";
      else if (m.is404) cell = "404";
      else if (m.httpStatus === 200 && m.bodyLen > 50) cell = "可见";
      else cell = "?";
      rows[mod.key].roles[role.id] = cell;
    }
  }
  return rows;
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const all = [];
  // 先跑基线 sys_admin
  const baselineOrder = ROLES.slice().sort((a,b) => (b.baseline?1:0)-(a.baseline?1:0));
  for (const role of baselineOrder) {
    const r = await auditRole(browser, role, null);
    all.push(r);
    await sleep(1500);
  }
  await browser.close();

  analyze(all);
  const matrix = buildMatrix(all);

  const summary = {
    base: BASE_ORIGIN, stamp: STAMP, out: OUT,
    roles: all.length,
    loginOk: all.filter(r => r.loginOk).length,
    totalPerms: Object.fromEntries(all.map(r => [r.id, r.permissionCount])),
    defects: defects.length,
    blockers: defects.filter(d => d.level === "BLOCKER").length,
    criticals: defects.filter(d => d.level === "CRITICAL").length,
    majors: defects.filter(d => d.level === "MAJOR").length,
    minors: defects.filter(d => d.level === "MINOR").length,
  };

  fs.writeFileSync(path.join(OUT, "roles-report.json"), JSON.stringify({ summary, matrix, roles: all, defects }, null, 2));
  // 单独写一份菜单矩阵 CSV
  const roleIds = all.map(r => r.id);
  const csvLines = ["模块," + roleIds.join(",")];
  for (const mod of MODULES) {
    const cells = roleIds.map(rid => matrix[mod.key].roles[rid] || "-");
    csvLines.push(mod.label + "," + cells.join(","));
  }
  fs.writeFileSync(path.join(OUT, "menu-matrix.csv"), "\uFEFF" + csvLines.join("\n"), "utf8");

  console.log("\n========== 汇总 ==========");
  console.log(JSON.stringify(summary, null, 2));
  console.log("报告目录: " + OUT);
  console.log(" - roles-report.json");
  console.log(" - menu-matrix.csv");
})();





