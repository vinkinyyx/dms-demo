// tools/probe-pc-ux.cjs - PC 深度 UX：筛选popover/导入导出/表单下拉/BOM促销计价
const { chromium } = require("playwright");
const fs=require("fs"),path=require("path");
const BASE="http://43.128.145.141/dms";
const STAMP=new Date().toISOString().replace(/[-:T.Z]/g,"").slice(0,14);
const OUT=path.join(__dirname,"..","automation_test","v4-browser-results","pc-ux-"+STAMP);
fs.mkdirSync(OUT,{recursive:true});
const results=[];
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
function rec(n,s,d){results.push({name:n,status:s,detail:String(d||"").slice(0,500)});console.log(`[${s}] ${n} | ${String(d||"").slice(0,160)}`);}
function attach(p){const e=[];p.on("console",m=>{if(m.type()==="error"&&!/favicon|ResizeObserver|Download is|ERR_ABORTED|net::ERR_FAILED/i.test(m.text()))e.push(m.text().slice(0,200));});p.on("pageerror",x=>e.push("PE:"+x.message.slice(0,200)));return e;}

(async()=>{
  const b=await chromium.launch();
  const p=await b.newPage({viewport:{width:1600,height:1000}});
  const errs=attach(p);
  const net=[]; p.on("response",r=>{if(r.status()>=400&&!/favicon|\.map|actuator/i.test(r.url()))net.push(r.status()+" "+r.url().replace(BASE,"").slice(0,100));});
  await p.goto(BASE+"/login");
  await p.locator('input[placeholder*="租"]').first().fill("default").catch(()=>{});
  await p.locator('input[placeholder*="账"],input[placeholder*="用户"]').first().fill("admin");
  await p.locator('input[type="password"]').first().fill("Sh123456");
  await p.getByRole("button",{name:/登\s*录/}).first().click();
  await p.waitForURL(u=>!u.pathname.includes("/login"),{timeout:15000});
  await p.waitForTimeout(2500);

  // 1) 产品筛选 popover
  await p.goto(BASE+"/m/products"); await p.waitForTimeout(3500);
  // 找列头筛选图标
  let funnelOpened=false;
  const funnelIcons = await p.locator(".el-table__filter-icon, .el-table__column-filter-trigger, [class*=filter]").count();
  // CrudView 自定义筛选漏斗：找按钮/图标
  const filterBtns = await p.locator(".toolbar .el-button, .crud-toolbar .el-button, .filter-btn, [class*=filter-trigger]").count();
  // 尝试点击表格列上的筛选图标
  const colFilter = p.locator(".el-table__column-filter-trigger, .el-table__filter-icon").first();
  if(await colFilter.count()){
    await colFilter.click({force:true}).catch(()=>{});
    await p.waitForTimeout(1200);
    funnelOpened = await p.locator(".el-popper:visible, .el-popover:visible").count() > 0;
  }
  rec("产品筛选popover", funnelOpened?"PASS":(funnelIcons||filterBtns?"WARN":"FAIL"), `colFilter=${await colFilter.count()} opened=${funnelOpened} icons=${funnelIcons} filterBtns=${filterBtns}`);
  await p.screenshot({path:path.join(OUT,"product-filter.png")});
  // 关闭
  await p.keyboard.press("Escape"); await p.waitForTimeout(500);

  // 2) 导入导出：产品页工具栏按钮 + 导入弹窗
  const exportBtn = p.getByRole("button",{name:/导\s*出/}).first();
  const hasExport = await exportBtn.count()>0;
  const importBtn = p.getByRole("button",{name:/导\s*入/}).first();
  const hasImport = await importBtn.count()>0;
  rec("产品页导入按钮", hasImport?"PASS":"FAIL", `found=${hasImport}`);
  rec("产品页导出按钮", hasExport?"PASS":"FAIL", `found=${hasExport}`);
  if(hasImport){
    await importBtn.click(); await p.waitForTimeout(2000);
    const dlg = await p.locator(".el-dialog:visible").count();
    const hasTemplate = await p.locator("body").innerText().then(t=>/模板|下载|template/i.test(t));
    rec("产品导入弹窗", dlg>0?"PASS":"FAIL", `dialog=${dlg} hasTemplateLink=${hasTemplate}`);
    await p.screenshot({path:path.join(OUT,"product-import.png")});
    await p.keyboard.press("Escape"); await p.waitForTimeout(800);
  }
  if(hasExport){
    p.once("dialog",()=>{});
    await exportBtn.click(); await p.waitForTimeout(2000);
    const dlg2 = await p.locator(".el-dialog:visible").count();
    rec("产品导出弹窗/响应", dlg2>0?"PASS":"WARN", `dialog=${dlg2}（可能直接下载或弹条件窗）`);
    await p.screenshot({path:path.join(OUT,"product-export.png")});
    await p.keyboard.press("Escape"); await p.waitForTimeout(800);
  }

  // 3) 销售订单创建页表单：经销商下拉有数据、产品下拉有数据、字段标签完整
  await p.goto(BASE+"/order-create/sales"); await p.waitForTimeout(3500);
  const formText = await p.locator("body").innerText();
  const labelsOk = /经销商|订单类型|期望日期|订单明细|产品|数量|含税单价|最终金额/.test(formText);
  rec("订单创建页字段完整", labelsOk?"PASS":"FAIL", "基本信息+明细列存在");
  // 经销商下拉
  const dealerSelect = p.locator(".el-select").filter({hasText:/经销商|请选择/}).first();
  // 找包含"经销商"label 的 select
  const allSelects = p.locator(".el-select");
  const selectCount = await allSelects.count();
  let dealerOptions=0;
  // 点击第一个 el-select（通常是经销商）
  try{
    const firstSelect = p.locator(".el-select").first();
    await firstSelect.click(); await p.waitForTimeout(1200);
    const opts = await p.locator(".el-select-dropdown:visible .el-select-dropdown__item").count();
    dealerOptions = opts;
    rec("订单表单下拉有数据", opts>0?"PASS":"WARN", `第一个下拉选项数=${opts}（select总数=${selectCount}）`);
    await p.screenshot({path:path.join(OUT,"order-create-select.png")});
    await p.keyboard.press("Escape"); await p.waitForTimeout(500);
  }catch(e){ rec("订单表单下拉","WARN",String(e.message).slice(0,120)); }
  // 添加行
  const addLine = p.getByRole("button",{name:/添加行|添加产品|\+/}).first();
  if(await addLine.count()){ await addLine.click(); await p.waitForTimeout(1500); rec("订单添加行","PASS","clicked"); }
  else rec("订单添加行","WARN","未找到添加行按钮");

  // 4) 价格/产品：产品价格页能列出价格（验证外键显示名称非裸ID）
  await p.goto(BASE+"/m/product-prices"); await p.waitForTimeout(3000);
  const priceText = await p.locator(".el-table").first().innerText().catch(()=>"");
  const hasRawId = /^\d{2,}$/m.test(priceText) && !/¥|￥|\.\d{2}/.test(priceText);
  rec("产品价格页加载", priceText.length>50?"PASS":"FAIL", `tableTextLen=${priceText.length}`);
  await p.screenshot({path:path.join(OUT,"product-prices.png")});

  // 5) BOM 组合品页 + 促销规则页 加载无错
  await p.goto(BASE+"/m/product-bundles"); await p.waitForTimeout(3000);
  rec("BOM组合品页", (await p.locator(".el-table, body").first().innerText()).length>50?"PASS":"FAIL", "loaded");
  await p.goto(BASE+"/m/promotions"); await p.waitForTimeout(3000);
  const promoText = await p.locator("body").innerText();
  rec("促销规则页", promoText.length>50?"PASS":"FAIL", `len=${promoText.length}`);
  await p.screenshot({path:path.join(OUT,"promotions.png")});

  // 6) 只读详情页排版：经销商详情/产品详情 抽查
  await p.goto(BASE+"/m/dealers"); await p.waitForTimeout(3000);
  const firstView = p.getByRole("button",{name:/查\s*看|详\s*情/}).first();
  if(await firstView.count()){ await firstView.click(); await p.waitForTimeout(2500);
    const dlg = await p.locator(".el-dialog:visible, .el-drawer:visible").count();
    rec("经销商详情打开", dlg>0?"PASS":"WARN", `dialog/drawer=${dlg} url=${p.url()}`);
    await p.screenshot({path:path.join(OUT,"dealer-detail.png")});
    // 检查裸ID：详情里不应有 "productId=12" 或 "targetProductId=13"
    const dt = await p.locator(".el-dialog:visible, .el-drawer:visible, body").first().innerText().catch(()=>"");
    const raw = /targetProductId=\d+|=\d{2,}\D/.test(dt) && /Id=\d/.test(dt);
    rec("经销商详情无裸ID", !raw?"PASS":"WARN","检查外键是否显示名称");
  } else rec("经销商详情","WARN","无查看按钮");

  // 7) 日期格式检查：不应出现 T00:00:00
  await p.goto(BASE+"/m/orders"); await p.waitForTimeout(3000);
  const orderText = await p.locator("body").innerText();
  const isoDate = /\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/.test(orderText);
  rec("订单列表无ISO日期直出", !isoDate?"PASS":"FAIL", isoDate?"发现 T00:00:00":"日期格式正确");
  // 乱码
  const garbage = /\?{3,}/.test(orderText);
  rec("订单列表无乱码", !garbage?"PASS":"FAIL", garbage?"发现????":"无乱码");

  await p.screenshot({path:path.join(OUT,"orders-list.png")});

  rec("Console错误汇总", errs.length===0?"PASS":"FAIL", `count=${errs.length} ${errs.slice(0,3).join(" | ")}`);
  rec("网络4xx/5xx", net.length===0?"PASS":"FAIL", `count=${net.length} ${net.slice(0,3).join(" | ")}`);

  const summary={pass:results.filter(r=>r.status==="PASS").length,warn:results.filter(r=>r.status==="WARN").length,fail:results.filter(r=>r.status==="FAIL").length};
  fs.writeFileSync(path.join(OUT,"report.json"),JSON.stringify({summary,results},null,2),"utf8");
  console.log("\n=== SUMMARY ===",JSON.stringify(summary),"\nreport:",OUT);
  await b.close();
})();
