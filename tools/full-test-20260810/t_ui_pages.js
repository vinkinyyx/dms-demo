const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const OUT = path.join(__dirname,'results','ui-pages');
if(!fs.existsSync(OUT)) fs.mkdirSync(OUT,{recursive:true});
const results=[];
function rec(scope,id,title,status,detail){results.push({scope,id,title,status,detail:String(detail||'').slice(0,500)});const tag=status==='PASS'?'PASS':status==='FAIL'?'FAIL':status==='WARN'?'WARN':'INFO';console.log(`[${tag}] ${scope} ${id} ${title} :: ${String(detail||'').slice(0,200)}`)}

const PAGES = [
  ['BASIC','/home','工作台首页'],
  ['BASIC','/module/products','产品管理'],
  ['BASIC','/module/categories','产品分类'],
  ['BASIC','/module/dealers','经销商管理'],
  ['BASIC','/module/hospitals','医院/终端'],
  ['BASIC','/module/warehouses','仓库管理'],
  ['BASIC','/module/suppliers','供应商'],
  ['CONTRACT','/module/contracts','合同'],
  ['CONTRACT','/module/authorizations','授权管理'],
  ['ORDER','/module/orders','销售订单'],
  ['ORDER','/module/sales-returns','销退订单'],
  ['ORDER','/module/purchase-orders','采购订单'],
  ['ORDER','/module/purchase-returns','采退订单'],
  ['INV','/module/inventory','库存查询'],
  ['INV','/module/sales-outs','销售出库'],
  ['INV','/module/receipts','收货入库'],
  ['INV','/module/stock-moves','库存移动'],
  ['INV','/module/inventory-adjustments','库存调整'],
  ['SURG','/module/surgery-reports','手术植入报告'],
  ['PROMO','/module/promotions','促销规则'],
  ['DASH','/dashboard','数据看板'],
  ['RPT','/module/report-order-trace','订单追踪'],
  ['USER','/positions','销售岗位'],
  ['USER','/module/users','账号管理'],
  ['USER','/module/roles','角色管理'],
  ['MAP','/product-mappings','产品对码'],
  // extra known routes
  ['CTPL','/contracts/templates','合同模板'],
  ['APPR','/approvals','审批中心'],
  ['CFG','/tenant-page-configs','列表页配置'],
  ['LOG','/api-call-logs','接口调用日志'],
  ['LOG','/email-logs','邮件日志'],
  ['SYS','/system-settings','系统设置'],
];

(async()=>{
  const browser = await chromium.launch({headless:true});
  const ctx = await browser.newContext({viewport:{width:1440,height:900}});
  const page = await ctx.newPage();
  // login
  await page.goto('http://8.133.193.238:8083/login',{waitUntil:'networkidle'});
  await page.fill('input[placeholder="账号"]','sys_admin');
  await page.fill('input[type="password"]','Dms@123456');
  await page.click('button:has-text("登 录")');
  await page.waitForURL(/\/home/);
  await page.waitForTimeout(2000);

  for (const [scope,route,name] of PAGES) {
    const errors=[];
    const onConsole = m => { if(m.type()==='error') errors.push(m.text()); };
    const onPageError = e => errors.push('PAGEERROR:'+e.message);
    page.on('console',onConsole);
    page.on('pageerror',onPageError);
    try {
      const t0 = Date.now();
      const resp = await page.goto('http://8.133.193.238:8083'+route,{waitUntil:'networkidle',timeout:20000});
      await page.waitForTimeout(1500);
      const elapsed = Date.now()-t0;
      const url = page.url();
      const bodyTxt = await page.evaluate(()=>document.body.innerText);
      const bodyLen = bodyTxt.length;
      const hasBlank = bodyLen < 50;
      const has404 = /404|Not Found|页面不存在|找不到/.test(bodyTxt);
      const has500 = /500|服务器错误|系统异常/.test(bodyTxt);
      const tables = await page.locator('table,.el-table').count();
      const btns = await page.locator('button').count();
      const inputs = await page.locator('input').count();
      const cards = await page.locator('.el-card').count();
      const finalStatus = resp ? resp.status() : 'no-response';
      let status='PASS';
      let detail=`HTTP ${finalStatus} ${elapsed}ms bodyLen=${bodyLen} tables=${tables} btns=${btns} inputs=${inputs} cards=${cards}`;
      if (hasBlank) { status='FAIL'; detail+=' | WHITE/BLANK PAGE'; }
      if (has404) { status='FAIL'; detail+=' | 404 NOT FOUND'; }
      if (has500) { status='FAIL'; detail+=' | 500 SERVER ERROR'; }
      if (errors.length>0) { status = status==='PASS'?'WARN':status; detail+=` | console errors=${errors.length}: ${errors.slice(0,3).join('; ')}`; }
      // screenshot
      await page.screenshot({path:path.join(OUT,`${scope}-${route.replace(/\//g,'_')}.png`),fullPage:false}).catch(()=>{});
      rec(scope,route,name,status,detail);
    } catch(e) {
      rec(scope,route,name,'FAIL',`Exception: ${e.message}`);
    } finally {
      page.off('console',onConsole);
      page.off('pageerror',onPageError);
    }
  }
  fs.writeFileSync(path.join(__dirname,'results','ui-pages.json'),JSON.stringify(results,null,2));
  const summary = results.reduce((a,r)=>{a[r.status]=(a[r.status]||0)+1;return a;},{});
  console.log('\n=== PAGE SUMMARY ===',JSON.stringify(summary));
  await browser.close();
})();
