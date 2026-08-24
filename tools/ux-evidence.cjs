const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

const BASE = "http://43.128.145.141";
const OUT = path.join(__dirname, "..", "automation_test", "v4-browser-results", "ux-evidence");
fs.mkdirSync(OUT, { recursive: true });

const findings = [];
function log(sev, page, issue, detail, shot) {
  findings.push({ sev, page, issue, detail, shot });
  console.log(`[${sev}] ${page}: ${issue} :: ${detail.slice(0,150)}`);
}
const sleep = ms => new Promise(r=>setTimeout(r,ms));

async function login(p) {
  await p.goto(`${BASE}/login`,{waitUntil:"networkidle"});
  await sleep(800);
  const ins = p.locator(".login-form input");
  await ins.nth(0).fill("default");
  await ins.nth(1).fill("admin");
  await ins.nth(2).fill("Sh123456");
  await p.locator(".btn-login").first().click();
  await p.waitForURL(/^(?!.*\/login).*$/,{timeout:15000}).catch(()=>{});
  await sleep(1500);
}

(async () => {
  const b = await chromium.launch({headless:true});
  const p = await b.newPage({viewport:{width:1440,height:900}});
  const api5xx=[];
  p.on("response",r=>{ if(r.status()>=500) api5xx.push(r.status()+" "+r.url()); });
  await login(p);

  // 1. Orders detail - check raw enum COMPLETED/SALES
  await p.goto(`${BASE}/m/orders`,{waitUntil:"networkidle"});
  await sleep(800);
  const orderRows = p.locator(".el-table__body-wrapper tbody tr");
  if (await orderRows.count()) {
    await orderRows.first().locator(".el-button").first().click();
    await sleep(800);
    const dlg = p.locator(".el-dialog:visible, .el-drawer:visible").first();
    if (await dlg.count()) {
      const txt = (await dlg.innerText()).replace(/\s+/g," ");
      const rawStatus = /\b(COMPLETED|DRAFT|SUBMITTED|PENDING_APPROVAL|APPROVED|REJECTED|CANCELLED)\b/.test(txt);
      const rawType = /\b(SALES|RETURNS?|PURCHASE)\b/.test(txt) && !/(销售|采购|退货)/.test(txt);
      if (rawStatus) log("MEDIUM","订单详情","状态显示原始英文枚举码",txt.match(/状态\s*[:：]?\s*([A-Z_]+)/)?.[0] || txt.slice(0,200));
      if (rawType) log("MEDIUM","订单详情","订单类型显示原始英文枚举码",txt.match(/订单类型\s*[:：]?\s*([A-Z_]+)/)?.[0] || "");
      await p.screenshot({path:path.join(OUT,"orders-detail-enum.png")});
      await p.keyboard.press("Escape");
      await sleep(300);
    }
  }

  // 2. Products detail - GOODS/raw product type
  await p.goto(`${BASE}/m/products`,{waitUntil:"networkidle"});
  await sleep(800);
  const prodRows = p.locator(".el-table__body-wrapper tbody tr");
  if (await prodRows.count()) {
    await prodRows.first().locator(".el-button").first().click();
    await sleep(800);
    const dlg = p.locator(".el-dialog:visible, .el-drawer:visible").first();
    if (await dlg.count()) {
      const txt = (await dlg.innerText()).replace(/\s+/g," ");
      const rawType = /产品类型\s*[:：]?\s*(GOODS|SERVICE|BUNDLE|RAW)\b/.test(txt);
      if (rawType) log("MEDIUM","产品详情","产品类型显示 GOODS 等原始枚举而非中文",txt.match(/产品类型[^产品]*/)?.[0]||txt.slice(0,150));
      await p.screenshot({path:path.join(OUT,"products-detail-enum.png")});
      await p.keyboard.press("Escape");
      await sleep(300);
    }
  }

  // 3. Inventory detail - raw tenantId/warehouseCode/productCode technical fields
  await p.goto(`${BASE}/m/inventory`,{waitUntil:"networkidle"});
  await sleep(800);
  const invRows = p.locator(".el-table__body-wrapper tbody tr");
  if (await invRows.count()) {
    await invRows.first().locator(".el-button").first().click();
    await sleep(800);
    const dlg = p.locator(".el-dialog:visible, .el-drawer:visible").first();
    if (await dlg.count()) {
      const txt = (await dlg.innerText()).replace(/\s+/g," ");
      const hasTenantId = /租户ID\s*[:：]?\s*[0-9a-f-]{30,}/.test(txt);
      const hasTechCode = /(warehouseCode|productCode)\s*[:：]?\s*[A-Z0-9-]+/.test(txt);
      if (hasTenantId) log("LOW","库存详情","对业务用户显示租户ID(技术字段)","tenantId UUID exposed");
      if (hasTechCode) log("LOW","库存详情","显示 warehouseCode/productCode 技术字段名",txt.match(/(warehouseCode|productCode)[^\s]*/)?.[0]||"");
      await p.screenshot({path:path.join(OUT,"inventory-detail-raw.png")});
      await p.keyboard.press("Escape");
      await sleep(300);
    }
  }

  // 4. Product prices - CNY/SKU raw
  await p.goto(`${BASE}/m/product-prices`,{waitUntil:"networkidle"});
  await sleep(800);
  const priceRows = p.locator(".el-table__body-wrapper tbody tr");
  if (await priceRows.count()) {
    await priceRows.first().locator(".el-button").first().click();
    await sleep(800);
    const dlg = p.locator(".el-dialog:visible, .el-drawer:visible").first();
    if (await dlg.count()) {
      const txt = (await dlg.innerText()).replace(/\s+/g," ");
      const rawCcy = /币种\s*[:：]?\s*(CNY|USD|EUR)\b/.test(txt);
      const skuLabel = /\bSKU\b\s*[A-Z]/.test(txt);
      if (rawCcy) log("LOW","价格详情","币种显示 CNY 代码而非人民币符号/中文","");
      if (skuLabel) log("LOW","价格详情","标签显示 SKU 而非产品编码",txt.slice(0,200));
      await p.screenshot({path:path.join(OUT,"price-detail-raw.png")});
      await p.keyboard.press("Escape");
      await sleep(300);
    }
  }

  // 5. Dealers - level T1 raw
  await p.goto(`${BASE}/m/dealers`,{waitUntil:"networkidle"});
  await sleep(800);
  const dRows = p.locator(".el-table__body-wrapper tbody tr");
  if (await dRows.count()) {
    await dRows.first().locator(".el-button").first().click();
    await sleep(800);
    const dlg = p.locator(".el-dialog:visible, .el-drawer:visible").first();
    if (await dlg.count()) {
      const txt = (await dlg.innerText()).replace(/\s+/g," ");
      const rawLevel = /级别\s*[:：]?\s*(T1|T2|T3|T4)\b/.test(txt);
      if (rawLevel) log("LOW","经销商详情","级别显示 T1/T2 代码而非中文等级",txt.match(/级别[^经]*/)?.[0]||"");
      const rawRegion = /所属区域\s*[:：]?\s*\d+\s*$/.test(txt) || /所属区域\s*[:：]?\s*\d+\s/.test(txt);
      if (rawRegion) log("MEDIUM","经销商详情","所属区域显示数字ID而非区域名称",txt.match(/所属区域\s*[:：]?\s*\d+/)?.[0]||"");
      await p.screenshot({path:path.join(OUT,"dealer-detail-raw.png")});
      await p.keyboard.press("Escape");
      await sleep(300);
    }
  }

  // 6. Dashboard chart axis labels cut off / funnel raw English statuses
  await p.goto(`${BASE}/dashboard`,{waitUntil:"networkidle"});
  await sleep(1500);
  const dashText = (await p.locator("body").innerText()).replace(/\s+/g," ");
  if (/\b(PENDING_APPROVAL|CANCELLED|SUBMITTED|APPROVED|DRAFT|CC)\b/.test(dashText)) {
    log("MEDIUM","仪表盘-订单漏斗","漏斗图例显示英文状态码而非中文",dashText.match(/订单漏斗[\s\S]{0,300}/)?.[0]?.slice(0,300)||"");
  }
  await p.screenshot({path:path.join(OUT,"dashboard-funnel.png")});

  // 7. Authorizations detail - "经销商 1" showing id instead of name
  await p.goto(`${BASE}/m/authorizations`,{waitUntil:"networkidle"});
  await sleep(800);
  const aRows = p.locator(".el-table__body-wrapper tbody tr");
  if (await aRows.count()) {
    await aRows.first().locator(".el-button").first().click();
    await sleep(800);
    const dlg = p.locator(".el-dialog:visible, .el-drawer:visible").first();
    if (await dlg.count()) {
      const txt = (await dlg.innerText()).replace(/\s+/g," ");
      if (/经销商\s+1\s*$/.test(txt) || /经销商\s*[:：]\s*\d+\s/.test(txt)) {
        log("HIGH","授权详情","外键字段显示数字ID而非经销商名称",txt.slice(0,200));
      }
      await p.screenshot({path:path.join(OUT,"auth-detail-id.png")});
      await p.keyboard.press("Escape");
    }
  }

  // 8. Verify real Dashboard APIs work
  const realApis = ["/api/dashboard/kpi","/api/dashboard/sales-trend","/api/dashboard/order-funnel","/api/menu-configs","/api/dicts/types"];
  for (const u of realApis) {
    const r = await p.request.get(`${BASE}${u}`);
    const t = (await r.text()).slice(0,150);
    if (r.status() !== 200) log("HIGH","API",`${u} returned ${r.status()}`,t);
    else console.log(`[API-OK] ${u}: ${t.slice(0,80)}`);
  }

  console.log("\n=== 5XX errors:", api5xx.length, "===");
  api5xx.forEach(e=>console.log("  ",e));

  fs.writeFileSync(path.join(OUT,"findings.json"), JSON.stringify(findings,null,2));
  console.log("\n=== UX Findings:", findings.length, "===");
  findings.forEach(f=>console.log(`  [${f.sev}] ${f.page}/${f.issue}`));
  console.log("\nEvidence dir:", OUT);
  await b.close();
})();
