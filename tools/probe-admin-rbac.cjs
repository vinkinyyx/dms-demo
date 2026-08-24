// tools/probe-admin-rbac.cjs - 后台管理 + RBAC 角色/权限 API 探测
const http=require("http");
const args=process.argv.slice(2);
function arg(n,d){const h=args.find(a=>a.startsWith("--"+n+"="));return h?h.split("=").slice(1).join("="):d;}
function req(method,p,body,token,host){
  return new Promise(res=>{
    const data=body?Buffer.from(JSON.stringify(body)):null;
    const headers=data?{"Content-Type":"application/json","Content-Length":data.length}:{};
    if(token)headers["Authorization"]="Bearer "+token;
    const r=http.request({host:host||"43.128.145.141",port:80,path:p,method,headers,timeout:20000},r=>{
      let c=[];r.on("data",x=>c.push(x));r.on("end",()=>{const t=Buffer.concat(c).toString("utf8");let j=null;try{j=JSON.parse(t)}catch(e){}res({status:r.statusCode,txt:t,json:j});});
    });
    r.on("error",e=>res({status:0,txt:String(e)}));r.on("timeout",()=>{r.destroy();res({status:0,txt:"TIMEOUT"})});
    if(data)r.write(data);r.end();
  });
}
function ok(j){return j&&(j.code===0||j.code===200||j.success===true);}
function D(j){return j&&(j.data!==undefined?j.data:j.result);}
(async()=>{
  // 业务端 admin 登录
  const login=await req("POST","/api/auth/login",{tenantCode:"default",username:"admin",password:"Sh123456"});
  const token=login.json&&login.json.data&&(login.json.data.accessToken||login.json.data.token);
  if(!token){console.log("LOGIN FAIL",login.status,login.txt.slice(0,200));return;}
  console.log("[PASS] 业务端登录");

  // RBAC: 角色列表 + 权限树
  const checks=[
    ["角色列表","/api/roles?page=1&size=50"],
    ["权限树/菜单","/api/permissions/tree","/api/menus/tree"],
    ["用户列表","/api/users?page=1&size=5"],
    ["我的权限","/api/me/permissions"],
    ["我的信息","/api/auth/me"],
    ["审批待办","/api/approval/todo?page=1&size=5"],
    ["审批已办","/api/approval/done?page=1&size=5"],
    ["消息通知","/api/notifications?page=1&size=5"],
    ["导入导出任务","/api/async-tasks?page=1&size=5"],
  ];
  for(const c of checks){
    const paths=Array.isArray(c)?[c[1],c[2]]:[c[1]];
    let done=false;
    for(const pth of paths.filter(Boolean)){
      const r=await req("GET",pth,null,token);
      if(r.status===200&&ok(r.json)){console.log(`[PASS] ${c[0]} via ${pth}`);done=true;break;}
      if(!Array.isArray(c)){}
    }
    if(!done){
      const r=await req("GET",paths[0],null,token);
      console.log(`[FAIL] ${c[0]} status=${r.status} code=${r.json&&r.json.code} msg=${(r.json&&r.json.message)||r.txt.slice(0,80)}`);
    }
  }

  // 后台 admin 登录（/dms/admin/api）
  // 先探测后台登录端点
  const adminPaths=["/dms/admin/api/auth/login","/admin/api/auth/login","/api/admin/auth/login"];
  let adminToken=null, adminLoginPath=null;
  for(const ap of adminPaths){
    const r=await req("POST",ap,{username:"admin",password:"Sh123456",tenantCode:"default"});
    if(r.status===200&&ok(r.json)&&r.json.data){adminToken=r.json.data.accessToken||r.json.data.token;adminLoginPath=ap;console.log(`[PASS] 后台登录 via ${ap}`);break;}
  }
  if(!adminToken){
    // 试 admin 专用账号
    for(const ap of adminPaths){
      const r=await req("POST",ap,{username:"admin",password:"admin123"});
      if(r.status===200&&ok(r.json)&&r.json.data){adminToken=r.json.data.accessToken||r.json.data.token;adminLoginPath=ap;console.log(`[PASS] 后台登录(admin/admin123) via ${ap}`);break;}
    }
  }
  if(adminToken){
    const adminChecks=[
      ["租户管理","/api/admin/tenants?page=1&size=5"],
      ["平台用户","/api/admin/users?page=1&size=5"],
    ];
    for(const [name,pth] of adminChecks){
      const r=await req("GET",pth,null,adminToken);
      console.log(`[${r.status===200&&ok(r.json)?"PASS":"FAIL"}] 后台:${name} status=${r.status} ${(r.json&&r.json.message)||""}`);
    }
  } else {
    console.log("[WARN] 后台登录未成功（路径/账号待确认），将由浏览器审计覆盖");
  }

  // 邮件发送：先看邮件配置，不实际发（避免SMTP未配置产生错误日志）
  const mailLogs=await req("GET","/api/email-logs?page=1&size=5&status=SUCCESS",null,token);
  if(ok(mailLogs.json)){const d=D(mailLogs.json);console.log(`[PASS] 邮件日志(成功) total=${d&&d.total}`);}
  // 统计邮件失败
  const mailFail=await req("GET","/api/email-logs?page=1&size=1&status=FAIL",null,token);
  if(ok(mailFail.json)){const d=D(mailFail.json);console.log(`[INFO] 邮件失败日志 total=${d&&d.total}（>0 表示SMTP曾发送失败，需关注）`);}
})();
