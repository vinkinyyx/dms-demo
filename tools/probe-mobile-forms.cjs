const { chromium } = require('playwright');
const BASE='http://43.128.145.141/dms';
(async()=>{
 const browser=await chromium.launch({headless:true});
 const ctx=await browser.newContext({viewport:{width:393,height:852},isMobile:true,hasTouch:true,deviceScaleFactor:2});
 const page=await ctx.newPage();
 page.on('console',m=>console.log('C',m.type(),m.text()));
 await page.goto(BASE+'/mobile/login',{waitUntil:'domcontentloaded'});
 await page.waitForTimeout(1000);
 await page.locator('.van-field').filter({hasText:'租户'}).locator('input').fill('default');
 await page.locator('.van-field').filter({hasText:'账号'}).locator('input').fill('sys_admin');
 await page.locator('input[type=password]').first().fill('Dms@123456');
 await page.getByRole('button',{name:/登\s*录/}).click();
 await page.waitForURL(u=>!u.pathname.includes('/login'),{timeout:15000}).catch(()=>{});
 await page.waitForTimeout(2000);
 for (const url of ['/mobile/surgery-reports/create','/mobile/orders/create']) {
  console.log('\nURL',url);
  await page.goto(BASE+url,{waitUntil:'domcontentloaded'}); await page.waitForTimeout(1500);
  for (const label of ['经销商','医院','仓库','订单类型']) {
   const field=page.locator('.van-field').filter({hasText:label}).first();
   if (await field.count()) {
    await field.click(); await page.waitForTimeout(600);
    const n=await page.locator('.van-picker:visible .van-picker-column__item').filter({hasNotText:/^请选择/}).count();
    console.log(label,'options',n);
    if (n) {
      await page.locator('.van-picker:visible .van-picker-column__item').filter({hasNotText:/^请选择/}).first().click();
      await page.waitForTimeout(200);
      await page.locator('.van-picker:visible .van-picker__confirm').first().click();
      await page.waitForTimeout(400);
      const val=await field.locator('input').first().inputValue().catch(e=>'ERR:'+e.message);
      console.log(label,'inputValue=',val);
    }
   }
  }
  const date=page.locator('.van-field').filter({hasText:'日期'}).first();
  if (await date.count()) { console.log('date before', await date.locator('input').first().inputValue().catch(e=>e.message)); await date.click(); await page.waitForTimeout(500); await page.locator('.van-picker:visible .van-picker__confirm').first().click().catch(e=>console.log('date confirm err',e.message)); await page.waitForTimeout(500); console.log('date after', await date.locator('input').first().inputValue().catch(e=>e.message)); }
  const product=page.locator('.van-field').filter({hasText:'产品'}).first();
  await product.click(); await page.waitForTimeout(2000);
  console.log('popup cells',await page.locator('.van-popup:visible .van-cell').count());
  console.log('popup first html',await page.locator('.van-popup:visible .van-cell').first().evaluate(e=>e.outerHTML.slice(0,800)).catch(e=>e.message));
  await page.locator('.van-popup:visible .van-cell').first().click({force:true}).catch(e=>console.log('click err',e.message));
  await page.waitForTimeout(1000);
  console.log('after click popup visible',await page.locator('.van-popup:visible').count());
  console.log('product input',await product.locator('input').first().inputValue().catch(e=>e.message));
  console.log('batch',await page.locator('.van-field').filter({hasText:/批号|序列号/}).count());
  console.log('qty stepper',await page.locator('.van-stepper').count());
  await page.screenshot({path:'D:/Workspace/TRAE/DMS/automation_test/v4-browser-results/probe-'+url.replace(/\W/g,'-')+'.png',fullPage:true});
 }
 await browser.close();
})();
