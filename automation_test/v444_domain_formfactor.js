// 域名 + 设备形态分流验证：PC 域名登录、移动端自动跳 mobile、手动切电脑版
const { chromium } = require('playwright');
const BASE = process.env.E2E_BASE || 'http://dms-dev.mysolmed.com';
const results = [];
function log(n, ok, d){ results.push({n,ok}); console.log((ok?'PASS ':'FAIL ')+n+' :: '+JSON.stringify(d).slice(0,260)); }

async function fresh(browser, mobile){
  const ctx = mobile ? await browser.newContext({
      viewport:{width:393,height:852}, isMobile:true, hasTouch:true,
      userAgent:'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1'
    }) : await browser.newContext({ viewport:{width:1440,height:900} });
  const page = await ctx.newPage();
  const errs=[]; page.on('console',m=>{ if(m.type()==='error'){const t=m.text(); if(!/favicon|cdn|jsdelivr|tailwind|ResizeObserver/i.test(t))errs.push(t.slice(0,150));}});
  page.on('pageerror',e=>errs.push('pe:'+String(e).slice(0,150)));
  const h5=[]; page.on('response',r=>{const s=r.status(); if(s>=500)h5.push(s+' '+r.url().replace(BASE,'').slice(0,90));});
  return {ctx,page,errs,h5};
}
async function mobileLogin(page){
  await page.goto(BASE+'/dms/mobile/login',{waitUntil:'networkidle'}).catch(()=>{});
  await page.waitForTimeout(1200);
  await page.locator('input[placeholder="租户代码"]').fill('default').catch(()=>{});
  await page.locator('input[placeholder="请输入账号"]').fill('admin').catch(()=>{});
  await page.locator('input[type="password"]').fill('Sh123456').catch(()=>{});
  await page.locator('.van-form button.van-button--primary').first().click().catch(()=>{});
  await page.waitForTimeout(5000);
}

(async()=>{
  const browser=await chromium.launch({headless:true});

  // ===== MOBILE =====
  {
    const {ctx,page,errs,h5}=await fresh(browser,true);
    // 1) root domain -> should land on mobile login
    await page.goto(BASE+'/',{waitUntil:'networkidle'}).catch(()=>{});
    await page.waitForTimeout(2500);
    let u=page.url();
    log('M1-手机访问域名根/自动到移动页', /\/dms\/mobile\/login/.test(u), {url:u.replace(BASE,'')});
    // is Vant mobile login (not PC)? check for van-field / mobile-specific
    const isVant = await page.locator('.van-cell-group, .van-field').count();
    log('M2-落地页是移动Vant登录(非PC)', isVant>0, {vantNodes:isVant});
    // 3) phone directly opening PC login -> redirected to mobile login
    await page.goto(BASE+'/dms/login',{waitUntil:'networkidle'}).catch(()=>{});
    await page.waitForTimeout(2000);
    u=page.url();
    log('M3-手机开/dms/login被弹到移动登录', /\/dms\/mobile\/login/.test(u), {url:u.replace(BASE,'')});
    // 4) phone opening a PC business page -> mobile (login first)
    await mobileLogin(page);
    const tok=await page.evaluate(()=>localStorage.getItem('dms_access_token'));
    await page.goto(BASE+'/dms/home',{waitUntil:'networkidle'}).catch(()=>{});
    await page.waitForTimeout(2500);
    u=page.url();
    log('M4-已登录手机开PC工作台->移动首页', !!tok && /\/dms\/mobile\/home/.test(u), {url:u.replace(BASE,''),tok:!!tok});
    // mobile lists render
    await page.goto(BASE+'/dms/mobile/orders',{waitUntil:'networkidle'}).catch(()=>{});
    await page.waitForTimeout(3000);
    const cards=await page.locator('.van-cell,.van-card,.order-card').count();
    const body=await page.evaluate(()=>document.body.innerText);
    log('M5-移动订单列表渲染', cards>0 && !/加载中/.test(body), {cards, stuck:/加载中/.test(body)});
    // 6) switch to PC version
    await page.goto(BASE+'/dms/mobile/profile',{waitUntil:'networkidle'}).catch(()=>{});
    await page.waitForTimeout(1500);
    await page.locator('.van-cell:has-text("切换到电脑版")').click().catch(e=>log('M6-点击切换入口',false,e.message));
    await page.waitForTimeout(3000);
    u=page.url();
    const pref=await page.evaluate(()=>sessionStorage.getItem('dms_view_pref'));
    log('M6-切换到电脑版->PC工作台', /\/dms\/home/.test(u) && !/mobile/.test(u) && pref==='pc', {url:u.replace(BASE,''),pref});
    // 7) after switch, refresh /dms/home stays PC (no bounce)
    await page.goto(BASE+'/dms/home',{waitUntil:'networkidle'}).catch(()=>{});
    await page.waitForTimeout(2500);
    u=page.url();
    log('M7-切PC后刷新PC页不再弹回移动', /\/dms\/home/.test(u) && !/mobile/.test(u), {url:u.replace(BASE,'')});
    log('M8-移动形态console无红错/无5xx', errs.length===0 && h5.length===0, {errs:errs.slice(0,3),h5});
    await ctx.close();
  }

  // ===== DESKTOP =====
  {
    const {ctx,page,errs,h5}=await fresh(browser,false);
    await page.goto(BASE+'/',{waitUntil:'networkidle'}).catch(()=>{});
    await page.waitForTimeout(2500);
    let u=page.url();
    const isPcLogin = /\/dms\/login/.test(u);
    log('D1-PC访问域名根->PC登录页', isPcLogin, {url:u.replace(BASE,'')});
    const notMobile = !/mobile/.test(u);
    log('D2-PC不被跳到移动页', notMobile, {url:u.replace(BASE,'')});
    // desktop login works
    await page.locator('input[placeholder="租户代码"], input[placeholder="租户"]').first().fill('default').catch(()=>{});
    await page.locator('input[placeholder="账号"], input[placeholder="请输入账号"]').first().fill('admin').catch(()=>{});
    await page.locator('input[type="password"]').first().fill('Sh123456').catch(()=>{});
    await page.locator('button:has-text("登录"), button:has-text("登 录")').first().click().catch(()=>{});
    await page.waitForTimeout(5000);
    await page.waitForLoadState('networkidle').catch(()=>{});
    u=page.url();
    log('D3-PC登录进工作台', /\/dms\/home/.test(u) && !/mobile/.test(u), {url:u.replace(BASE,'')});
    log('D4-PC形态console无红错/无5xx', errs.length===0 && h5.length===0, {errs:errs.slice(0,3),h5});
    await ctx.close();
  }

  const pass=results.filter(r=>r.ok).length;
  console.log(`\n==== SUMMARY ${pass}/${results.length} (${BASE}) ====`);
  await browser.close();
  process.exit(pass===results.length?0:1);
})();
