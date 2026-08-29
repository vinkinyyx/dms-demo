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
(async()=>{
  const b=await chromium.launch({headless:true});
  const ctx=await b.newContext({viewport:{width:393,height:852},isMobile:true,hasTouch:true,userAgent:'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) Mobile/15E148'});
  const p=await ctx.newPage();
  const errs=[];p.on('pageerror',e=>errs.push(String(e).slice(0,150)));
  p.on('console',m=>{if(m.type()==='error'){const t=m.text();if(!/favicon|cdn/i.test(t))errs.push(t.slice(0,150));}});
  const h5=[];let submitResp=null, createResp=null;
  p.on('response',async r=>{
    if(r.status()>=500) h5.push(r.status()+' '+r.url().slice(0,60));
    if(/\/api\/sales-orders\/\d+\/submit/.test(r.url())){ try{ submitResp={status:r.status(), body:(await r.json())}; }catch(e){} }
    if(r.url().endsWith('/api/sales-orders') && r.request().method()==='POST'){ try{ createResp=(await r.json()); }catch(e){} }
  });
  await login(p);
  await p.goto(BASE+'/dms/mobile/smart-order',{waitUntil:'networkidle'}); await sleep(1800);
  const last=()=>p.locator('.bubble.bot').last();
  const lb=async()=>await last().innerText();
  const tap=async re=>{const btn=last().locator('.opt-btn',{hasText:re}).first();await btn.waitFor({timeout:8000});await btn.click();};
  const tapIdx=async i=>{const btn=last().locator('.opt-btn').nth(i);await btn.waitFor({timeout:8000});await btn.click();};
  const send=async v=>{const inp=p.locator('.input-bar input');await inp.waitFor({state:'visible',timeout:10000});await sleep(150);await inp.fill(String(v));await p.locator('.input-bar button').click();};

  // 销售订单 -> 客户 -> 产品 -> qty -> 百分比行折扣 -> 继续?否 -> 整单折扣(百分比) -> 确认提交
  await tap('销售订单'); await sleep(1200);
  await tapIdx(0); await sleep(2000);
  await send('PRD-T'); await sleep(2500);
  let t=await lb();
  let n=await last().locator('.opt-btn').count();
  log('P0 搜索产品', n>=1, {t:t.slice(0,40),n});
  await tapIdx(0); await sleep(1200);
  await send('10'); await sleep(1500);
  t=await lb();
  log('P1 行折扣步', /第五步/.test(t), {});
  await tap('按百分比折扣'); await sleep(1500);
  await send('90'); await sleep(1800);   // 9 折
  t=await lb();
  log('P2 行折扣后到第六步', /第六步/.test(t), {t:t.slice(0,40)});
  await tap('产品已添加完毕'); await sleep(1800);
  t=await lb();
  log('P3 第七步整单优惠', /第七步/.test(t), {});
  await tap('整单折扣'); await sleep(1500);
  t=await lb();
  log('P4 第八步折扣方式', /第八步/.test(t)&&/百分比/.test(t), {t:t.slice(0,40)});
  await tap('按百分比'); await sleep(1500);
  await send('95'); await sleep(4000);   // 整单 95 折
  t=await lb();
  log('P5 到确认步摘要', /第九步/.test(t)&&/应付金额/.test(t), {t:t.slice(0,60)});
  const card=await last().locator('.summary-card').innerText().catch(()=> '');
  log('P6 摘要含整单折扣+金额', /整单折扣/.test(card) && /¥/.test(card), {card:card.replace(/\n/g,' ').slice(0,160)});
  // 确认提交
  await tap('确认提交'); await sleep(5000);
  t=await lb();
  log('P7 提交成功进审批', /已提交成功|进入审批/.test(t), {t:t.slice(0,80), create:createResp&&createResp.data&&createResp.data.code, submit:submitResp&&submitResp.status});
  // 回读订单状态
  const newId = createResp && createResp.data && createResp.data.id;
  if(newId){
    const token=await p.evaluate(()=>localStorage.getItem('dms_access_token'));
    const detail=await p.evaluate(async({token,newId})=>{const r=await fetch('/api/sales-orders/'+newId,{headers:{Authorization:'Bearer '+token}});return await r.json();},{token,newId});
    const d=detail.data||{};
    log('P8 回读订单状态为待审批/审批中', /PENDING|APPROVAL|RUNNING|SUBMITTED/.test(String(d.status)), {status:d.status, code:d.code, orderType:d.orderType});
  }
  log('P99 无红错无5xx', errs.length===0&&h5.length===0, {errs:errs.slice(0,3),h5});
  await p.screenshot({path:'automation_test/v4-browser-results/smart-order-submit.png',fullPage:true});
  const pass=R.filter(r=>r.ok).length;
  console.log(`\n==== SUBMIT/DISCOUNT FLOW ${pass}/${R.length} ====`);
  await b.close();
  process.exit(pass===R.length?0:1);
})();
