const { loginAPI, apiGet } = require('./harness');
(async()=>{
  const r = await loginAPI('sys_admin','Dms@123456');
  const token = r.json.data.accessToken;
  console.log('token len', token.length);
  // try different auth header styles
  for (const hdr of [
    ['Authorization', `Bearer ${token}`],
    ['X-Access-Token', token],
    ['token', token],
    ['x-token', token],
  ]) {
    const res = await fetch('http://8.133.193.238:8083/api/auth/me', { headers: { [hdr[0]]: hdr[1] } });
    console.log(hdr[0], res.status, (await res.text()).slice(0,200));
  }
})();
