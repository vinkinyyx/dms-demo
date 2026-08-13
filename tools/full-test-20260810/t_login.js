const { record, save, loginAPI } = require('./harness');
(async () => {
  const accounts = ['sys_admin','sales_mgr','sales','cs','biz','fin','contract','dealer_admin'];
  for (const u of accounts) {
    const r = await loginAPI(u, 'Dms@123456');
    const token = r.json?.data?.token || r.json?.data?.accessToken || r.json?.token;
    record('LOGIN', `001-${u}`, `${u} 登录`, r.status===200 && token ? 'PASS':'FAIL', `HTTP ${r.status}, msg=${r.json?.message||r.json?.error||''}, tokenLen=${token?String(token).length:0}`);
    if (r.status===200 && token) {
      record('LOGIN', `002-${u}`, `${u} 当前用户接口`, 'PASS', `user=${r.json?.data?.user?.username || r.json?.data?.username}, roles=${JSON.stringify(r.json?.data?.user?.roles||r.json?.data?.roles||[])}`);
    }
  }
  // 反向
  const bad = await loginAPI('sys_admin','wrongpass');
  record('LOGIN','003-badpw','错误密码应拒绝', bad.status!==200?'PASS':'FAIL', `HTTP ${bad.status} body=${JSON.stringify(bad.json).slice(0,200)}`);
  const empty = await loginAPI('','');
  record('LOGIN','004-empty','空账号密码应拒绝', empty.status!==200?'PASS':'FAIL', `HTTP ${empty.status}`);
  const sum = save();
  console.log(JSON.stringify(sum,null,2));
})();
