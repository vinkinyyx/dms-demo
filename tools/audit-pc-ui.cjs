// tools/audit-pc-ui.cjs
// 三端审计 - PC 前台：逐模块打开、截图、监听 Console/网络、检查工具栏/表格/分页/筛选
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

const args = process.argv.slice(2);
function arg(n, d) { const h = args.find(a => a.startsWith("--" + n + "=")); return h ? h.split("=").slice(1).join("=") : d; }
const BASE = (arg("base") || "http://43.128.145.141/dms").replace(/\/$/, "");
const USER = arg("user", "admin");
const PASS = arg("pass", "Sh123456");
const TENANT = arg("tenant", "default");
const STAMP = new Date().toISOString().replace(/[-:T.Z]/g, "").slice(0, 14);
const OUT = path.join(__dirname, "..", "automation_test", "v4-browser-results", "audit-pc-" + STAMP);
fs.mkdirSync(OUT, { recursive: true });

// (key, label, route?)  route 缺省走 /m/<key>
const MODULES = [
  ["dashboard","数据驾驶舱","/dashboard"],
  ["reports","报表中心","/reports"],
  ["products","产品管理"],
  ["categories","产品分类"],
  ["product-lines","产品线管理"],
  ["product-bundles","产品组合/BOM"],
  ["product-prices","产品价格"],
  ["dealers","经销商管理"],
  ["hospitals","医院/终端"],
  ["warehouses","仓库管理"],
  ["regions","区域管理"],
  ["suppliers","供应商"],
  ["authorizations","授权管理"],
  ["promotions","促销规则"],
  ["orders","销售订单"],
  ["sales-returns","销退订单"],
  ["purchase-orders","采购订单"],
  ["purchase-returns","采退订单"],
  ["inventory","库存查询"],
  ["sales-outs","销售出库"],
  ["receipts","收货入库"],
  ["stock-moves","库存移动"],
  ["inventory-adjustments","库存调整"],
  ["surgery-reports","手术植入报台"],
  ["positions","销售岗位","/positions"],
  ["users","账号管理"],
  ["approval-templates","审批流配置","/approval/templates"],
  ["approval-todo","我的审批","/approval/todo"],
  ["approval-admin","审批监控","/approval/admin"],
  ["log-center","日志中心","/log-center"],
  ["contracts","合同工作台","/contracts"],
  ["contract-templates","合同模板","/contracts/templates"],
  ["async-tasks","导入导出任务","/async-tasks"],
  ["dealer-profile","经销商画像","/dealers/profile"],
  ["notifications","消息中心","/notifications"],
];

const results = [];
const sleep = ms => new Promise(r => setTimeout(r, ms));

function rec(name, status, detail) {
  results.push({ name, status, detail: String(detail||"").slice(0,500) });
  console.log(`[${status}] ${name}${detail?" | "+String(detail).slice(0,160):""}`);
}

async function auditModule(page, key, label, route) {
  const url = route || ("/m/" + key);
  const consoleErrors = [];
  const networkErrors = [];
  const pageErrors = [];
  const onConsole = m => { if (m.type()==="error") { const t=m.text(); if(!/favicon|ResizeObserver|Download is prohibited|net::ERR_ABORTED|Download is disallowed/i.test(t)) consoleErrors.push(t.slice(0,300)); } };
  const onPageErr = e => pageErrors.push(e.message.slice(0,300));
  const onResp = r => { const s=r.status(); if(s>=400){ const u=r.url(); if(!/favicon|\.map|actuator/i.test(u)) networkErrors.push(s+" "+u.replace(BASE,"").slice(0,120)); } };
  page.on("console", onConsole); page.on("pageerror", onPageErr); page.on("response", onResp);
  const item = { key, label, url, errors:{consoleErrors,networkErrors,pageErrors} };
  try {
    const t0 = Date.now();
    const resp = await page.goto(BASE + url, { waitUntil: "domcontentloaded", timeout: 30000 });
    item.httpStatus = resp ? resp.status() : null;
    await sleep(3500);
    // 等表格加载
    await page.waitForTimeout(1500);
    item.title = await page.title();
    item.h1 = await page.locator("h1,h2,.page-title,.el-breadcrumb, .module-title").first().textContent().catch(()=>"").then(t=>(t||"").trim().slice(0,80));
    // 白屏检测
    const bodyText = (await page.locator("body").innerText().catch(()=>"")).trim();
    item.bodyLen = bodyText.length;
    if (bodyText.length < 20) item.blank = true;
    // 404
    if (bodyText.includes("404") && bodyText.length < 100) item.is404 = true;
    // 工具栏：查询/重置
    const hasQuery = await page.getByRole("button",{name:/查\s*询|搜\s*索/}).count();
    const hasReset = await page.getByRole("button",{name:/重\s*置/}).count();
    item.toolbar = { query:hasQuery, reset:hasReset };
    // 表格
    item.tableCount = await page.locator(".el-table").count();
    item.rowCount = await page.locator(".el-table__body-wrapper tr").count();
    // 分页
    item.pagination = await page.locator(".el-pagination").count();
    // 列设置/新增/导出/导入 按钮
    item.hasAdd = await page.getByRole("button",{name:/新\s*增|添\s*加|创\s*建/}).count();
    item.hasExport = await page.getByRole("button",{name:/导\s*出/}).count();
    item.hasImport = await page.getByRole("button",{name:/导\s*入/}).count();
    // 筛选漏斗
    item.filterIcons = await page.locator(".crud-filter-trigger, .el-table__column-filter-trigger, [class*=filter]").count();
    // 乱码检测
    if (/\?\?\?\?/.test(bodyText)) item.garbled = true;
    // 截图
    await page.screenshot({ path: path.join(OUT, key+".png"), fullPage: false }).catch(()=>{});
    item.ms = Date.now()-t0;
    // 判定
    let status = "PASS";
    if (item.blank||item.is404||pageErrors.length||networkErrors.filter(n=>/^[45]/.test(n)).some(n=>!n.includes("401"))) status="FAIL";
    else if (consoleErrors.length) status="WARN";
    rec(label, status, `http=${item.httpStatus} rows=${item.rowCount} table=${item.tableCount} page=${item.pagination} ce=${consoleErrors.length} ne=${networkErrors.length} pe=${pageErrors.length}`);
  } catch(e) {
    item.exception = e.message.slice(0,300);
    await page.screenshot({ path: path.join(OUT, key+"-ERR.png") }).catch(()=>{});
    rec(label, "FAIL", "EXCEPTION: "+e.message.slice(0,200));
  } finally {
    page.off("console", onConsole); page.off("pageerror", onPageErr); page.off("response", onResp);
  }
  results[results.length-1].item = item;
  return item;
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 900 }, ignoreHTTPSErrors: true });
  const page = await ctx.newPage();
  // 登录
  await page.goto(BASE+"/login", { waitUntil:"domcontentloaded" });
  await sleep(1000);
  const tenantIn = page.locator('input[placeholder*="租"],input[placeholder*="tenant" i]').first();
  if (await tenantIn.count()) { try{await tenantIn.fill(TENANT);}catch(e){} }
  await page.locator('input[placeholder*="账"],input[placeholder*="用户"]').first().fill(USER).catch(()=>{});
  await page.locator('input[type="password"]').first().fill(PASS).catch(()=>{});
  await page.keyboard.press("Enter");
  await page.waitForURL(u=>!u.pathname.includes("/login"), { timeout: 15000 }).catch(()=>{});
  await sleep(3000);
  const loggedIn = !page.url().includes("/login");
  rec("PC登录", loggedIn?"PASS":"FAIL", "url="+page.url());
  if (!loggedIn) { await browser.close(); process.exit(1); }

  for (const [key,label,route] of MODULES) {
    await auditModule(page, key, label, route);
    await sleep(600);
  }

  await browser.close();
  const summary = {
    base: BASE, user: USER, stamp: STAMP, out: OUT,
    total: results.length-1,
    pass: results.filter(r=>r.status==="PASS").length,
    warn: results.filter(r=>r.status==="WARN").length,
    fail: results.filter(r=>r.status==="FAIL").length,
  };
  fs.writeFileSync(path.join(OUT,"report.json"), JSON.stringify({summary, results}, null, 2));
  console.log("\n===== SUMMARY =====");
  console.log(JSON.stringify(summary, null, 2));
  console.log("Report: " + OUT);
})();


