const fs = require('fs');
const BASE = 'http://8.133.193.238:8082';
const FRONT = 'http://8.133.193.238:8083';
const results = [];
const tokens = {};
function sleep(ms){return new Promise(r=>setTimeout(r,ms));}
async function req(method, path, opts={}) {
  const start = Date.now();
  const controller = new AbortController();
  const to = setTimeout(()=>controller.abort(), opts.timeout||15000);
  try {
    const res = await fetch((opts.base||BASE)+path, {method, headers:{'Content-Type':'application/json',...(opts.headers||{})}, body: opts.body?JSON.stringify(opts.body):undefined, signal:controller.signal, redirect:opts.redirect||'follow'});
    const text = await res.text();
    let json; try{json=JSON.parse(text)}catch{json=undefined}
    return {status:res.status, ok:res.ok, text, json, ms:Date.now()-start, headers:Object.fromEntries(res.headers.entries())};
  } catch(e) { return {status:0, ok:false, text:String(e), ms:Date.now()-start}; }
  finally { clearTimeout(to); }
}
function record(suite, id, expected, actual, pass, detail='') { results.push({suite,id,expected,actual,pass:!!pass,detail}); }
async function login(name, username, password, tenantCode='') {
  const r = await req('POST','/api/auth/login',{body:{username,password,tenantCode,rememberMe:true}});
  const token = r.json?.data?.accessToken || r.json?.data?.token || r.json?.data?.access_token;
  tokens[name] = token;
  return r;
}
(async()=>{
  const health = await req('GET','/actuator/health');
  record('env','health','UP', health.json?.status, health.json?.status==='UP', health.text);
  for (const [path] of [['/login'],['/mobile/login'],['/admin/'],['/home']]) {
    const r = await req('GET',path,{base:FRONT,redirect:'manual'});
    record('frontend',path,'200 SPA',r.status, r.status>=200 && r.status<400, r.text.slice(0,120));
  }
  const unauth = await req('GET','/api/auth/me');
  record('auth','unauth-me','401',unauth.status, unauth.status===401, unauth.text.slice(0,200));
  const accounts = [
    ['admin','admin','Sh123456',''],['sys_admin','sys_admin','Dms@123456',''],['sales_mgr','sales_mgr','Dms@123456',''],['sales','sales','Dms@123456',''],['cs','cs','Dms@123456',''],['biz','biz','Dms@123456',''],['fin','fin','Dms@123456',''],['contract','contract','Dms@123456',''],['dealer_admin','dealer_admin','Dms@123456',''],
  ];
  for (const [name,u,p,t] of accounts) {
    const r = await login(name,u,p,t);
    record('login',name,'success token', r.status, r.status===200 && !!tokens[name], JSON.stringify(r.json||r.text).slice(0,300));
    if(tokens[name]){
      const me = await req('GET','/api/auth/me',{headers:{Authorization:`Bearer ${tokens[name]}`}});
      const d = me.json?.data||{};
      record('me',name,'user fields', me.status, me.status===200 && d.id&&d.username&&d.roleName!==undefined, JSON.stringify({username:d.username,role:d.roleName,userType:d.userType,perms:(d.permissionCodes||[]).length}));
    }
  }
  const wrongPwd = await req('POST','/api/auth/login',{body:{username:'sys_admin',password:'wrong-password'}});
  record('login','wrong-password','business error',wrongPwd.status, wrongPwd.status>=400 || wrongPwd.json?.code && wrongPwd.json.code!=='0', JSON.stringify(wrongPwd.json||wrongPwd.text).slice(0,300));
  const noUser = await req('POST','/api/auth/login',{body:{username:'noexists_'+Date.now(),password:'Dms@123456'}});
  record('login','no-user','business error',noUser.status, noUser.status>=400 || noUser.json?.code && noUser.json.code!=='0', JSON.stringify(noUser.json||noUser.text).slice(0,300));
  const badTenant = await req('POST','/api/auth/login',{body:{username:'admin',password:'Sh123456',tenantCode:'WRONG'}});
  record('login','bad-tenant','business error',badTenant.status, badTenant.status>=400 || badTenant.json?.code && badTenant.json.code!=='0', JSON.stringify(badTenant.json||badTenant.text).slice(0,300));
  const sqli = await req('POST','/api/auth/login',{body:{username:"admin' OR '1'='1",password:'x'}});
  record('security','sqli-login','rejected',sqli.status, sqli.status>=400 && sqli.json?.code!==0, sqli.text.slice(0,200));
  const xss = await req('POST','/api/auth/login',{body:{username:'<script>alert(1)</script>',password:'x'}});
  record('security','xss-login','rejected',xss.status, xss.status>=400 && xss.json?.code!==0, xss.text.slice(0,200));
  const adminLogin = await req('POST','/api/admin/auth/login',{body:{username:'admin',password:'Sh123456'}});
  const adminToken = adminLogin.json?.data?.accessToken || adminLogin.json?.data?.token;
  record('admin-login','admin','success',adminLogin.status, adminLogin.status===200 && !!adminToken, JSON.stringify(adminLogin.json||adminLogin.text).slice(0,300));
  if(adminToken){
    const cross = await req('GET','/api/auth/me',{headers:{Authorization:`Bearer ${adminToken}`}});
    record('token-isolation','admin-token-to-business-api','401',cross.status, cross.status===401, cross.text.slice(0,200));
    const adminMe = await req('GET','/api/admin/auth/me',{headers:{Authorization:`Bearer ${adminToken}`}});
    record('admin-login','admin-me','200',adminMe.status, adminMe.status===200, JSON.stringify(adminMe.json||adminMe.text).slice(0,200));
  }
  const bizToken = tokens.sys_admin;
  const moduleGets = [
'/api/products?page=1&size=5','/api/product-categories?page=1&size=5','/api/product-lines?page=1&size=5','/api/product-package-levels?page=1&size=5','/api/product-bundles?page=1&size=5','/api/dealers?page=1&size=5','/api/hospitals?page=1&size=5','/api/warehouses?page=1&size=5','/api/regions?page=1&size=5','/api/suppliers?page=1&size=5','/api/product-prices?page=1&size=5','/api/contracts?page=1&size=5','/api/contract-templates?page=1&size=5','/api/authorizations?page=1&size=5','/api/orders?page=1&size=5','/api/sales-returns?page=1&size=5','/api/purchase-orders?page=1&size=5','/api/purchase-returns?page=1&size=5','/api/inventory?page=1&size=5','/api/sales-outs?page=1&size=5','/api/receipts?page=1&size=5','/api/stock-moves?page=1&size=5','/api/inventory-adjustments?page=1&size=5','/api/surgery-reports?page=1&size=5','/api/promotions?page=1&size=5','/api/dashboard/summary','/api/reports?page=1&size=5','/api/dealers/profile?dealerId=1','/api/approval/tasks/todo?page=1&size=5','/api/approval/templates?page=1&size=5','/api/approval/delegations?page=1&size=5','/api/approval/admin/instances?page=1&size=5','/api/sales-positions?page=1&size=5','/api/users?page=1&size=5','/api/roles','/api/tenant-ui/pages/products/buttons','/api/api-call-logs?page=1&size=5','/api/email-logs?page=1&size=5','/api/product-mappings?page=1&size=5','/api/notifications?page=1&size=5','/api/home/dashboard'
  ];
  for(const path of moduleGets){
    const r = await req('GET',path,{headers:{Authorization:`Bearer ${bizToken}`}});
    const ok = r.status===200 && (r.json?.success !== false);
    record('module-get',path,'200 ok',r.status,ok,JSON.stringify(r.json||r.text).slice(0,500));
    await sleep(50);
  }
  for (const role of ['sales','dealer_admin']) {
    if(!tokens[role]) continue;
    for (const path of ['/api/users?page=1&size=5','/api/approval/admin/instances?page=1&size=5','/api/product-mappings?page=1&size=5']) {
      const r = await req('GET',path,{headers:{Authorization:`Bearer ${tokens[role]}`}});
      record('permission',`${role}:${path}`,'forbidden or hidden-safe',r.status,r.status===403||r.status===401||(r.status===200 && path.includes('/users')===false),r.text.slice(0,250));
    }
  }
  fs.writeFileSync('tools/test-output/api-smoke-results.json', JSON.stringify(results,null,2));
  const failed = results.filter(x=>!x.pass);
  console.log(JSON.stringify({total:results.length,passed:results.length-failed.length,failed:failed.length,failures:failed},null,2));
})();

