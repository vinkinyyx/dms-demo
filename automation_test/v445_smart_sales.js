const { chromium } = require('playwright');
const BASE='http://dms-dev.mysolmed.com';
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const R=[];const log=(n,ok,d)=>{R.push({n,ok});console.log((ok?'PASS ':'FAIL ')+n+' :: '+JSON.stringify(d).slice(0,200));};
(async()=>{
  const b=await chromium.launch({headless:true});
  const ctx=await b.newContext({viewport:{width:393,height:852},isMobile:true,hasTouch:true,userAgent:'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1'});
  const p=await ctx.newPage();
  const errs=[];p.on('pageerror',e=>errs.push(String(e).slice(0,150)));
  p.on('console',m=>{if(m.type()==='error'){const t=m.text();if(!/favicon|cdn/i.test(t))errs.push(t.slice(0,150));}});
  const h5=[];p.on('response',r=>{if(r.status()>=500)h5.push(r.status());});
  await p.goto(BASE+'/dms/mobile/login',{waitUntil:'networkidle'}); await sleep(1000);
  await p.evaluate(()=>localStorage.clear());
  await p.locator('input[placeholder="租户代码"]').fill('default');
  await p.locator('input[placeholder="请输入账号"]').fill('admin');
  await p.locator('input[type="password"]').fill('Sh123456');
  await p.locator('.van-form button.van-button--primary').first().click(); await sleep(5000);
  await p.goto(BASE+'/dms/mobile/smart-order',{waitUntil:'networkidle'}); await sleep(2000);
  const last=()=>p.locator('.bubble.bot').last();
  const lb=async()=>await last().innerText();
  const tap=async (re)=>{const btn=last().locator('.opt-btn',{hasText:re}).first();await btn.waitFor({timeout:8000});await btn.click();};
  const tapIdx=async(i)=>{const btn=last().locator('.opt-btn').nth(i);await btn.waitFor({timeout:8000});await btn.click();};
  const send=async v=>{const inp=p.locator('.input-bar input');await inp.waitFor({state:'visible',timeout:10000});await sleep(200);await inp.fill(String(v));await p.locator('.input-bar button').click();};

  // 销售 -> 客户 -> 产品1 -> qty5 -> 无行折扣 -> 继续加 -> 产品2 -> qty3 -> 无行折扣 -> 否 -> 整单无优惠 -> 确认摘要 -> 保存草稿
  await tap('销售订单'); await sleep(1200);
  await tapIdx(0); await sleep(2000);
  await send('PRD'); await sleep(2500);
  await tapIdx(0); await sleep(1200);          // 产品1
  await send('5'); await sleep(1500);
  await tap('无折扣'); await sleep(1500);
  let t=await lb();
  log('A7 第六步继续加产品', /第六步/.test(t), {});
  await tap('是，继续添加'); await sleep(1800);
  t=await lb();
  log('A8 回到产品搜索+输入框', /第三步/.test(t) && await p.locator('.input-bar input').count()===1, {inp:await p.locator('.input-bar input').count()});
  await send('PRD'); await sleep(2500);
  // 产品2：选第二个（PRD-B002）
  await tapIdx(1); await sleep(1500);
  t=await lb();
  log('A8b 产品2后问数量(非重复)', /第四步/.test(t), {t:t.slice(0,40)});
  if (/第四步/.test(t)) {
    await send('3'); await sleep(1500);
    t=await lb();
    if (/第五步/.test(t)) { await tap('无折扣'); await sleep(1500); }
  } else {
    log('A8c 产品2重复或异常', false, {t:t.slice(0,80)});
  }
  t=await lb();
  // 现在应在第六步；点“否”
  await tap('产品已添加完毕'); await sleep(1800);
  t=await lb();
  log('A9 到第七步整单优惠', /第七步/.test(t)&&/整单优惠/.test(t), {t:t.slice(0,40)});
  await tap('无整单优惠'); await sleep(4000);
  t=await lb();
  log('A10 到第九步确认摘要', /第九步/.test(t)&&/应付金额/.test(t), {t:t.slice(0,60)});
  const lines=await last().locator('.sum-line').count();
  log('A11 摘要2行', lines===2, {lines});
  await tap('保存草稿'); await sleep(4000);
  t=await lb();
  log('A12 草稿保存成功', /已保存为草稿/.test(t), {t:t.slice(0,60)});
  log('Z99 无红错无5xx', errs.length===0&&h5.length===0, {errs:errs.slice(0,2),h5});
  await p.screenshot({path:'automation_test/v4-browser-results/smart-order-sales.png',fullPage:true});
  const pass=R.filter(r=>r.ok).length;
  console.log(`\n==== SALES FLOW ${pass}/${R.length} ====`);
  await b.close();
  process.exit(pass===R.length?0:1);
})();
