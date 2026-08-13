const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const OUT = path.join(__dirname, 'screenshots');
if (!fs.existsSync(OUT)) fs.mkdirSync(OUT, {recursive:true});

const results = [];
function rec(scope, id, title, status, detail) {
  results.push({scope,id,title,status,detail:String(detail||'').slice(0,500)});
  const tag = status==='PASS'?'PASS':status==='FAIL'?'FAIL':status==='WARN'?'WARN':'INFO';
  console.log(`[${tag}] ${scope} ${id} ${title} :: ${String(detail||'').slice(0,200)}`);
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: {width:1440,height:900}, ignoreHTTPSErrors: true });
  const page = await ctx.newPage();
  const errors = [];
  page.on('console', msg => { if (msg.type()==='error') errors.push(msg.text()); });
  page.on('pageerror', err => errors.push('PAGEERROR: '+err.message));

  // TS-LOGIN-001 PC登录正向
  await page.goto('http://8.133.193.238:8083/login', {waitUntil:'networkidle'});
  await page.screenshot({path: path.join(OUT,'01-login-page.png')});
  const title = await page.title();
  rec('LOGIN-UI','001-01','登录页可访问', title ? 'PASS':'FAIL', `title=${title}`);
  // 检查租户代码字段是否存在
  const tenantInput = await page.locator('input[placeholder*="租户"], input[name="tenantCode"], #tenantCode').count();
  rec('LOGIN-UI','001-02','租户代码字段存在', tenantInput>0?'PASS':'WARN', `tenant field count=${tenantInput}`);

  // 登录
  await page.fill('input[placeholder="账号"]', 'sys_admin');
  await page.fill('input[type="password"]', 'Dms@123456');
  await page.screenshot({path: path.join(OUT,'02-login-filled.png')});
  await page.click('button:has-text("登 录")');
  await page.waitForURL(/\/(home|m\/)/, {timeout:10000}).catch(()=>{});
  await page.waitForTimeout(2000);
  const url1 = page.url();
  rec('LOGIN-UI','001-03','admin登录成功跳转/home', /home|m\//.test(url1)?'PASS':'FAIL', `url=${url1}`);
  await page.screenshot({path: path.join(OUT,'03-home.png'), fullPage:false});

  // 验证首页元素
  await page.goto('http://8.133.193.238:8083/home', {waitUntil:'networkidle'});
  await page.waitForTimeout(2000);
  const kpiCards = await page.locator('.kpi-card, .stat-card, [class*="kpi"], [class*="stat"]').count();
  rec('HOME-UI','001-01','首页KPI卡片渲染', kpiCards>=4?'PASS':'WARN', `kpi card count=${kpiCards}`);
  const tabs = await page.locator('.el-tabs__item, [role="tab"], [class*="tab"]').count();
  rec('HOME-UI','001-02','时间Tab存在', tabs>=3?'PASS':'WARN', `tab count=${tabs}`);
  const charts = await page.locator('canvas, .echarts, [class*="chart"], svg').count();
  rec('HOME-UI','001-03','图表渲染', charts>=2?'PASS':'WARN', `chart/svg count=${charts}`);
  await page.screenshot({path:path.join(OUT,'04-home-detail.png')});

  // 登出
  const userDropdown = page.locator('.avatar-wrapper, .user-info, [class*="avatar"], .header-right :last-child').first();
  if (await userDropdown.count() > 0) {
    await userDropdown.click().catch(()=>{});
    await page.waitForTimeout(500);
    const logoutBtn = page.locator('text=退出登录, text=登出, text=Logout').first();
    if (await logoutBtn.count()>0) {
      await logoutBtn.click();
      await page.waitForTimeout(1500);
      rec('LOGIN-UI','006-04','登出按钮可点击', /login/.test(page.url())?'PASS':'WARN', `url=${page.url()}`);
    } else {
      rec('LOGIN-UI','006-04','登出按钮存在','WARN','未找到退出按钮');
    }
  }

  // TS-LOGIN-002 反向 - 错误密码
  await page.goto('http://8.133.193.238:8083/login', {waitUntil:'networkidle'});
  await page.fill('input[placeholder="账号"]', 'sys_admin');
  await page.fill('input[type="password"]', 'wrongpass');
  await page.click('button:has-text("登 录")');
  await page.waitForTimeout(2000);
  const errMsg = await page.locator('.el-message--error, .el-form-item__error, [class*="error"]').first().textContent().catch(()=>'');
  rec('LOGIN-UI','002-01','错误密码显示错误提示', errMsg?'PASS':'FAIL', `msg=${errMsg}`);
  await page.screenshot({path:path.join(OUT,'05-login-error.png')});

  // 空表单提交
  await page.goto('http://8.133.193.238:8083/login', {waitUntil:'networkidle'});
  await page.click('button:has-text("登 录")');
  await page.waitForTimeout(1000);
  const validationErr = await page.locator('.el-form-item__error').count();
  rec('LOGIN-UI','002-02','空表单前端校验', validationErr>0?'PASS':'FAIL', `validation errors=${validationErr}`);

  // H5登录页
  const mobileCtx = await browser.newContext({ viewport:{width:390,height:844}, isMobile:true, hasTouch:true, userAgent:'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15' });
  const mp = await mobileCtx.newPage();
  const merrs = [];
  mp.on('console', msg=>{if(msg.type()==='error')merrs.push(msg.text())});
  mp.on('pageerror', e=>merrs.push('PAGEERROR:'+e.message));
  await mp.goto('http://8.133.193.238:8083/mobile/login', {waitUntil:'networkidle'});
  await mp.waitForTimeout(1500);
  const mTitle = await mp.title();
  rec('MOB-UI','001-01','移动端登录页可访问', mTitle?'PASS':'FAIL', `title=${mTitle}, url=${mp.url()}`);
  await mp.screenshot({path:path.join(OUT,'06-mobile-login.png')});
  await mp.fill('input[type="text"], input[placeholder="账号"]', 'sys_admin').catch(()=>{});
  await mp.fill('input[type="password"]', 'Dms@123456').catch(()=>{});
  await mp.click('button:has-text("登 录")').catch(()=>{});
  await mp.waitForTimeout(3000);
  rec('MOB-UI','001-02','移动端登录跳转', /mobile/.test(mp.url())?'PASS':'FAIL',`url=${mp.url()}`);
  await mp.screenshot({path:path.join(OUT,'07-mobile-home.png')});

  // 平台后台登录页
  await page.goto('http://8.133.193.238:8083/admin/', {waitUntil:'networkidle'});
  await page.waitForTimeout(1500);
  const adminUrl = page.url();
  rec('ADM-UI','001-01','平台后台登录页可访问', /admin/.test(adminUrl)?'PASS':'FAIL',`url=${adminUrl}`);
  await page.screenshot({path:path.join(OUT,'08-admin-login.png')});

  fs.writeFileSync(path.join(__dirname,'results','ui-login.json'), JSON.stringify(results,null,2));
  fs.writeFileSync(path.join(__dirname,'results','browser-errors.json'), JSON.stringify({pc:errors,mobile:merrs},null,2));
  console.log('\nPC errors:', errors.length, 'Mobile errors:', merrs.length);
  if (errors.length) console.log('PC errors sample:', errors.slice(0,5));
  if (merrs.length) console.log('Mobile errors sample:', merrs.slice(0,5));

  await browser.close();
})().catch(e=>{ console.error(e); process.exit(1); });

