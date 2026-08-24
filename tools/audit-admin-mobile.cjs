// tools/audit-admin-mobile.cjs
const { chromium } = require("playwright");
const fs = require("fs"); const path = require("path");
const args = process.argv.slice(2);
function arg(n,d){const h=args.find(a=>a.startsWith("--"+n+"="));return h?h.split("=").slice(1).join("="):d;}
const BASE = (arg("base")||"http://43.128.145.141/dms").replace(/\/$/,"");
const STAMP = new Date().toISOString().replace(/[-:T.Z]/g,"").slice(0,14);
const OUT = path.join(__dirname,"..","automation_test","v4-browser-results","audit-admin-mobile-"+STAMP);
fs.mkdirSync(OUT,{recursive:true});
const results=[]; const sleep=ms=>new Promise(r=>setTimeout(r,ms));
function rec(name,status,detail){results.push({name,status,detail:String(detail||"").slice(0,400)});console.log(`[${status}] ${name}${detail?" | "+String(detail).slice(0,150):""}`);}

const ADMIN_PAGES = [
  ["mfr-tenants","厂家租户","/admin/tenants/manufacturers"],
  ["dealer-tenants","经销商租户","/admin/tenants/dealers"],
  ["tenant-admins","租户管理员","/admin/tenant-admins"],
  ["role-templates","角色模板","/admin/role-templates"],
  ["menus","平台菜单","/admin/menus"],
  ["ui-configs","页面配置","/admin/ui-configs"],
  ["dicts","全局字典","/admin/dicts"],
  ["api-logs","接口日志","/admin/logs/api"],
  ["audit-logs","审计日志","/admin/logs/audits"],
  ["reports","报表总览","/admin/reports"],
];
const MOBILE_PAGES = [
  ["m-home","首页","/mobile/home"],
  ["m-orders","销售订单","/mobile/orders"],
  ["m-surgery","手术报台","/mobile/surgery-reports"],
  ["m-dashboard","我的业绩","/mobile/dashboard"],
  ["m-approvals","移动审批","/mobile/approvals"],
  ["m-messages","消息中心","/mobile/messages"],
  ["m-profile","我的","/mobile/profile"],
];

async function auditPage(page, label, url, isMobile){
  const ce=[],ne=[],pe=[];
  const oc=m=>{if(m.type()==="error"){const t=m.text();if(!/favicon|ResizeObserver|net::ERR_ABORTED/i.test(t))ce.push(t.slice(0,250));}};
  const oe=e=>pe.push(e.message.slice(0,250));
  const or=r=>{if(r.status()>=400){const u=r.url();if(!/favicon|\.map/i.test(u))ne.push(r.status()+" "+u.replace(BASE,"").slice(0,100));}};
  page.on("console",oc);page.on("pageerror",oe);page.on("response",or);
  try{
    const resp=await page.goto(BASE+url,{waitUntil:"domcontentloaded",timeout:25000});
    await sleep(isMobile?2500:3000);
    const body=(await page.locator("body").innerText().catch(()=>"")).trim();
    const blank=body.length<15;
    const has404=/404/.test(body)&&body.length<100;
    await page.screenshot({path:path.join(OUT,label+".png")}).catch(()=>{});
    let status="PASS";
    if(blank||has404||pe.length)status="FAIL";
    else if(ce.length||ne.filter(n=>/^[45]/.test(n)).length)status="WARN";
    rec(label,status,`http=${resp?resp.status():null} bodyLen=${body.length} ce=${ce.length} ne=${ne.length} pe=${pe.length}`);
  }catch(e){ await page.screenshot({path:path.join(OUT,label+"-ERR.png")}).catch(()=>{}); rec(label,"FAIL","EX: "+e.message.slice(0,150)); }
  finally{ page.off("console",oc);page.off("pageerror",oe);page.off("response",or); }
}

(async()=>{
  const browser=await chromium.launch({headless:true});
  // ---- ADMIN ----
  const actx=await browser.newContext({viewport:{width:1600,height:900}});
  const ap=await actx.newPage();
  await ap.goto(BASE+"/admin/login",{waitUntil:"domcontentloaded"});await sleep(1500);
  await ap.locator('input[placeholder*="用户"],input[placeholder*="账号"]').first().fill("admin").catch(()=>{});
  await ap.locator('input[type="password"]').first().fill("Sh123456").catch(()=>{});
  await ap.getByRole("button",{name:/登/}).first().click().catch(()=>{ap.keyboard.press("Enter");});
  await ap.waitForURL(u=>!u.pathname.includes("/admin/login"),{timeout:12000}).catch(()=>{});
  await sleep(2500);
  rec("Admin登录", ap.url().includes("/admin/login")?"FAIL":"PASS","url="+ap.url());
  for(const [k,l,u] of ADMIN_PAGES) await auditPage(ap,l,u,false);
  await actx.close();

  // ---- MOBILE ----
  const mctx=await browser.newContext({viewport:{width:393,height:852},isMobile:true,hasTouch:true,userAgent:"Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148"});
  const mp=await mctx.newPage();
  await mp.goto(BASE+"/mobile/login",{waitUntil:"domcontentloaded"});await sleep(1500);
  await mp.locator('input[placeholder*="租"]').first().fill("default").catch(()=>{});
  await mp.locator('input[placeholder*="账"]').first().fill("admin").catch(()=>{});
  await mp.locator('input[type="password"]').first().fill("Sh123456").catch(()=>{});
  await mp.getByRole("button",{name:/登/}).first().click().catch(()=>mp.keyboard.press("Enter"));
  await mp.waitForURL(u=>!u.pathname.includes("/mobile/login"),{timeout:12000}).catch(()=>{});
  await sleep(2500);
  rec("Mobile登录", mp.url().includes("/mobile/login")?"FAIL":"PASS","url="+mp.url());
  for(const [k,l,u] of MOBILE_PAGES) await auditPage(mp,l,u,true);
  await mctx.close();

  await browser.close();
  const summary={stamp:STAMP,out:OUT,pass:results.filter(r=>r.status==="PASS").length,warn:results.filter(r=>r.status==="WARN").length,fail:results.filter(r=>r.status==="FAIL").length};
  fs.writeFileSync(path.join(OUT,"report.json"),JSON.stringify({summary,results},null,2));
  console.log("\n===== SUMMARY =====");console.log(JSON.stringify(summary,null,2));
})();

