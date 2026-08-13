const { chromium } = require('playwright');
(async()=>{
  const b = await chromium.launch({headless:true});
  const ctx = await b.newContext({viewport:{width:1440,height:900}});
  const p = await ctx.newPage();
  const errs=[];
  p.on('console',m=>{if(m.type()==='error')errs.push(m.text())});
  p.on('pageerror',e=>errs.push('PAGE:'+e.message));
  // login via API then set localStorage
  await p.goto('http://8.133.193.238:8083/login',{waitUntil:'networkidle'});
  await p.fill('input[placeholder="账号"]','sys_admin');
  await p.fill('input[type="password"]','Dms@123456');
  await p.click('button:has-text("登 录")');
  await p.waitForURL(/\/home/,{timeout:15000});
  await p.waitForTimeout(5000);
  console.log('URL:',p.url());
  // probe home tabs
  const tabs = await p.evaluate(()=>{
    const all = document.querySelectorAll('[role="tab"], .el-tabs__item, [class*="tab"]');
    return Array.from(all).slice(0,20).map(e=>({tag:e.tagName,cls:e.className,txt:e.textContent.trim().slice(0,30),vis:e.offsetParent!==null}));
  });
  console.log('TABS:',JSON.stringify(tabs,null,2));
  // probe dropdown / user area
  const userArea = await p.evaluate(()=>{
    const sels = ['.avatar-wrapper','.user-info','.header-dropdown','[class*="avatar"]','[class*="user"]','.el-dropdown'];
    const r=[];
    for (const s of sels) { document.querySelectorAll(s).forEach(e=>r.push({s,cls:e.className,txt:e.textContent.trim().slice(0,50),vis:e.offsetParent!==null})); }
    return r.slice(0,15);
  });
  console.log('USER:',JSON.stringify(userArea,null,2));
  await p.screenshot({path:'screenshots/home-loaded.png'});
  // check console errors
  console.log('ERRORS:',errs.length, errs.slice(0,10));
  await b.close();
})();
