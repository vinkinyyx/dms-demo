const { chromium } = require('playwright');
(async()=>{
  const b = await chromium.launch({headless:true});
  const p = await (await b.newContext({viewport:{width:1440,height:900}})).newPage();
  const errs=[];
  p.on('console',m=>{if(m.type()==='error')errs.push(m.text())});
  p.on('pageerror',e=>errs.push('PE:'+e.message));
  await p.goto('http://8.133.193.238:8083/login',{waitUntil:'networkidle'});
  await p.fill('input[placeholder="账号"]','sys_admin');
  await p.fill('input[type="password"]','Dms@123456');
  await p.click('button:has-text("登 录")');
  await p.waitForURL(/\/home/);
  await p.waitForTimeout(2000);
  // Try actual route /m/products
  await p.evaluate(()=>{ document.querySelector('#app').__vue_app__.config.globalProperties.$router.push('/m/products'); });
  await p.waitForTimeout(3000);
  console.log('After /m/products URL:',p.url());
  const body1 = await p.evaluate(()=>document.body.innerText);
  const tables1 = await p.locator('table,.el-table').count();
  console.log('bodyLen:',body1.length,'tables:',tables1,'isHome:',body1.includes('欢迎使用'));
  console.log('bodyStart:',body1.slice(0,300));
  await p.screenshot({path:'results/ui-nav/m-products.png'});
  // Click first menu item
  await p.evaluate(()=>{
    const items = document.querySelectorAll('.el-menu-item');
    console.log('Menu items count:', items.length);
    items.forEach((it,i)=>console.log(i, it.textContent.trim(), '->', it.getAttribute('data-route')||it.dataset.route));
  });
  await b.close();
})();
