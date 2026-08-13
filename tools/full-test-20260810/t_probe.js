const { loginAPI, apiGet } = require('./harness');
(async()=>{
  const r = await loginAPI('sys_admin','Dms@123456');
  console.log(JSON.stringify(r.json, null, 2).slice(0,3000));
  const token = r.json.data.token;
  for (const p of ['/api/auth/me','/api/auth/permissions','/api/users/me','/api/menus/my','/api/menus/user-menus','/api/rbac/my-permissions','/api/tenant/info']) {
    const x = await apiGet(token, p);
    console.log('\n>>>', p, x.status, JSON.stringify(x.json).slice(0,500));
  }
})();
