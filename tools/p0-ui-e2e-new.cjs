const { chromium } = require("playwright");
const BASE = process.env.E2E_BASE || "http://43.128.145.141:8083";
const results = [];
function check(n,c,d=""){results.push([n,!!c,""+d]);console.log((c?"PASS":"FAIL"),n,d);}
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
async function mainText(page){
  // prefer the main content area so sidebar doesn't mask failures
  for (const sel of [".el-main","main",".page-content"]) {
    const el=page.locator(sel).first();
    if (await el.count()>0) { try { return (await el.innerText())||""; } catch(e){} }
  }
  return await page.locator("body").innerText().catch(()=>"");
}
async function visit(page,path,re){
  try{await page.goto(BASE+path,{waitUntil:"domcontentloaded",timeout:25000});}catch(e){}
  await sleep(2500);
  try{await page.waitForSelector(".el-table,.el-card,.van-cell,.el-empty,.el-pagination,.van-empty",{timeout:8000});}catch(e){}
  const t=await mainText(page);
  const noRoute=/404|页面不存在|找不到该页面/.test(t);
  const ok=!noRoute&&(!re||re.test(t));
  check(path,ok,t.replace(/\s+/g," ").slice(0,60));
}
(async()=>{
  const browser=await chromium.launch({headless:true});
  const ctx=await browser.newContext({viewport:{width:1440,height:900},ignoreHTTPSErrors:true});
  const page=await ctx.newPage();
  await page.goto(BASE+"/login",{waitUntil:"domcontentloaded"}); await sleep(800);
  await page.fill('input[placeholder="账号"]',"sys_admin");
  await page.fill('input[type=password]',"Dms@123456");
  await page.keyboard.press("Enter"); await sleep(3500);
  check("pc login",!page.url().includes("/login"),page.url());
  const pc=[["/home",/.{3}/],["/products",/产品/],["/orders",/订单/],["/contracts",/合同/],
    ["/contracts/templates",/模板|合同/],["/reports",/报表/],["/approval/todo",/审批/],
    ["/notifications",/通知|消息|审批待办/],["/login-logs",/登录|结果|用户名/]];
  for(const [p,re] of pc) await visit(page,p,re);

  const ap=await ctx.newPage();
  await ap.goto(BASE+"/admin/login",{waitUntil:"domcontentloaded"}); await sleep(800);
  await ap.fill('input[placeholder="用户名"]',"admin");
  await ap.fill('input[type=password]',"Sh123456");
  await ap.keyboard.press("Enter"); await sleep(3500);
  check("admin login",!ap.url().includes("/admin/login"),ap.url());
  const adm=[["/admin/tenants/manufacturers",/厂家|租户/],["/admin/tenants/dealers",/经销商/],
    ["/admin/role-templates",/.{3}/],["/admin/menus",/菜单/],["/admin/dicts",/字典/],
    ["/admin/logs/api",/.{3}/],["/admin/logs/audits",/.{3}/]];
  for(const [p,re] of adm) await visit(ap,p,re);

  const mctx=await browser.newContext({viewport:{width:390,height:844},isMobile:true,hasTouch:true,
    userAgent:"Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148",ignoreHTTPSErrors:true});
  const mp=await mctx.newPage();
  await mp.goto(BASE+"/mobile/login",{waitUntil:"domcontentloaded"}); await sleep(800);
  await mp.fill('input[placeholder="请输入账号"]',"sys_admin");
  await mp.fill('input[placeholder="请输入密码"]',"Dms@123456");
  await mp.keyboard.press("Enter"); await sleep(4000);
  check("mobile login",!mp.url().includes("/mobile/login"),mp.url());
  const mob=[["/mobile/home",/工作台|首页|今日/],["/mobile/approvals",/审批/],
    ["/mobile/surgery-reports",/手术|报台/],["/mobile/surgery-reports/create",/手术|报台|患者|照片/],
    ["/mobile/messages",/消息|通知/]];
  for(const [p,re] of mob) await visit(mp,p,re);
  await mp.goto(BASE+"/mobile/surgery-reports/create",{waitUntil:"domcontentloaded"}); await sleep(2500);
  const up=await mp.locator(".van-uploader,input[type=file]").count();
  check("mobile uploader",up>0,"count="+up);

  const failed=results.filter(r=>!r[1]);
  console.log(JSON.stringify({total:results.length,passed:results.length-failed.length,failed:failed.length}));
  if(failed.length) console.log("FAILED:",failed.map(f=>f[0]).join(", "));
  await browser.close();
  process.exit(failed.length?1:0);
})();
