const { chromium } = require('playwright');
const fs = require('fs'), path=require('path');
const OUT = path.join(__dirname,'results','ui-nav');
if(!fs.existsSync(OUT)) fs.mkdirSync(OUT,{recursive:true});
const results=[];
function rec(s,i,t,d,x){results.push({scope:s,id:i,title:t,status:d,detail:String(x||'').slice(0,500)});const tag=d;console.log(`[${tag}] ${s} ${i} ${t} :: ${String(x||'').slice(0,200)}`)}

const MENU_PAGES = [
  ['BASIC','产品管理','/module/products'],
  ['BASIC','产品分类','/module/categories'],
  ['BASIC','经销商管理','/module/dealers'],
  ['BASIC','医院终端','/module/hospitals'],
  ['BASIC','仓库管理','/module/warehouses'],
  ['BASIC','供应商','/module/suppliers'],
  ['CONTRACT','合同','/module/contracts'],
  ['CONTRACT','授权管理','/module/authorizations'],
  ['ORDER','销售订单','/module/orders'],
  ['ORDER','销退订单','/module/sales-returns'],
  ['ORDER','采购订单','/module/purchase-orders'],
  ['ORDER','采退订单','/module/purchase-returns'],
  ['INV','库存查询','/module/inventory'],
  ['INV','销售出库','/module/sales-outs'],
  ['INV','收货入库','/module/receipts'],
  ['INV','库存移动','/module/stock-moves'],
  ['INV','库存调整','/module/inventory-adjustments'],
  ['SURG','手术植入报告','/module/surgery-reports'],
  ['PROMO','促销规则','/module/promotions'],
  ['RPT','订单追踪','/module/report-order-trace'],
  ['USER','销售岗位','/positions'],
  ['USER','账号管理','/module/users'],
  ['USER','角色管理','/module/roles'],
];

(async()=>{
  const browser = await chromium.launch({headless:true});
  const ctx = await browser.newContext({viewport:{width:1440,height:900}});
  const page = await ctx.newPage();
  await page.goto('http://8.133.193.238:8083/login',{waitUntil:'networkidle'});
  await page.fill('input[placeholder="账号"]','sys_admin');
  await page.fill('input[type="password"]','Dms@123456');
  await page.click('button:has-text("登 录")');
  await page.waitForURL(/\/home/);
  await page.waitForTimeout(3000);

  for (const [scope,name,route] of MENU_PAGES) {
    const errors=[];
    const onC = m=>{if(m.type()==='error')errors.push(m.text())};
    const onP = e=>errors.push('PE:'+e.message);
    page.on('console',onC); page.on('pageerror',onP);
    try {
      // SPA internal navigation via router
      const ok = await page.evaluate((r)=>{
        if(window.$nuxt && window.$nuxt.$router) { window.$nuxt.$router.push(r); return 'nuxt'; }
        if(window.__VUE_APP__ && window.__VUE_APP__.config.globalProperties.$router) { window.__VUE_APP__.config.globalProperties.$router.push(r); return 'vue3'; }
        const app = document.querySelector('#app');
        if(app && app.__vue_app__ && app.__vue_app__.config.globalProperties.$router) { app.__vue_app__.config.globalProperties.$router.push(r); return 'vue3app'; }
        return 'no-router';
      }, route);
      await page.waitForTimeout(3000);
      // also try clicking menu
      const clicked = await page.evaluate((n)=>{
        const items = document.querySelectorAll('.el-menu-item, .el-sub-menu__title, [role="menuitem"]');
        for(const it of items) { if(it.textContent.trim().includes(n)) { it.click(); return true; } }
        return false;
      }, name);
      await page.waitForTimeout(3000);
      const bodyTxt = await page.evaluate(()=>document.body.innerText);
      const tables = await page.locator('table,.el-table').count();
      const btns = await page.locator('button').count();
      const url = page.url();
      const isHome = bodyTxt.includes('欢迎使用 DMS') && bodyTxt.includes('仪表盘数据');
      const hasTable = tables > 0;
      const hasList = /总数|共.*条|新增|查询|筛选|序号|编码|名称/.test(bodyTxt);
      let status='PASS', detail=`nav=${ok} menuClicked=${clicked} url=${url} bodyLen=${bodyTxt.length} tables=${tables} btns=${btns} isHome=${isHome}`;
      if (isHome && route!=='/home') { status='FAIL'; detail+=' | STAYED ON HOME (route not rendered)'; }
      if (!isHome && !hasTable && !hasList) { status='WARN'; detail+=' | no table/list detected'; }
      if (errors.length) { status = status==='PASS'?'WARN':'FAIL'; detail+=` | errors=${errors.length}: ${errors.slice(0,2).join(';')}`; }
      await page.screenshot({path:path.join(OUT,`${scope}-${name}.png`)}).catch(()=>{});
      rec(scope,route,name,status,detail);
    } catch(e) { rec(scope,route,name,'FAIL',e.message); }
    finally { page.off('console',onC); page.off('pageerror',onP); }
  }
  fs.writeFileSync(path.join(__dirname,'results','ui-nav.json'),JSON.stringify(results,null,2));
  await browser.close();
})();
