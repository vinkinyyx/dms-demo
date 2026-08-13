const { chromium } = require('./node_modules/playwright');
const base = 'http://43.128.145.141:8083';
(async()=>{
 const browser = await chromium.launch({headless:true});
 const page = await browser.newPage({viewport:{width:1440,height:960}});
 for (const url of [base+'/login', base+'/admin/login', base+'/m/login']) {
  await page.goto(url,{waitUntil:'domcontentloaded'});
  await page.waitForTimeout(2000);
  const data = await page.evaluate(()=> ({url:location.href, title:document.title, inputs:[...document.querySelectorAll('input')].map((i,idx)=>({idx,type:i.type,placeholder:i.placeholder,value:i.value,name:i.name,id:i.id,cls:i.className, aria:i.getAttribute('aria-label')})), buttons:[...document.querySelectorAll('button')].map((b,idx)=>({idx,text:b.innerText,type:b.type,cls:b.className}))}));
  console.log(JSON.stringify(data,null,2));
 }
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
