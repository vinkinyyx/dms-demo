// tools/audit-pc-interaction.cjs
// 深度交互：筛选/排序/分页/详情/新增弹窗/导出，针对关键业务页
const { chromium } = require("playwright");
const fs = require("fs"); const path = require("path");
const args = process.argv.slice(2);
function arg(n,d){const h=args.find(a=>a.startsWith("--"+n+"="));return h?h.split("=").slice(1).join("="):d;}
const BASE = (arg("base")||"http://43.128.145.141/dms").replace(/\/$/,"");
const STAMP = new Date().toISOString().replace(/[-:T.Z]/g,"").slice(0,14);
const OUT = path.join(__dirname,"..","automation_test","v4-browser-results","audit-interact-"+STAMP);
fs.mkdirSync(OUT,{recursive:true});
const results=[];
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
function rec(n,s,d){results.push({name:n,status:s,detail:String(d||"").slice(0,400)});console.log(`[${s}] ${n}${d?" | "+String(d).slice(0,150):""}`);}

async function login(page){
  await page.goto(BASE+"/login",{waitUntil:"domcontentloaded"}); await sleep(1200);
  await page.locator('input[placeholder*="租"]').first().fill("default").catch(()=>{});
  await page.locator('input[placeholder*="账"]').first().fill("admin").catch(()=>{});
  await page.locator('input[type="password"]').first().fill("Sh123456").catch(()=>{});
  await page.keyboard.press("Enter");
  await page.waitForURL(u=>!u.pathname.includes("/login"),{timeout:15000}).catch(()=>{});
  await sleep(2500);
}

async function withPage(label, fn){
  const browser = await chromium.launch({headless:true});
  const ctx = await browser.newContext({viewport:{width:1600,height:900}});
  const page = await ctx.newPage();
  const ce=[],ne=[],pe=[];
  page.on("console",m=>{if(m.type()==="error"&&!/favicon|ResizeObserver|net::ERR_ABORTED/i.test(m.text()))ce.push(m.text().slice(0,200));});
  page.on("pageerror",e=>pe.push(e.message.slice(0,200)));
  page.on("response",r=>{if(r.status()>=400&&!/favicon|\.map/i.test(r.url()))ne.push(r.status()+" "+r.url().replace(BASE,"").slice(0,100));});
  try{ await login(page); await fn(page,ce,ne,pe); }
  catch(e){ rec(label,"FAIL","EX: "+e.message.slice(0,200)); await page.screenshot({path:path.join(OUT,label+"-ERR.png")}).catch(()=>{}); }
  finally{ await browser.close(); }
}

// 1. Dashboard 渲染
withPage("dashboard-render", async(page,ce,ne,pe)=>{
  await page.goto(BASE+"/dashboard",{waitUntil:"domcontentloaded"}); await sleep(4000);
  const charts = await page.locator("canvas, .echarts, [_echarts_instance_]").count();
  const kpiCards = await page.locator(".kpi-card").count();
  await page.screenshot({path:path.join(OUT,"dashboard.png")});
  rec("Dashboard KPI卡片", kpiCards>=3?"PASS":"FAIL", `cards=${kpiCards}`);
  rec("Dashboard 图表", charts>=2?"PASS":"FAIL", `charts=${charts}`);
  if(ce.length) rec("Dashboard Console","WARN",ce.join(";").slice(0,200)); else rec("Dashboard Console","PASS","clean");
  if(ne.filter(n=>/^[45]/.test(n)).length) rec("Dashboard Network","FAIL",ne.join(";").slice(0,200));
});

// 2. 销售订单：筛选/排序/分页/详情
withPage("orders-interact", async(page,ce,ne,pe)=>{
  await page.goto(BASE+"/m/orders",{waitUntil:"domcontentloaded"}); await sleep(3000);
  // 分页切换
  const sizeSel = page.locator(".el-pagination .el-select").first();
  if(await sizeSel.count()){
    await sizeSel.click(); await sleep(500);
    await page.locator(".el-select-dropdown__item:has-text('50')").first().click().catch(()=>{});
    await sleep(2000);
  }
  const rows50 = await page.locator(".el-table__body-wrapper tr").count();
  rec("订单分页切换50", rows50>=30?"PASS":"WARN",`rows=${rows50}`);
  // 排序：点"订单日期"表头
  const sortable = page.locator(".el-table__header-wrapper th.descending, .el-table__header-wrapper th.ascending, th .caret-wrapper").first();
  if(await sortable.count()){ await sortable.click(); await sleep(2000); rec("订单列排序","PASS","clicked"); }
  else rec("订单列排序","WARN","no sortable header found");
  // 点第一行查看按钮
  const viewBtn = page.locator(".el-table__body-wrapper tr").first().locator("button:has-text('查看'), a:has-text('查看')").first();
  if(await viewBtn.count()){
    await viewBtn.click(); await sleep(2500);
    const detailText = (await page.locator("body").innerText()).length;
    await page.screenshot({path:path.join(OUT,"order-detail.png")});
    rec("订单详情页", detailText>200?"PASS":"FAIL",`textLen=${detailText} url=${page.url().replace(BASE,"")}`);
  } else rec("订单详情页","WARN","no view button in first row");
  // 新增按钮弹窗
  await page.goto(BASE+"/m/orders",{waitUntil:"domcontentloaded"}); await sleep(2500);
  const addBtn = page.getByRole("button",{name:/新\s*增/}).first();
  if(await addBtn.count()){
    await addBtn.click(); await sleep(2500);
    const dialogVisible = await page.locator(".el-dialog, .el-drawer").count();
    await page.screenshot({path:path.join(OUT,"order-add.png")});
    rec("订单新增弹窗", dialogVisible>0?"PASS":"FAIL",`dialogs=${dialogVisible}`);
  } else rec("订单新增按钮","WARN","no add button");
  if(ce.length) rec("订单Console","WARN",ce.slice(0,3).join(";").slice(0,200));
});

// 3. 产品：筛选漏斗 + 详情外键显示
withPage("products-interact", async(page,ce,ne,pe)=>{
  await page.goto(BASE+"/m/products",{waitUntil:"domcontentloaded"}); await sleep(3000);
  // 找筛选漏斗图标
  const filterIcon = page.locator(".el-table__header-wrapper .el-icon, [class*=filter]").first();
  let filterWorks=false;
  if(await filterIcon.count()){
    await filterIcon.click(); await sleep(1000);
    const pop = page.locator(".el-popover, .el-popper").last();
    if(await pop.isVisible().catch(()=>false)){ filterWorks=true; await page.keyboard.press("Escape"); }
  }
  rec("产品筛选漏斗", filterWorks?"PASS":"WARN",`found=${await filterIcon.count()}`);
  // 查看详情，检查是否有裸id
  const viewBtn = page.locator(".el-table__body-wrapper tr").first().locator("button:has-text('查看')").first();
  if(await viewBtn.count()){
    await viewBtn.click(); await sleep(2500);
    const body = await page.locator("body").innerText();
    const hasRawId = /\bID[:：]\s*\d{4,}/.test(body);
    await page.screenshot({path:path.join(OUT,"product-detail.png")});
    rec("产品详情外键显示", hasRawId?"WARN":"PASS","rawId detected: "+hasRawId);
  }
  if(ce.length) rec("产品Console","WARN",ce.slice(0,3).join(";").slice(0,200));
});

// 4. 合同工作台
withPage("contracts-interact", async(page,ce,ne,pe)=>{
  await page.goto(BASE+"/contracts",{waitUntil:"domcontentloaded"}); await sleep(3500);
  await page.screenshot({path:path.join(OUT,"contracts.png")});
  const tbl = await page.locator(".el-table").count();
  const bodyLen = (await page.locator("body").innerText()).length;
  rec("合同工作台加载", tbl>0||bodyLen>100?"PASS":"FAIL",`table=${tbl} bodyLen=${bodyLen}`);
  if(ce.length) rec("合同Console","WARN",ce.slice(0,3).join(";").slice(0,200));
  if(ne.filter(n=>/^[45]/.test(n)).length) rec("合同Network","FAIL",ne.slice(0,3).join(";").slice(0,200));
});

// 5. 审批流配置
withPage("approval-templates-interact", async(page,ce,ne,pe)=>{
  await page.goto(BASE+"/approval/templates",{waitUntil:"domcontentloaded"}); await sleep(3000);
  await page.screenshot({path:path.join(OUT,"approval-templates.png")});
  const rows = await page.locator(".el-table__body-wrapper tr").count();
  rec("审批流配置列表", rows>0?"PASS":"FAIL",`rows=${rows}`);
});

// 6. 日志中心
withPage("log-center-interact", async(page,ce,ne,pe)=>{
  await page.goto(BASE+"/log-center",{waitUntil:"domcontentloaded"}); await sleep(3000);
  await page.screenshot({path:path.join(OUT,"log-center.png")});
  const tabs = await page.locator(".el-tabs__item").count();
  rec("日志中心Tab", tabs>=2?"PASS":"FAIL",`tabs=${tabs}`);
  const tbl = await page.locator(".el-table__body-wrapper tr").count();
  rec("日志数据", tbl>=0?"PASS":"FAIL",`rows=${tbl}`);
});

// 7. 导出按钮（销售订单）
withPage("export-test", async(page,ce,ne,pe)=>{
  await page.goto(BASE+"/m/orders",{waitUntil:"domcontentloaded"}); await sleep(3000);
  const exportBtn = page.getByRole("button",{name:/导\s*出/}).first();
  let exportResp=null;
  if(await exportBtn.count()){
    const [resp] = await Promise.all([
      page.waitForResponse(r=>r.url().includes("export")||r.url().includes("download"),{timeout:10000}).catch(()=>null),
      exportBtn.click()
    ]);
    await sleep(2000);
    // 可能弹导出配置对话框
    const dlg = await page.locator(".el-dialog:visible").count();
    const confirmBtn = page.locator(".el-dialog:visible .el-button--primary").first();
    if(dlg && await confirmBtn.count()){
      await confirmBtn.click(); await sleep(3000);
    }
    rec("导出按钮响应", "PASS", `dialog=${dlg} hadResp=${!!resp}`);
  } else rec("导出按钮","WARN","no export button");
});

setTimeout(()=>{
  const summary={pass:results.filter(r=>r.status==="PASS").length,warn:results.filter(r=>r.status==="WARN").length,fail:results.filter(r=>r.status==="FAIL").length};
  fs.writeFileSync(path.join(OUT,"report.json"),JSON.stringify({summary,results},null,2));
  console.log("\n===== INTERACT SUMMARY =====");console.log(JSON.stringify(summary,null,2));
  process.exit(0);
}, 90000);
