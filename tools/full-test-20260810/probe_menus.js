const { loginAPI, apiGet } = require('./harness');
(async()=>{
  const t = (await loginAPI('sys_admin','Dms@123456')).json.data.accessToken;
  const r = await apiGet(t, '/api/menus');
  const menus = r.json.data;
  function walk(items, depth=0) {
    for (const m of items) {
      console.log(`${'  '.repeat(depth)}${m.menuKey} | ${m.label} | ${m.route} | perm=${m.permissionCode||''}`);
      if (m.children) walk(m.children, depth+1);
    }
  }
  walk(menus);
})();
