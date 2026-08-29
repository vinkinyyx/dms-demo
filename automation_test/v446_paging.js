const { chromium } = require('playwright');
const BASE='http://dms-dev.mysolmed.com';
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const R=[];const log=(n,ok,d)=>{R.push({n,ok});console.log((ok?'PASS ':'FAIL ')+n+' :: '+JSON.stringify(d).slice(0,220));};
(async()=>{
  const b=await chromium.launch({headless:true});
  const ctx=await b.newContext({viewport:{width:393,height:852},isMobile:true,hasTouch:true,userAgent:'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) Mobile/15E148'});
  const p=await ctx.newPage();
  const errs=[];p.on('pageerror',e=>errs.push(String(e).slice(0,140)));
  p.on('console',m=>{if(m.type()==='error'){const t=m.text();if(!/favicon|cdn/i.test(t))errs.push(t.slice(0,140));}});
  const h5=[];p.on('response',r=>{if(r.status()>=500)h5.push(r.status());});
  await p.goto(BASE+'/dms/mobile/login',{waitUntil:'networkidle'}); await sleep(1000);
  await p.evaluate(()=>localStorage.clear());
  await p.locator('input[placeholder="租户代码"]').fill('default');
  await p.locator('input[placeholder="请输入账号"]').fill('admin');
  await p.locator('input[type="password"]').fill('Sh123456');
  await p.locator('.van-form button.van-button--primary').first().click(); await sleep(5000);
  await p.goto(BASE+'/dms/mobile/smart-order',{waitUntil:'networkidle'}); await sleep(1800);
  const last=()=>p.locator('.bubble.bot').last();
  const lb=async()=>await last().innerText();
  const tap=async re=>{const btn=last().locator('.opt-btn',{hasText:re}).first();await btn.waitFor({timeout:8000});await btn.click();};
  const tapIdx=async i=>{const btn=last().locator('.opt-btn').nth(i);await btn.waitFor({timeout:8000});await btn.click();};
  const send=async v=>{const inp=p.locator('.input-bar input');await inp.waitFor({state:'visible',timeout:10000});await inp.fill(String(v));await p.locator('.input-bar button').click();};

  await tap('销售订单'); await sleep(1200);
  // ===== 客户分页 =====
  let t=await lb();
  let pageInfo=(await lb()).split('\n')[0];
  log('P1 客户列表分页(第1/N批)', /第 1\/\d+ 批/.test(pageInfo), {pageInfo});
  const firstBatchLabels=await last().locator('.opt-btn').allInnerTexts();
  log('P2 客户首批最多5个+翻页', firstBatchLabels.filter(x=>/^\d+\./.test(x)).length<=5 && firstBatchLabels.some(x=>/下一批/.test(x)), {cnt:firstBatchLabels.length,hasNext:firstBatchLabels.some(x=>/下一批/.test(x))});
  await tap('下一批'); await sleep(800);
  pageInfo=(await last().locator('.b-title').innerText().catch(()=> ''));
  log('P3 翻到第2批', /第 2\//.test(pageInfo), {pageInfo});
  const secondLabels=await last().locator('.opt-btn').allInnerTexts();
  log('P4 第2批有上一批', secondLabels.some(x=>/上一批/.test(x)), {});
  await tap('上一批'); await sleep(800);
  pageInfo=(await last().locator('.b-title').innerText().catch(()=> ''));
  log('P5 翻回第1批', /第 1\//.test(pageInfo), {pageInfo});
  // 选第一个客户
  await tapIdx(0); await sleep(2000);

  // ===== 产品搜索 + 分页 =====
  await send('PRD'); await sleep(2800);
  t=await lb();
  pageInfo=(await last().locator('.b-title').innerText().catch(()=> ''));
  const prodBtns=await last().locator('.opt-btn').allInnerTexts();
  const prodCount=prodBtns.filter(x=>/^\d+\. PRD/.test(x)).length;
  log('P6 产品首批列5个+批次信息', prodCount===5 && /第 1\//.test(pageInfo), {prodCount,pageInfo});
  log('P7 产品有下一批(共20个)', prodBtns.some(x=>/下一批/.test(x)), {next:prodBtns.find(x=>/下一批/.test(x))});
  await tap('下一批'); await sleep(800);
  pageInfo=(await last().locator('.b-title').innerText().catch(()=> ''));
  const batch2=await last().locator('.opt-btn').allInnerTexts();
  const nums=batch2.filter(x=>/^\d+\. PRD/.test(x)).map(x=>parseInt(x));
  log('P8 产品第2批编号从6开始(全局连续)', nums[0]===6, {firstNum:nums[0],pageInfo});
  // 翻回第1批选产品1
  await tap('上一批'); await sleep(800);
  await tapIdx(0); await sleep(1500);
  t=await lb();
  log('P9 选产品后到数量步', /第四步/.test(t), {t:t.slice(0,30)});

  // ===== 数量快捷条 =====
  const qtyBar=await p.locator('.qty-bar').count();
  log('Q1 数量步显示快捷条', qtyBar===1, {qtyBar});
  await p.locator('.qty-quick .van-button', {hasText:'10'}).first().click(); await sleep(300);
  const qtyVal=await p.locator('.qty-val').innerText();
  log('Q2 点常用数量10', qtyVal==='10', {qtyVal});
  await p.locator('.qty-stepper .van-button').nth(1).click(); await sleep(300); // +
  const qtyVal2=await p.locator('.qty-val').innerText();
  log('Q3 加号步进 10->11', qtyVal2==='11', {qtyVal2});
  await p.locator('.qty-bar .van-button--primary').click(); await sleep(1800);
  t=await lb();
  log('Q4 确定数量后到行折扣步', /第五步/.test(t), {t:t.slice(0,30)});

  // 走到确认步，取消
  await tap('无折扣'); await sleep(1500);
  await tap('产品已添加完毕'); await sleep(1800);
  await tap('无整单优惠'); await sleep(4000);
  t=await lb();
  log('E1 到确认摘要步', /第九步/.test(t), {t:t.slice(0,30)});
  await tap('取消'); await sleep(1200);

  log('Z99 全程无红错无5xx', errs.length===0&&h5.length===0, {errs:errs.slice(0,3),h5});
  await p.screenshot({path:'automation_test/v4-browser-results/smart-order-paging.png',fullPage:true});
  const pass=R.filter(r=>r.ok).length;
  console.log(`\n==== PAGING/QTY ${pass}/${R.length} ====`);
  await b.close();
  process.exit(pass===R.length?0:1);
})();


