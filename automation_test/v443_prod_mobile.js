// 生产环境移动端 H5 v4.4.3 列表加载修复验证（修正登录字段定位）
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const BASE = process.env.E2E_BASE || 'http://8.133.193.238';
const OUTDIR = path.join(__dirname, 'v4-browser-results');
fs.mkdirSync(OUTDIR, { recursive: true });

(async () => {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({
    ignoreHTTPSErrors: true,
    viewport: { width: 393, height: 852 },
    isMobile: true, hasTouch: true,
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1'
  });
  const page = await ctx.newPage();
  const errs = [], http5 = [], http4 = [];
  page.on('console', m => { if (m.type()==='error'){ const t=m.text(); if(!/favicon|ResizeObserver|cdn|jsdelivr|tailwind/i.test(t)) errs.push(t.slice(0,180)); }});
  page.on('pageerror', e => errs.push('pageerror: '+String(e).slice(0,180)));
  page.on('response', r => { const s=r.status(); const u=r.url(); if(s>=500)http5.push(s+' '+u.replace(BASE,'').slice(0,100)); else if(s>=400&&s!==401&&!/assets|cdn/.test(u))http4.push(s+' '+u.replace(BASE,'').slice(0,100)); });

  await page.goto(BASE + '/dms/mobile/login', { waitUntil: 'networkidle' }).catch(()=>{});
  await page.waitForTimeout(1500);
  await page.locator('input[placeholder="租户代码"]').fill('default').catch(e=>console.log('tenant fill fail',e.message));
  await page.locator('input[placeholder="请输入账号"]').fill('admin').catch(e=>console.log('user fill fail',e.message));
  await page.locator('input[type="password"]').fill('Sh123456').catch(e=>console.log('pwd fill fail',e.message));
  // submit button is the van-button native-type=submit
  await page.locator('button.van-button--primary.van-button--block, button[type="submit"], .van-form button.van-button--primary').first().click().catch(e=>console.log('click fail',e.message));
  await page.waitForTimeout(5500);
  await page.waitForLoadState('networkidle').catch(()=>{});
  console.log('after login url:', page.url());
  const token = await page.evaluate(() => localStorage.getItem('dms_access_token'));
  console.log('token set:', !!token);
  if (!token) {
    console.log('LOGIN FAILED, body:', (await page.evaluate(()=>document.body.innerText)).replace(/\s+/g,' ').slice(0,200));
    await page.screenshot({ path: path.join(OUTDIR,'prod-mobile-loginfail.png') });
    await browser.close(); process.exit(1);
  }

  const pages = [
    { name: 'MOrders', cn: '销售订单', url: BASE+'/dms/mobile/orders' },
    { name: 'MApprovals', cn: '移动审批', url: BASE+'/dms/mobile/approvals' },
    { name: 'MMessages', cn: '消息中心', url: BASE+'/dms/mobile/messages' },
  ];
  const results = [];
  for (const p of pages) {
    await page.goto(p.url, { waitUntil: 'networkidle' }).catch(()=>{});
    await page.waitForTimeout(3000);
    await page.mouse.wheel(0, 700); await page.waitForTimeout(1200);
    await page.mouse.wheel(0, 1000); await page.waitForTimeout(1500);
    const body = await page.evaluate(() => document.body.innerText);
    const cards = await page.locator('.van-card, .order-card, .approval-item, .van-cell').count();
    const loading = /加载中/.test(body);
    const empty = /暂无|空空如也|没有更多|无数据/.test(body);
    await page.screenshot({ path: path.join(OUTDIR, 'prod-mobile-'+p.name+'.png') }).catch(()=>{});
    const snippet = body.replace(/\s+/g,' ').slice(0, 260);
    console.log(`\n=== ${p.name} ${p.cn} ===`);
    console.log('cards/cells:', cards, '| loading:', loading, '| emptyHint:', empty);
    console.log('body:', snippet);
    results.push({ page: p.name, cards, loading, snippet });
  }
  console.log('\n--- console errors:', JSON.stringify(errs.slice(0,10)));
  console.log('--- http5xx:', JSON.stringify(http5));
  console.log('--- http4xx:', JSON.stringify(http4.slice(0,10)));
  await browser.close();
})();
