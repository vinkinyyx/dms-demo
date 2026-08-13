const { chromium } = require('playwright');
(async()=>{
  const b = await chromium.launch({headless:true});
  const p = await (await b.newContext({viewport:{width:1440,height:900}})).newPage();
  await p.goto('http://8.133.193.238:8083/login',{waitUntil:'networkidle'});
  await p.waitForTimeout(2000);
  const inputs = await p.locator('input').all();
  for (const i of inputs) {
    const info = await i.evaluate(el=>({type:el.type,placeholder:el.placeholder,name:el.name,id:el.id,className:el.className,visible:el.offsetParent!==null}));
    console.log(JSON.stringify(info));
  }
  const btns = await p.locator('button').all();
  for (const b of btns) {
    const info = await b.evaluate(el=>({text:el.textContent.trim(),className:el.className,visible:el.offsetParent!==null}));
    console.log('BTN:', JSON.stringify(info));
  }
  await b.close();
})();
