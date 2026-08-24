const { chromium } = require("playwright");
(async () => {
  const b = await chromium.launch({headless:true});
  const p = await b.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
  const reqs=[];
  p.on("response",r=>{ if(r.url().includes("/api/")) reqs.push(r.status()+" "+r.url().slice(r.url().indexOf("/api/"))); });
  await p.goto("http://43.128.145.141/mobile/login",{waitUntil:"networkidle"});
  await new Promise(r=>setTimeout(r,1500));
  const inputs = p.locator("input");
  await inputs.nth(0).fill("default");
  await inputs.nth(1).fill("admin");
  await inputs.nth(2).fill("Sh123456");
  await p.locator("button").filter({hasText:/登/}).first().click();
  await new Promise(r=>setTimeout(r,3000));
  console.log("URL:", p.url());
  console.log("BODY:", (await p.locator("body").innerText()).replace(/\s+/g," ").slice(0,500));
  console.log("API REQS:", reqs.filter(r=>r.includes("auth")||r.includes("login")));
  if(!p.url().includes("/login")) {
    for(const path of ["/mobile/home","/mobile/approvals","/mobile/surgery-reports","/mobile/messages"]) {
      await p.goto("http://43.128.145.141"+path,{waitUntil:"networkidle",timeout:15000}).catch(()=>{});
      await new Promise(r=>setTimeout(r,800));
      const t=(await p.locator("body").innerText()).replace(/\s+/g," ").trim();
      console.log(`\n[${path}] url=${p.url()} len=${t.length}`);
      console.log("  text:", t.slice(0,250));
      const err5xx = reqs.filter(r=>r.startsWith("5"));
      if(err5xx.length) console.log("  5xx:", err5xx);
    }
  }
  await b.close();
})();
