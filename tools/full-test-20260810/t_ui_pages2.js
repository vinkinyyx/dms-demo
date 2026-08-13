const { chromium } = require('playwright');
const fs = require('fs'), path=require('path');
const OUT = path.join(__dirname,'results','ui-pages-v2');
if(!fs.existsSync(OUT)) fs.mkdirSync(OUT,{recursive:true});
const results=[];
function rec(s,i,t,d,x){results.push({scope:s,id:i,title:t,status:d,detail:String(x||'').slice(0,800)});const tag=d;const line=`[${tag}] ${s} ${i} ${t} :: ${String(x||'').slice(0,250)}`; if(d==='FAIL')console.error(line);else console.log(line)}

const PAGES = [
  ['HOME','/home','工作台首页'],
  ['BASIC','/m/products','产品管理'],
  ['BASIC','/m/categories','产品分类'],
  ['BASIC','/m/product-lines','产品线管理'],
  ['BASIC','/m/product-package-levels','产品包装层级'],
  ['BASIC','/m/product-bundles','产品组合'],
  ['BASIC','/m/dealers','经销商管理'],
  ['BASIC','/m/hospitals','医院终端'],
  ['BASIC','/m/warehouses','仓库管理'],
  ['BASIC','/m/regions','区域管理'],
  ['BASIC','/m/suppliers','供应商'],
  ['BASIC','/m/product-prices','产品价格'],
  ['CONTRACT','/contracts','合同工作台'],
  ['CONTRACT','/contracts/templates','合同模板'],
  ['CONTRACT','/m/authorizations','授权管理'],
  ['ORDER','/m/orders','销售订单'],
  ['ORDER','/m/sales-returns','销退订单'],
  ['ORDER','/m/purchase-orders','采购订单'],
  ['ORDER','/m/purchase-returns','采退订单'],
  ['INV','/m/inventory','库存查询'],
  ['INV','/m/sales-outs','销售出库'],
  ['INV','/m/receipts','收货入库'],
  ['INV','/m/stock-moves','库存移动'],
  ['INV','/m/inventory-adjustments','库存调整'],
  ['SURG','/m/surgery-reports','手术植入报台'],
  ['PROMO','/m/promotions','促销规则'],
  ['DASH','/dashboard','数据驾驶舱'],
  ['RPT','/reports?key=sales-ranking','销售业绩排行'],
  ['RPT','/reports?key=product-top10','产品TOP10'],
  ['RPT','/reports?key=inventory-turnover','库存周转'],
  ['RPT','/reports?key=surgery-stats','手术统计'],
  ['RPT','/reports?key=receivables','应收款项'],
  ['RPT','/reports?key=order-trace','订单追溯'],
  ['DEALER','/dealers/profile','经销商画像'],
  ['APPR','/approval/todo','我的审批'],
  ['APPR','/approval/templates','审批流配置'],
  ['APPR','/approval/delegations','审批委托'],
  ['APPR','/approval/admin','审批监控'],
  ['USER','/positions','销售岗位'],
  ['USER','/m/users','账号管理'],
  ['USER','/roles-manage','角色权限'],
  ['CFG','/tenant-page-configs','列表页配置'],
  ['LOG','/api-call-logs','接口调用日志'],
  ['LOG','/email-logs','邮件日志'],
  ['MAP','/product-mappings','产品对码'],
];

(async()=>{
  const browser = await chromium.launch({headless:true});
  const ctx = await browser.newContext({viewport:{width:1440,height:900}});
  const page = await ctx.newPage();
  const allErrors=[];
  page.on('console',m=>{if(m.type()==='error')allErrors.push({url:page.url(),msg:m.text()})});
  page.on('pageerror',e=>allErrors.push({url:page.url(),msg:'PE:'+e.message}));
  await page.goto('http://8.133.193.238:8083/login',{waitUntil:'networkidle'});
  await page.fill('input[placeholder="账号"]','sys_admin');
  await page.fill('input[type="password"]','Dms@123456');
  await page.click('button:has-text("登 录")');
  await page.waitForURL(/\/home/);
  await page.waitForTimeout(2000);

  for (const [scope,route,name] of PAGES) {
    const errBefore = allErrors.length;
    try {
      const t0=Date.now();
      const resp = await page.goto('http://8.133.193.238:8083'+route,{waitUntil:'networkidle',timeout:20000});
      await page.waitForTimeout(2000);
      const elapsed=Date.now()-t0;
      const info = await page.evaluate(()=>{
        const body = document.body.innerText;
        const tables = document.querySelectorAll('table,.el-table').length;
        const btns = document.querySelectorAll('button').length;
        const inputs = document.querySelectorAll('input').length;
        const cards = document.querySelectorAll('.el-card').length;
        const forms = document.querySelectorAll('.el-form').length;
        const empty = document.querySelectorAll('.el-empty,.el-table__empty-text').length;
        const dialogs = document.querySelectorAll('.el-dialog').length;
        const errorText = /系统异常|服务器错误|500|网络错误|Cannot read|undefined is not|null is not/.test(body);
        const notFound = /404|页面不存在|找不到/.test(body);
        const whiteScreen = body.length < 100;
        return {bodyLen:body.length, bodyStart:body.slice(0,200), tables, btns, inputs, cards, forms, empty, dialogs, errorText, notFound, whiteScreen};
      });
      const newErrors = allErrors.slice(errBefore);
      let status='PASS';
      let detail=`HTTP ${resp?resp.status():'?'} ${elapsed}ms body=${info.bodyLen} tbl=${info.tables} btn=${info.btns} inp=${info.inputs} card=${info.cards} form=${info.forms} empty=${info.empty}`;
      if(info.whiteScreen){status='FAIL';detail+=' | WHITE SCREEN'}
      if(info.errorText){status='FAIL';detail+=' | ERROR TEXT ON PAGE'}
      if(info.notFound){status='FAIL';detail+=' | 404 NOT FOUND'}
      if(newErrors.length>0){status=status==='PASS'?'WARN':status;detail+=` | consoleErr=${newErrors.length}: ${newErrors.slice(0,3).map(e=>e.msg).join('; ').slice(0,200)}`}
      if(info.bodyLen<500 && info.tables===0){status=status==='PASS'?'WARN':status;detail+=' | SPARSE CONTENT'}
      await page.screenshot({path:path.join(OUT,`${scope}-${name}.png`)}).catch(()=>{});
      rec(scope,route,name,status,detail);
    } catch(e) {
      rec(scope,route,name,'FAIL','Exception: '+e.message);
    }
  }
  fs.writeFileSync(path.join(__dirname,'results','ui-pages-v2.json'),JSON.stringify(results,null,2));
  fs.writeFileSync(path.join(__dirname,'results','all-browser-errors.json'),JSON.stringify(allErrors,null,2));
  const sum = results.reduce((a,r)=>{a[r.status]=(a[r.status]||0)+1;return a;},{});
  console.log('\n=== SUMMARY ===',JSON.stringify(sum));
  console.log('Total browser errors:',allErrors.length);
  await browser.close();
})();
