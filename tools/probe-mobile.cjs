const { chromium } = require("playwright");
(async () => {
  const b = await chromium.launch({headless:true});
  const p = await b.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
  const errs=[];
  const reqs=[];
  p.on("console",m=>{ if(m.type()==="error") errs.push(m.text()); });
  p.on("pageerror",e=>errs.push("PE:"+e.message));
  p.on("response",r=>{ if(r.url().includes("/auth/")||r.url().includes("/login")) reqs.push(r.status()+" "+r.url()); });
  await p.goto("http://43.128.145.141/mobile/login",{waitUntil:"networkidle"});
  await new Promise(r=>setTimeout(r,2000));
  console.log("URL:", p.url());
  console.log("INPUTS:");
  const inputs = await p.locator("input").all();
  for(let i=0;i<inputs.length;i++){
    const el = inputs[i];
    console.log(`  [${i}] type=${await el.getAttribute("type")} placeholder=${await el.getAttribute("placeholder")} name=${await el.getAttribute("name")} value="${await el.inputValue().catch(()=>"")}"`);
  }
  console.log("BUTTONS:");
  const btns = await p.locator("button, .van-button, [role=button]").all();
  for(let i=0;i<btns.length;i++){
    const t = (await btns[i].innerText().catch(()=>"")).trim();
    console.log(`  [${i}] "${t}"`);
  }
  console.log("AUTH REQUESTS:", reqs);
  const inputs2 = p.locator("input");
  for (let i=0;i<await inputs2.count();i++){
    const t = await inputs2.nth(i).getAttribute("type")||"text";
    if(t==="password") await inputs2.nth(i).fill("Sh123456");
    else { const v=await inputs2.nth(i).inputValue().catch(()=>""); if(!v) await inputs2.nth(i).fill("admin"); }
  }
  await p.screenshot({path: "tools/probe-mobile-before-click.png"});
  const loginBtn = p.locator("button, .van-button").filter({hasText:/登/}).first();
  console.log("login btn count:", await loginBtn.count());
  if(await loginBtn.count()) {
    await loginBtn.click();
    await new Promise(r=>setTimeout(r,3000));
  }
  console.log("URL after login:", p.url());
  console.log("AUTH REQUESTS after:", reqs);
  console.log("BODY:", (await p.locator("body").innerText()).replace(/\s+/g," ").slice(0,400));
  console.log("ERRS:", errs.slice(0,5));
  await p.screenshot({path: "tools/probe-mobile-after.png"});
  await b.close();
})();
