const { chromium } = require('playwright');
const BASE='http://8.133.193.238:8083';
const results=[];
function check(n,c,d=''){results.push([n,!!c,d]);console.log((c?'PASS':'FAIL'),n,d);}
(async()=>{
  const browser=await chromium.launch({headless:true});
  const ctx=await browser.newContext({viewport:{width:1440,height:900},ignoreHTTPSErrors:true});
  const page=await ctx.newPage();
  const errs=[]; page.on('console',m=>{if(m.type()==='error')errs.push(m.text())});
  await page.goto(BASE+'/login',{waitUntil:'networkidle',timeout:30000});
  await page.fill('input[placeholder="账号"]','sys_admin');
  await page.fill('input[placeholder="密码"]','Dms@123456');
  await page.click('button');
  await page.waitForTimeout(4000);
  check('pc login success', !page.url().includes('/login'), page.url());
  check('pc bell present', await page.locator('.el-badge').count()>0 || await page.locator('[class*="bell"]').count()>0);
  await page.screenshot({path:'tools/pc-home.png'});
  await page.goto(BASE+'/notifications',{waitUntil:'networkidle',timeout:20000}); await page.waitForTimeout(1500);
  let t=await page.locator('body').innerText();
  check('pc notifications', /通知|消息/.test(t)&&!/404/.test(t), t.slice(0,60).replace(/\n/g,' '));
  await page.screenshot({path:'tools/pc-notifications.png'});
  await page.goto(BASE+'/login-logs',{waitUntil:'networkidle',timeout:20000}); await page.waitForTimeout(1500);
  t=await page.locator('body').innerText();
  check('pc login logs', (await page.locator('.el-table__row').count())>0, 'rows='+(await page.locator('.el-table__row').count()));
  await page.screenshot({path:'tools/pc-loginlogs.png'});
  await page.goto(BASE+'/approval/todo',{waitUntil:'networkidle',timeout:20000}); await page.waitForTimeout(2000);
  t=await page.locator('body').innerText();
  check('pc approval todo', /审批/.test(t), t.slice(0,60).replace(/\n/g,' '));
  await page.screenshot({path:'tools/pc-approval-todo.png'});

  const mctx=await browser.newContext({viewport:{width:390,height:844},isMobile:true,hasTouch:true,userAgent:'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148',ignoreHTTPSErrors:true});
  const mp=await mctx.newPage();
  await mp.goto(BASE+'/mobile/login',{waitUntil:'networkidle',timeout:30000});
  await mp.fill('input[placeholder="请输入账号"]','sys_admin');
  await mp.fill('input[placeholder="请输入密码"]','Dms@123456');
  await mp.click('button');
  await mp.waitForTimeout(4000);
  check('mobile login', !mp.url().includes('/mobile/login'), mp.url());
  await mp.goto(BASE+'/mobile/approvals',{waitUntil:'networkidle',timeout:20000}); await mp.waitForTimeout(2000);
  let mt=await mp.locator('body').innerText();
  check('mobile approvals list', /待我审批|移动审批|审批/.test(mt), mt.slice(0,60).replace(/\n/g,' '));
  await mp.screenshot({path:'tools/mobile-approvals.png'});
  const cells=mp.locator('.van-cell');
  if(await cells.count()>0){
    await cells.first().click(); await mp.waitForTimeout(2500);
    const dt=await mp.locator('body').innerText();
    check('mobile approval detail', /单据|类型|单号|审批/.test(dt), dt.slice(0,80).replace(/\n/g,' '));
    await mp.screenshot({path:'tools/mobile-approval-detail.png'});
  } else check('mobile approval detail',false,'no list item');
  await mp.goto(BASE+'/mobile/surgery-reports/create',{waitUntil:'networkidle',timeout:20000}); await mp.waitForTimeout(2000);
  const st=await mp.locator('body').innerText();
  check('mobile surgery create', /手术|报台|患者/.test(st), st.slice(0,60).replace(/\n/g,' '));
  check('mobile photo uploader', await mp.locator('.van-uploader').count()>0);
  check('mobile photo upload present', await mp.locator('.van-uploader').count()>0);
  await mp.screenshot({path:'tools/mobile-surgery-create.png'});
  await mp.goto(BASE+'/mobile/messages',{waitUntil:'networkidle',timeout:20000}); await mp.waitForTimeout(1500);
  const msgt=await mp.locator('body').innerText();
  check('mobile messages', /消息|通知/.test(msgt), msgt.slice(0,60).replace(/\n/g,' '));
  await mp.screenshot({path:'tools/mobile-messages.png'});

  const failed=results.filter(r=>!r[1]);
  console.log(JSON.stringify({total:results.length,passed:results.length-failed.length,failed:failed.length,consoleErrors:errs.length}));
  if(errs.length)console.log('ERRS:',errs.slice(0,8).join(' | '));
  await browser.close();
  process.exit(failed.length?1:0);
})();


