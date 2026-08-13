const { chromium } = require('playwright');
(async()=>{
  const b = await chromium.launch({headless:true});
  const p = await (await b.newContext({viewport:{width:1440,height:900}})).newPage();
  await p.goto('http://8.133.193.238:8083/login',{waitUntil:'networkidle'});
  await p.fill('input[placeholder="账号"]','sys_admin');
  await p.fill('input[type="password"]','Dms@123456');
  await p.click('button:has-text("登 录")');
  await p.waitForURL(/\/home/);
  await p.waitForTimeout(4000);
  const info = await p.evaluate(()=>{
    const body = document.body.innerText;
    const cards = document.querySelectorAll('[class*="card"],[class*="kpi"],[class*="stat"],[class*="summary"]');
    const buttons = document.querySelectorAll('button,[role="button"],[class*="btn"]');
    const radioGroups = document.querySelectorAll('.el-radio-group,.el-radio-button,[class*="radio"],[class*="time"]');
    return {
      bodyLen: body.length,
      bodyStart: body.slice(0,2000),
      cardCount: cards.length,
      cardSamples: Array.from(cards).slice(0,15).map(e=>({cls:e.className, txt:e.textContent.trim().slice(0,80)})),
      btnSamples: Array.from(buttons).slice(0,20).map(e=>({tag:e.tagName,cls:e.className,txt:e.textContent.trim().slice(0,40)})),
      radioCount: radioGroups.length,
      radioSamples: Array.from(radioGroups).slice(0,15).map(e=>({cls:e.className,txt:e.textContent.trim().slice(0,80)}))
    };
  });
  console.log(JSON.stringify(info,null,2));
  await b.close();
})();
