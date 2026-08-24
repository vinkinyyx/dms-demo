const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

const BASE = "http://43.128.145.141";
const OUT = path.join(__dirname, "..", "automation_test", "v4-browser-results", "deep-audit-api");
fs.mkdirSync(OUT, { recursive: true });

(async () => {
  const b = await chromium.launch({headless:true});
  const p = await b.newPage({viewport:{width:1440,height:900}});
  let token = "";
  let loginResp = null;
  p.on("response", async r => {
    if (r.url().includes("/api/auth/login") && r.ok()) {
      loginResp = r;
      try {
        const j = await r.json();
        token = j.data?.token || j.token || (j.data && (j.data.accessToken || j.data.access_token)) || "";
        console.log("LOGIN RESP keys:", Object.keys(j));
        console.log("LOGIN data keys:", j.data ? Object.keys(j.data) : "no data");
        if (!token && j.data) {
          for (const k of Object.keys(j.data)) {
            if (typeof j.data[k] === "string" && j.data[k].length > 40) { token = j.data[k]; console.log("using field as token:", k); break; }
          }
        }
      } catch(e) { console.log("json parse err", e.message); }
    }
  });

  await p.goto(`${BASE}/login`,{waitUntil:"networkidle"});
  await new Promise(r=>setTimeout(r,800));
  const inputs = p.locator(".login-form input");
  await inputs.nth(0).fill("default");
  await inputs.nth(1).fill("admin");
  await inputs.nth(2).fill("Sh123456");
  await p.locator(".btn-login").first().click();
  await p.waitForURL(/^(?!.*\/login).*$/,{timeout:15000}).catch(()=>{});
  await new Promise(r=>setTimeout(r,2000));
  console.log("URL after login:", p.url());
  console.log("TOKEN:", token ? "acquired len="+token.length : "MISSING");

  const storage = await p.context().storageState();
  const cookieToken = storage.cookies.find(c => /token|auth/i.test(c.name));
  console.log("Cookie token:", cookieToken ? cookieToken.name+" len="+cookieToken.value.length : "none");
  const ls = await p.evaluate(() => {
    const out = {};
    for (let i=0;i<localStorage.length;i++) { const k=localStorage.key(i); out[k]=(localStorage.getItem(k)||"").slice(0,60); }
    return out;
  });
  console.log("localStorage keys:", Object.keys(ls));
  console.log("localStorage:", JSON.stringify(ls).slice(0,500));

  if (!token && cookieToken) token = cookieToken.value;
  if (!token) {
    for (const k of Object.keys(ls)) {
      if (/token/i.test(k) && ls[k].length > 30) { token = ls[k]; console.log("using ls token:", k); break; }
    }
  }

  const apiChecks = [
    "/api/products?page=1&size=5",
    "/api/dealers?page=1&size=5",
    "/api/orders?page=1&size=5",
    "/api/inventory?page=1&size=5",
    "/api/categories/list",
    "/api/dashboard/stats",
    "/api/menus/tree",
    "/api/operation-log/list/product/1",
  ];
  for (const url of apiChecks) {
    const opts = { headers: {} };
    if (token) opts.headers.Authorization = `Bearer ${token}`;
    try {
      const r = await p.request.get(`${BASE}${url}`, opts);
      const txt = (await r.text()).slice(0, 250);
      console.log(`[${r.status()}] ${url} :: ${txt}`);
    } catch(e) { console.log(`[ERR] ${url} :: ${e.message}`); }
  }

  await p.screenshot({path: path.join(OUT,"pc-after-login.png")});
  await b.close();
})();
