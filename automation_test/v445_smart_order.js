// 智能下单对话向导：完整链路验证（销售提交 / 补货 / 样品 / 容错 / 代金券）
const { chromium } = require('playwright');
const BASE = process.env.E2E_BASE || 'http://dms-dev.mysolmed.com';
const results = [];
function log(n, ok, d){ results.push({n,ok}); console.log((ok?'PASS ':'FAIL ')+n+' :: '+JSON.stringify(d).slice(0,280)); }
const sleep = ms => new Promise(r=>setTimeout(r,ms));

async function mobileLogin(page){
  await page.goto(BASE+'/dms/mobile/login',{waitUntil:'networkidle'}).catch(()=>{});
  await sleep(1200);
  await page.evaluate(()=>localStorage.clear());
  await page.locator('input[placeholder="租户代码"]').fill('default').catch(()=>{});
  await page.locator('input[placeholder="请输入账号"]').fill('admin').catch(()=>{});
  await page.locator('input[type="password"]').fill('Sh123456').catch(()=>{});
  await page.locator('.van-form button.van-button--primary').first().click().catch(()=>{});
  await sleep(5000);
}
// 只点最后一条机器人气泡里的选项
async function tapOpt(page, re){
  const last = page.locator('.bubble.bot').last();
  const btn = last.locator('.opt-btn', {hasText: re}).first();
  await btn.waitFor({timeout:10000});
  await btn.click();
}
async function tapOptIndex(page, idx){
  const last = page.locator('.bubble.bot').last();
  const btn = last.locator('.opt-btn').nth(idx);
  await btn.waitFor({timeout:10000});
  await btn.click();
}
async function lastBubble(page){ return await page.locator('.bubble.bot').last().innerText().catch(()=> ''); }
async function inputSend(page, val){
  const inp = page.locator('.input-bar input');
  await inp.waitFor({state:'visible',timeout:15000});
  await sleep(300);
  await inp.fill(String(val));
  await page.locator('.input-bar button').click();
}
async function gotoWizard(page){
  await page.goto(BASE+'/dms/mobile/smart-order',{waitUntil:'networkidle'}).catch(()=>{});
  await sleep(2000);
}

(async()=>{
  const browser = await chromium.launch({headless:true});
  const ctx = await browser.newContext({viewport:{width:393,height:852},isMobile:true,hasTouch:true,
    userAgent:'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1'});
  const page = await ctx.newPage();
  const errs=[]; page.on('console',m=>{ if(m.type()==='error'){const t=m.text(); if(!/favicon|cdn|jsdelivr|tailwind|ResizeObserver/i.test(t))errs.push(t.slice(0,160));}});
  page.on('pageerror',e=>errs.push('pe:'+String(e).slice(0,160)));
  const h5=[]; page.on('response',r=>{const s=r.status(); if(s>=500)h5.push(s+' '+r.url().replace(BASE,'').slice(0,90));});

  await mobileLogin(page);

  // ===== S1: 首页入口 =====
  await page.goto(BASE+'/dms/mobile/home',{waitUntil:'networkidle'}).catch(()=>{});
  await sleep(2500);
  const homeText = await page.locator('.quick-grid').innerText().catch(()=> '');
  log('S1-首页快捷入口含智能下单', /智能下单/.test(homeText), {has:/智能下单/.test(homeText)});

  await gotoWizard(page);
  let b = await lastBubble(page);
  log('S2-向导首屏3类型', /销售订单/.test(b)&&/补货订单/.test(b)&&/样品订单/.test(b), {b:b.slice(0,40)});

  // ===== 链路A：销售订单 -> 产品 -> 数量 -> 行折扣(无) -> 加产品 -> 整单无优惠 -> 保存草稿 =====
  await tapOpt(page, '销售订单');
  await sleep(1500);
  b = await lastBubble(page);
  log('A1-选销售->选客户步', /第二步/.test(b), {b:b.slice(0,30)});
  await tapOptIndex(page, 0); // 第一个客户 DLR-001
  await sleep(2000);
  b = await lastBubble(page);
  log('A2-选客户->产品搜索步', /第三步/.test(b) && await page.locator('.input-bar input').count()===1, {b:b.slice(0,30)});
  await inputSend(page, 'PRD');
  await sleep(2800);
  b = await lastBubble(page);
  let prodN = await page.locator('.bubble.bot').last().locator('.opt-btn').count();
  log('A3-搜索产品出结果', /找到/.test(b) && prodN>=1, {b:b.slice(0,60), prodN});
  if (prodN===0) { await inputSend(page, 'a'); await sleep(2800); b=await lastBubble(page); prodN = await page.locator('.bubble.bot').last().locator('.opt-btn').count(); log('A3b-宽关键词出结果', prodN>=1, {prodN}); }
  await tapOptIndex(page, 0);
  await sleep(1500);
  b = await lastBubble(page);
  log('A4-选产品->问数量', /第四步/.test(b)&&/数量/.test(b), {b:b.slice(0,30)});
  // 数量容错：非法 abc
  await inputSend(page, 'abc');
  await sleep(1000);
  b = await lastBubble(page);
  log('A5-非法数量被拦截', /大于 0 的整数|正整数/.test(b), {b:b.slice(0,50)});
  await inputSend(page, '5');
  await sleep(1500);
  b = await lastBubble(page);
  log('A6-数量合法->行折扣步(销售有)', /第五步/.test(b)&&/行折扣/.test(b), {b:b.slice(0,40)});
  await tapOpt(page, '无折扣');
  await sleep(1500);
  b = await lastBubble(page);
  log('A7-无行折扣->问继续加产品', /第六步/.test(b)&&/继续新增产品|继续添加/.test(b), {b:b.slice(0,40)});
  // 再加一个产品
  await tapOpt(page, '继续添加');
  await sleep(1500);
  b = await lastBubble(page);
  log('A8-继续->产品搜索', /第三步/.test(b), {b:b.slice(0,30)});
  await inputSend(page, 'a');
  await sleep(2800);
  prodN = await page.locator('.bubble.bot').last().locator('.opt-btn').count();
  // 选第二个产品（避免与第一个重复）
  if (prodN>=2) { await tapOptIndex(page, 1); } else { await tapOptIndex(page, 0); }
  await sleep(1500);
  b = await lastBubble(page);
  if (/已在订单中|重复/.test(b)) {
    // 重复则选别的
    await tapOptIndex(page, Math.min(2, prodN-1));
    await sleep(1500);
  }
  await inputSend(page, '3');
  await sleep(1500);
  b = await lastBubble(page);
  if (/第五步/.test(b)) { await tapOpt(page, '无折扣'); await sleep(1500); }
  b = await lastBubble(page);
  await tapOpt(page, /否|产品已添加/);
  await sleep(1500);
  b = await lastBubble(page);
  log('A9-完成加产品->整单优惠步(销售有)', /第七步/.test(b)&&/整单优惠/.test(b), {b:b.slice(0,40)});
  await tapOpt(page, '无整单优惠');
  await sleep(3500);
  b = await lastBubble(page);
  log('A10-到确认步含摘要+金额', /第九步/.test(b)&&/应付金额|本单金额/.test(b)&&/summary-card|¥/.test(b), {b:b.slice(0,80)});
  const sumLines = await page.locator('.bubble.bot').last().locator('.sum-line').count();
  log('A11-摘要含2个产品行', sumLines>=2, {sumLines});
  // 保存草稿
  await tapOpt(page, '保存草稿');
  await sleep(4000);
  b = await lastBubble(page);
  log('A12-保存草稿成功', /已保存为草稿/.test(b), {b:b.slice(0,80)});

  // ===== 链路B：补货订单 -> 跳过所有折扣 -> 确认步显示本单0金额 =====
  await gotoWizard(page);
  await tapOpt(page, '补货订单');
  await sleep(1500);
  // 找一个开通寄售的客户（DLR-012=青岛海诺 在 credit 列表）；先点 DLR-012（第12个）若无则看拦截
  // 直接点第一个，若拦截则改点“重新选择客户”再选 DLR-012
  await tapOptIndex(page, 0);
  await sleep(2000);
  b = await lastBubble(page);
  if (/尚未开通寄售/.test(b)) {
    log('B0-补货对未寄售客户拦截', true, {b:b.slice(0,50)});
    await tapOpt(page, '重新选择客户');
    await sleep(1500);
    // 选 DLR-012（索引11）
    await tapOptIndex(page, 11);
    await sleep(2000);
    b = await lastBubble(page);
  }
  log('B1-补货(寄售客户)->产品搜索', /第三步/.test(b), {b:b.slice(0,40)});
  await inputSend(page, 'a');
  await sleep(2800);
  await tapOptIndex(page, 0);
  await sleep(1500);
  await inputSend(page, '2');
  await sleep(1800);
  b = await lastBubble(page);
  // 补货：数量后应直接到“继续加产品”（第六步），无第五步行折扣、无样品原因
  log('B2-补货数量后跳过行折扣直接到第六步', /第六步/.test(b)&&/继续/.test(b), {b:b.slice(0,50)});
  await tapOpt(page, /否|产品已添加/);
  await sleep(3500);
  b = await lastBubble(page);
  // 补货应跳过第七步整单优惠，直接到确认（第九步），且本单金额 0.00
  log('B3-补货跳过整单优惠直接确认', /第九步/.test(b)&&/本单金额/.test(b), {b:b.slice(0,90)});
  const b3zero = /¥0\.00/.test(b);
  log('B4-补货本单金额0.00', b3zero, {zero:b3zero});
  // 取消
  await tapOpt(page, '取消');
  await sleep(1500);
  b = await lastBubble(page);
  log('B5-取消不建单', /已取消本单/.test(b), {b:b.slice(0,40)});

  // ===== 链路C：样品订单 -> 数量后问申请原因 -> 单品 -> 确认 =====
  await gotoWizard(page);
  await tapOpt(page, '样品订单');
  await sleep(1500);
  await tapOptIndex(page, 0);
  await sleep(2000);
  b = await lastBubble(page);
  log('C1-样品->产品搜索', /第三步/.test(b), {b:b.slice(0,30)});
  await inputSend(page, 'a');
  await sleep(2800);
  await tapOptIndex(page, 0);
  await sleep(1500);
  await inputSend(page, '1');
  await sleep(1800);
  b = await lastBubble(page);
  log('C2-样品数量后问申请原因(非行折扣)', /样品申请原因|申请原因/.test(b), {b:b.slice(0,50)});
  await inputSend(page, 'XX医院骨科术中试用');
  await sleep(3500);
  b = await lastBubble(page);
  // 样品单品：跳过第六步加产品、跳过第七步优惠，直接确认
  log('C3-样品单品直接到确认(无第六/七步)', /第九步/.test(b)&&/本单金额/.test(b), {b:b.slice(0,80)});
  const cLines = await page.locator('.bubble.bot').last().locator('.sum-line').count();
  log('C4-样品摘要仅1行', cLines===1, {cLines});

  // ===== 容错：输入0重新开始 =====
  await gotoWizard(page);
  await tapOpt(page, '销售订单');
  await sleep(1200);
  await tapOptIndex(page, 0);
  await sleep(1800);
  // 在产品搜索输入框输入 0
  const inp = page.locator('.input-bar input');
  await inp.waitFor({timeout:8000});
  await inp.fill('0');
  await page.locator('.input-bar button').click();
  await sleep(1500);
  b = await lastBubble(page);
  log('Z1-输入0回到第一步', /第一步/.test(b)&&/订单类型/.test(b), {b:b.slice(0,40)});

  log('Z99-全程console无红错/无5xx', errs.length===0 && h5.length===0, {errs:errs.slice(0,3), h5});
  await page.screenshot({path:'automation_test/v4-browser-results/smart-order-flow.png', fullPage:true});

  const pass=results.filter(r=>r.ok).length;
  console.log(`\n==== SMART ORDER SUMMARY ${pass}/${results.length} ====`);
  await browser.close();
  process.exit(pass===results.length?0:1);
})();
