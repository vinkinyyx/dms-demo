const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const BASE = process.env.DMS_BASE_URL || 'http://43.128.145.141';
const OUT = path.join(__dirname, 'results');
fs.mkdirSync(OUT, { recursive: true });
const rows = [];
function rec(scope, name, ok, detail){ rows.push({scope,name,ok,detail:String(detail||'').slice(0,300)}); console.log(`[${ok?'PASS':'FAIL'}] ${scope}/${name} :: ${String(detail||'').slice(0,140)}`); }
const sleep = ms => new Promise(r=>setTimeout(r,ms));

async function login(page, url, isAdmin){
  await page.goto(url, {waitUntil:'networkidle'});
  await sleep(800);
  if(isAdmin){
    const ins = page.locator('.login-card input, .el-card input');
    await ins.nth(0).fill('admin');
    await ins.nth(1).fill('Sh123456');
    await page.locator('.el-button--primary, button[type=submit], .btn-login').first().click().catch(()=>{});
  } else {
    const inputs = page.locator('.login-form input');
    await inputs.nth(1).fill('admin');
    await inputs.nth(2).fill('Sh123456');
    await page.locator('.btn-login').first().click().catch(()=>{});
  }
  await sleep(3000);
}
async function checkMenu(page, scope, m){
  let ok=true, det='';
  try{
    const r = await page.goto(`${BASE}${m}`, {waitUntil:'networkidle', timeout:20000});
    await sleep(450);
    const body = (await page.locator('body').innerText()).replace(/\s+/g,' ').trim();
    const status = r ? r.status() : 0;
    const is404 = /抱歉.*页面不存在|404.*页面不存在/.test(body);
    const is500 = /500|Internal Server Error|系统异常|系统错误/.test(body) && !/500\s*条|500\s*个|500\s*家|500\s*批/.test(body);
    ok = status<500 && !is404 && !is500 && body.length>40;
    det = `http=${status} len=${body.length} ${body.slice(0,90)}`;
  }catch(e){ ok=false; det=e.message.slice(0,140); }
  rec(scope, m, ok, det);
}

(async()=>{
  const browser = await chromium.launch({headless:true});
  const errs=[];
  const pc = await browser.newPage({viewport:{width:1440,height:900}});
  pc.on('pageerror', e=>errs.push('PC:'+e.message));
  pc.on('console', m=>{ if(m.type()==='error' && !/favicon|net::ERR_FAILED/.test(String(m.text()))) errs.push('PC-console:'+m.text()); });
  await login(pc, `${BASE}/login`);
  rec('PC','login', !pc.url().includes('/login'), pc.url());
  const pcMenus = ['/home','/m/products','/m/dealers','/m/warehouses','/m/hospitals','/m/suppliers',
    '/m/orders','/m/purchase-orders','/m/receipts','/m/sales-outs','/m/inventory','/m/stock-moves','/m/inventory-adjustments',
    '/stocktakes','/expiry-alerts','/traceability','/m/surgery-reports','/m/promotions',
    '/contracts','/contracts/templates','/m/authorizations',
    '/dashboard','/reports','/report-subscriptions','/product-mappings',
    '/approval/todo','/approval/templates','/approval/delegations','/approval/admin',
    '/m/users','/roles-manage','/tenant-page-configs','/log-center','/async-tasks'];
  for(const m of pcMenus) await checkMenu(pc,'PC',m);

  const ad = await browser.newPage({viewport:{width:1440,height:900}});
  ad.on('pageerror', e=>errs.push('AD:'+e.message));
  await login(ad, `${BASE}/admin/login`);
  rec('ADMIN','login', !ad.url().includes('/login'), ad.url());
  const adMenus=['/admin/tenants','/admin/admins','/admin/roles','/admin/menus','/admin/dicts','/admin/mappings','/admin/logs/api','/admin/logs/audits','/admin/login-logs'];
  for(const m of adMenus) await checkMenu(ad,'ADMIN',m);

  const mb = await browser.newPage({viewport:{width:390,height:844}, isMobile:true, hasTouch:true});
  mb.on('pageerror', e=>errs.push('MB:'+e.message));
  await login(mb, `${BASE}/mobile/login`);
  rec('MOBILE','login', !mb.url().includes('/login'), mb.url());
  for(const m of ['/mobile/home','/mobile/orders','/mobile/surgery-reports','/mobile/dashboard','/mobile/profile']) await checkMenu(mb,'MOBILE',m);

  const fail = rows.filter(r=>!r.ok);
  rec('JS','console_errors', errs.length===0, errs.join(' | ').slice(0,500));
  fs.writeFileSync(path.join(OUT,'deep-e2e-results.json'), JSON.stringify({total:rows.length, fail:fail.length, rows}, null, 2));
  console.log(`\nSUMMARY ${rows.length} checks, ${fail.length} failures`);
  if(fail.length) console.log(fail.map(f=>`  - ${f.scope}/${f.name}: ${f.detail}`).join('\n'));
  await browser.close();
  process.exit(fail.length>0?1:0);
})();
