const { chromium } = require('playwright');
const BASE='http://dms-dev.mysolmed.com';
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const R=[];const log=(n,ok,d)=>{R.push({n,ok});console.log((ok?'PASS ':'FAIL ')+n+' :: '+JSON.stringify(d).slice(0,200));};
async function login(p){
  await p.goto(BASE+'/dms/mobile/login',{waitUntil:'networkidle'}); await sleep(1000);
  await p.evaluate(()=>localStorage.clear());
  await p.locator('input[placeholder="租户代码"]').fill('default');
  await p.locator('input[placeholder="请输入账号"]').fill('admin');
  await p.locator('input[type="password"]').fill('Sh123456');
  await p.locator('.van-form button.van-button--primary').first().click(); await sleep(5000);
}
(async()=>{
  const b=await chromium.launch({headless:true});
  // ===== 代金券路径 =====
  {
    const ctx=await b.newContext({viewport:{width:393,height:852},isMobile:true,hasTouch:true,userAgent:'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) Mobile/15E148'});
    const p=await ctx.newPage();
    const errs=[];p.on('pageerror',e=>errs.push(String(e).slice(0,120)));
    await login(p);
    await p.goto(BASE+'/dms/mobile/smart-order',{waitUntil:'networkidle'}); await sleep(1800);
    const last=()=>p.locator('.bubble.bot').last();
    const lb=async()=>await last().innerText();
    const tap=async re=>{const btn=last().locator('.opt-btn',{hasText:re}).first();await btn.waitFor({timeout:8000});await btn.click();};
    const tapIdx=async i=>{const btn=last().locator('.opt-btn').nth(i);await btn.waitFor({timeout:8000});await btn.click();};
    const send=async v=>{const inp=p.locator('.input-bar input');await inp.waitFor({state:'visible',timeout:10000});await inp.fill(String(v));await p.locator('.input-bar button').click();};
    await tap('销售订单'); await sleep(1200);
    await tapIdx(0); await sleep(2000);
    await send('PRD-T'); await sleep(2500);
    await tapIdx(0); await sleep(1200);
    await send('10'); await sleep(1500);
    await tap('无折扣'); await sleep(1500);
    await tap('产品已添加完毕'); await sleep(1800);
    await tap('使用代金券'); await sleep(4000);
    let t=await lb();
    const hasVoucher = /代金券/.test(t) && /¥/.test(t);
    const noVoucher = /没有可用代金券/.test(t);
    log('V1 代金券步加载(有券或正确提示无券)', hasVoucher||noVoucher, {t:t.replace(/\n/g,' ').slice(0,120)});
    if(hasVoucher){
      const voucherBtns=await last().locator('.opt-btn').count();
      log('V2 列出可用代金券', voucherBtns>=2, {voucherBtns});
      await tapIdx(0); await sleep(4000);
      t=await lb();
      log('V3 选券后到确认步且摘要含代金券', /第九步/.test(t)&&/代金券/.test(await last().locator('.summary-card').innerText().catch(()=>'')), {t:t.slice(0,40)});
      await tap('取消'); await sleep(1200);
    } else {
      // 无券则返回选“无整单优惠”走完确认
      await tap('不使用整单优惠'); await sleep(3500);
      t=await lb();
      log('V2b 无券回退到确认步', /第九步/.test(t), {t:t.slice(0,40)});
      await tap('取消'); await sleep(1200);
    }
    log('V99 代金券路径无红错', errs.length===0, {errs:errs.slice(0,2)});
    await ctx.close();
  }
  // ===== 一口价路径 =====
  {
    const ctx=await b.newContext({viewport:{width:393,height:852},isMobile:true,hasTouch:true,userAgent:'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) Mobile/15E148'});
    const p=await ctx.newPage();
    const errs=[];p.on('pageerror',e=>errs.push(String(e).slice(0,120)));
    await login(p);
    await p.goto(BASE+'/dms/mobile/smart-order',{waitUntil:'networkidle'}); await sleep(1800);
    const last=()=>p.locator('.bubble.bot').last();
    const lb=async()=>await last().innerText();
    const tap=async re=>{const btn=last().locator('.opt-btn',{hasText:re}).first();await btn.waitFor({timeout:8000});await btn.click();};
    const tapIdx=async i=>{const btn=last().locator('.opt-btn').nth(i);await btn.waitFor({timeout:8000});await btn.click();};
    const send=async v=>{const inp=p.locator('.input-bar input');await inp.waitFor({state:'visible',timeout:10000});await inp.fill(String(v));await p.locator('.input-bar button').click();};
    await tap('销售订单'); await sleep(1200);
    await tapIdx(0); await sleep(2000);
    await send('PRD-T'); await sleep(2500);
    await tapIdx(0); await sleep(1200);
    await send('10'); await sleep(1500);
    await tap('无折扣'); await sleep(1500);
    await tap('产品已添加完毕'); await sleep(1800);
    await tap('整单一口价'); await sleep(1500);
    let t=await lb();
    log('F1 一口价问金额', /一口价/.test(t), {t:t.slice(0,40)});
    // 非法金额
    await send('abc'); await sleep(1200);
    t=await lb();
    log('F2 非法一口价被拦截', /大于 0/.test(t), {t:t.slice(0,50)});
    await send('100'); await sleep(4000);
    t=await lb();
    const card=await last().locator('.summary-card').innerText().catch(()=> '');
    log('F3 一口价确认步应付=¥100.00', /第九步/.test(t)&&/¥100\.00/.test(card), {card:card.replace(/\n/g,' ').slice(-80)});
    await tap('取消'); await sleep(1200);
    log('F99 一口价路径无红错', errs.length===0, {errs:errs.slice(0,2)});
    await ctx.close();
  }
  const pass=R.filter(r=>r.ok).length;
  console.log(`\n==== VOUCHER/FIXED ${pass}/${R.length} ====`);
  await b.close();
  process.exit(pass===R.length?0:1);
})();
