const { chromium } = require('playwright');
const BASE='http://43.128.145.141';
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const rows=[];
const rec=(s,n,ok,d)=>{rows.push({s,n,ok,d});console.log(`[${ok?'PASS':'FAIL'}] ${s}/${n} :: ${String(d||'').slice(0,120)}`);};
async function check(page,scope,m){
  try{ const r=await page.goto(BASE+m,{waitUntil:'networkidle',timeout:20000}); await sleep(500);
    const b=(await page.locator('body').innerText()).replace(/\s+/g,' ').trim();
    const is404=/抱歉.*页面不存在/.test(b); const is500=/Internal Server Error|系统异常/.test(b);
    rec(scope,m, r.status()<500&&!is404&&!is500&&b.length>40, `http=${r.status()} len=${b.length} ${b.slice(0,80)}`);
  }catch(e){ rec(scope,m,false,e.message.slice(0,120)); }
}
(async()=>{
  const b=await chromium.launch({headless:true});
  const errs=[];
  // mobile
  const mb=await b.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
  mb.on('pageerror',e=>errs.push('MB:'+e.message));
  await mb.goto(BASE+'/mobile/login',{waitUntil:'networkidle'}); await sleep(800);
  await mb.locator('input[type=password]').first().fill('Dms@123456'); const mins=mb.locator('.van-field input'); for(let i=0;i<await mins.count();i++){const t=await mins.nth(i).getAttribute('type')||'text'; if(t!=='password'){const v=await mins.nth(i).inputValue(); if(!v||v==='sys_admin'){ await mins.nth(i).fill('sys_admin'); break; }}}
  await mb.getByRole('button',{name:/登\s*录/}).first().click(); await sleep(3000);
  rec('MOBILE','login',!mb.url().includes('/login'),mb.url());
  for(const m of ['/mobile/home','/mobile/orders','/mobile/surgery-reports','/mobile/dashboard','/mobile/approvals','/mobile/messages','/mobile/profile']) await check(mb,'MOBILE',m);
  // admin
  const ad=await b.newPage({viewport:{width:1440,height:900}});
  ad.on('pageerror',e=>errs.push('AD:'+e.message));
  await ad.goto(BASE+'/admin/login',{waitUntil:'networkidle'}); await sleep(800);
  const ai=ad.locator('input');
  await ai.nth(0).fill('admin'); await ai.nth(1).fill('Sh123456');
  await ad.locator('button.el-button--primary, button[type=submit]').first().click(); await sleep(3000);
  rec('ADMIN','login',!ad.url().includes('/login'),ad.url());
  for(const m of ['/admin/tenants/manufacturers','/admin/tenants/dealers','/admin/tenant-admins','/admin/role-templates','/admin/menus','/admin/ui-configs','/admin/dicts','/admin/logs/api','/admin/logs/audits']) await check(ad,'ADMIN',m);
  rec('JS','errors',errs.length===0,errs.join(' | ').slice(0,400));
  const fail=rows.filter(r=>!r.ok);
  console.log(`\nSUMMARY ${rows.length} checks, ${fail.length} failures`);
  if(fail.length) console.log(fail.map(f=>`  - ${f.s}/${f.n}: ${f.d}`).join('\n'));
  await b.close(); process.exit(fail.length?1:0);
})();
