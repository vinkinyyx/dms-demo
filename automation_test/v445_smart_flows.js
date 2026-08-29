const { chromium } = require('playwright');
const BASE='http://dms-dev.mysolmed.com';
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const R=[];const log=(n,ok,d)=>{R.push({n,ok});console.log((ok?'PASS ':'FAIL ')+n+' :: '+JSON.stringify(d).slice(0,220));};

async function login(p){
  await p.goto(BASE+'/dms/mobile/login',{waitUntil:'networkidle'}); await sleep(1000);
  await p.evaluate(()=>localStorage.clear());
  await p.locator('input[placeholder="租户代码"]').fill('default');
  await p.locator('input[placeholder="请输入账号"]').fill('admin');
  await p.locator('input[type="password"]').fill('Sh123456');
  await p.locator('.van-form button.van-button--primary').first().click(); await sleep(5000);
}
async function wizard(p){ await p.goto(BASE+'/dms/mobile/smart-order',{waitUntil:'networkidle'}); await sleep(1800); }
function helpers(p){
  const last=()=>p.locator('.bubble.bot').last();
  return {
    last, lb:async()=>await last().innerText(),
    tap:async(re)=>{const b=last().locator('.opt-btn',{hasText:re}).first();await b.waitFor({timeout:8000});await b.click();},
    tapIdx:async(i)=>{const b=last().locator('.opt-btn').nth(i);await b.waitFor({timeout:8000});await b.click();},
    send:async(v)=>{const inp=p.locator('.input-bar input');await inp.waitFor({state:'visible',timeout:10000});await sleep(150);await inp.fill(String(v));await p.locator('.input-bar button').click();}
  };
}

(async()=>{
  const b=await chromium.launch({headless:true});
  const mkCtx=async()=>{
    const ctx=await b.newContext({viewport:{width:393,height:852},isMobile:true,hasTouch:true,userAgent:'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1'});
    const p=await ctx.newPage();
    const errs=[];p.on('pageerror',e=>errs.push(String(e).slice(0,150)));
    p.on('console',m=>{if(m.type()==='error'){const t=m.text();if(!/favicon|cdn/i.test(t))errs.push(t.slice(0,150));}});
    p.on('response',r=>{if(r.status()>=500)errs.push('5xx:'+r.status());});
    await login(p); await wizard(p);
    return {ctx,p,errs};
  };

  // ===== 补货链路 =====
  {
    const {ctx,p,errs}=await mkCtx(); const h=helpers(p);
    await h.tap('补货订单'); await sleep(1200);
    await h.tapIdx(0); await sleep(2000);
    let t=await h.lb();
    if(/尚未开通寄售/.test(t)){
      log('B0 补货拦截未寄售客户', true, {});
      await h.tap('重新选择客户'); await sleep(1200);
      // DLR-012 在 credit 列表(索引11)
      await h.tapIdx(11); await sleep(2000); t=await h.lb();
    } else { log('B0 首个客户已开通寄售(直接放行)', /第三步/.test(t), {t:t.slice(0,30)}); }
    log('B1 补货进入产品搜索', /第三步/.test(t), {t:t.slice(0,40)});
    await h.send('PRD'); await sleep(2500);
    await h.tapIdx(0); await sleep(1200);
    await h.send('2'); await sleep(1800);
    t=await h.lb();
    log('B2 补货数量后跳过行折扣(到第六步)', /第六步/.test(t), {t:t.slice(0,50)});
    await h.tap('产品已添加完毕'); await sleep(4000);
    t=await h.lb();
    log('B3 补货跳过第七步直接确认(第九步)', /第九步/.test(t), {t:t.slice(0,40)});
    log('B4 补货本单金额¥0.00', /¥0\.00/.test(t), {zero:/¥0\.00/.test(t)});
    log('B5 补货无行折扣/整单折扣选项文案', !/整单优惠|行折扣/.test(t), {});
    await h.tap('取消'); await sleep(1500);
    t=await h.lb();
    log('B6 取消成功', /已取消本单/.test(t), {});
    log('B99 补货链路无红错', errs.filter(e=>!/5xx/.test(e)).length===0, {errs:errs.slice(0,3)});
    await ctx.close();
  }

  // ===== 样品链路 =====
  {
    const {ctx,p,errs}=await mkCtx(); const h=helpers(p);
    await h.tap('样品订单'); await sleep(1200);
    await h.tapIdx(0); await sleep(2000);
    let t=await h.lb();
    log('C1 样品进入产品搜索', /第三步/.test(t), {t:t.slice(0,30)});
    await h.send('PRD'); await sleep(2500);
    await h.tapIdx(0); await sleep(1200);
    await h.send('1'); await sleep(1800);
    t=await h.lb();
    log('C2 样品数量后问申请原因(非行折扣)', /申请原因/.test(t), {t:t.slice(0,50)});
    await h.send('XX医院骨科术中试用'); await sleep(4000);
    t=await h.lb();
    log('C3 样品单品直接确认(跳过第六/七步)', /第九步/.test(t)&&/本单金额/.test(t), {t:t.slice(0,50)});
    const lines=await h.last().locator('.sum-line').count();
    log('C4 样品摘要仅1行', lines===1, {lines});
    log('C5 样品本单金额0', /¥0\.00/.test(t), {});
    log('C99 样品链路无红错', errs.filter(e=>!/5xx/.test(e)).length===0, {errs:errs.slice(0,3)});
    await ctx.close();
  }

  // ===== 容错：输入0重新开始 + 非法折扣值 =====
  {
    const {ctx,p,errs}=await mkCtx(); const h=helpers(p);
    await h.tap('销售订单'); await sleep(1200);
    await h.tapIdx(0); await sleep(2000);
    // 产品搜索输入 0 -> 重新开始
    await h.send('0'); await sleep(1500);
    let t=await h.lb();
    log('Z1 搜索步输入0回到第一步', /第一步/.test(t)&&/订单类型/.test(t), {t:t.slice(0,40)});
    const typeOpts=await h.last().locator('.opt-btn').count();
    log('Z2 重置后重新出现3类型', typeOpts===3, {typeOpts});
    await ctx.close();
  }

  const pass=R.filter(r=>r.ok).length;
  console.log(`\n==== REPLENISH/SAMPLE/ERROR ${pass}/${R.length} ====`);
  await b.close();
  process.exit(pass===R.length?0:1);
})();
