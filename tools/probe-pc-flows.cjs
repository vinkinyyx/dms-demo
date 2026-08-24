// tools/probe-pc-flows.cjs
// PC 业务流 API 层探测：R2 报表/业绩, R3 合同, R4 销退, R5 邮件, R6 报台/日志/促销/BOM
const http = require("http");
const fs = require("fs");
const path = require("path");

const args = process.argv.slice(2);
function arg(n,d){const h=args.find(a=>a.startsWith("--"+n+"="));return h?h.split("=").slice(1).join("="):d;}
const HOST = arg("host","43.128.145.141");
const PREFIX = "";
const USER = arg("user","admin");
const PASS = arg("pass","Sh123456");
const TENANT = arg("tenant","default");
const STAMP = new Date().toISOString().replace(/[-:T.Z]/g,"").slice(0,14);
const OUT = path.join(__dirname,"..","automation_test","v4-browser-results","pc-flows-"+STAMP);
fs.mkdirSync(OUT,{recursive:true});

const results = [];
function rec(name,status,detail){ results.push({name,status,detail:String(detail||"").slice(0,600)}); console.log(`[${status}] ${name} | ${String(detail||"").slice(0,180)}`); }

function req(method, p, body, token){
  return new Promise((resolve)=>{
    const data = body ? Buffer.from(JSON.stringify(body)) : null;
    const headers = data ? {"Content-Type":"application/json","Content-Length":data.length} : {};
    if(token) headers["Authorization"] = "Bearer "+token;
    const r = http.request({host:HOST,port:80,path:PREFIX+p,method,headers,timeout:20000},(res)=>{
      let chunks=[]; res.on("data",c=>chunks.push(c)); res.on("end",()=>{
        const txt=Buffer.concat(chunks).toString("utf8");
        let json=null; try{json=JSON.parse(txt);}catch(e){}
        resolve({status:res.statusCode, txt, json, headers:res.headers});
      });
    });
    r.on("error",e=>resolve({status:0,txt:String(e),json:null}));
    r.on("timeout",()=>{r.destroy();resolve({status:0,txt:"TIMEOUT",json:null});});
    if(data) r.write(data);
    r.end();
  });
}

function ok(j){ return j && (j.code===0 || j.code===200 || j.success===true); }
function dataOf(j){ return j && (j.data!==undefined ? j.data : j.result); }

(async()=>{
  // 登录
  const login = await req("POST","/api/auth/login",{tenantCode:TENANT,username:USER,password:PASS});
  let token=null;
  if(login.json && login.json.data){ token = login.json.data.accessToken || login.json.data.token; }
  if(!token){ rec("登录","FAIL",`status=${login.status} body=${login.txt.slice(0,200)}`); return finish(); }
  rec("登录","PASS","token len="+token.length);

  // R2 报表中心 7 类
  const reportEndpoints = [
    ["销售排行","/api/reports/sales-ranking"],
    ["产品TOP10","/api/reports/product-top10"],
    ["库存周转","/api/reports/inventory-turnover"],
    ["手术统计","/api/reports/surgery-stats"],
    ["应收款项","/api/reports/receivables"],
    ["订单追溯","/api/reports/order-trace"],
    ["报表概览","/api/reports/overview"],
    ["库存账龄","/api/reports/inventory-aging"],
    ["审批统计","/api/reports/order-approval-stats"],
  ];
  for(const [label,p] of reportEndpoints){
    const r = await req("GET",p,null,token);
    if(r.status===200 && ok(r.json)){
      const d = dataOf(r.json);
      const n = Array.isArray(d)? d.length : (d && d.records? d.records.length : (d && typeof d==="object"? Object.keys(d).length : 0));
      rec("报表:"+label,"PASS",`rows/keys=${n}`);
    } else rec("报表:"+label,"FAIL",`status=${r.status} code=${r.json&&r.json.code} msg=${(r.json&&r.json.message)||r.txt.slice(0,120)}`);
  }

  // 穿透报表（产品销售明细 / 经销商订单 / 医院手术）- 需要有效 id，先从 product-top10 取
  const top = await req("GET","/api/reports/product-top10",null,token);
  let pid=null, dealerId=null;
  if(ok(top.json)){ const arr=dataOf(top.json)||[]; const row=arr[0]; if(row){ pid=row.productId||row.product_id; dealerId=row.dealerId||row.dealer_id; } }
  if(pid){ const d=await req("GET",`/api/reports/product-sales-detail?productId=${pid}`,null,token); rec("穿透:产品销售明细",ok(d.json)?"PASS":"FAIL",`pid=${pid} status=${d.status}`); }
  else rec("穿透:产品销售明细","WARN","top10 无产品数据，跳过");

  // R2 岗位业绩：销售岗位树 + 我的范围 + 经销商业绩(achievement)
  const tree = await req("GET","/api/sales-positions/tree",null,token);
  if(ok(tree.json)){ const t=dataOf(tree.json); rec("销售岗位树","PASS","nodes="+(Array.isArray(t)?t.length:JSON.stringify(t).length)); }
  else rec("销售岗位树","FAIL",`status=${tree.status} ${(tree.json&&tree.json.message)||""}`);

  const myScope = await req("GET","/api/sales-positions/my-scope",null,token);
  rec("我的业绩范围(my-scope)",ok(myScope.json)?"PASS":"FAIL",`status=${myScope.status} ${(myScope.json&&myScope.json.message)||JSON.stringify(myScope.json||"").slice(0,120)}`);

  // 岗位绑定用户/经销商（取第一个岗位）
  let firstPosId=null;
  if(ok(tree.json)){ const t=dataOf(tree.json); if(Array.isArray(t)&&t.length){ firstPosId=t[0].id; } else if(t&&t.id){ firstPosId=t.id; } else if(Array.isArray(t)&&t[0]&&t[0].children){ firstPosId=t[0].id; } }
  if(firstPosId){
    const pu = await req("GET",`/api/sales-positions/${firstPosId}/users`,null,token);
    rec("岗位绑定销售账号",ok(pu.json)?"PASS":"FAIL",`posId=${firstPosId} users=${Array.isArray(dataOf(pu.json))?dataOf(pu.json).length:"?"}`);
    const pd = await req("GET",`/api/sales-positions/${firstPosId}/dealers`,null,token);
    rec("岗位绑定经销商",ok(pd.json)?"PASS":"FAIL",`posId=${firstPosId} dealers=${Array.isArray(dataOf(pd.json))?dataOf(pd.json).length:"?"}`);
  } else rec("岗位绑定","WARN","无岗位数据");

  // 经销商画像业绩 tab：取经销商列表第一个
  const dealers = await req("GET","/api/dealers?page=1&size=1",null,token);
  let did=null;
  if(ok(dealers.json)){ const d=dataOf(dealers.json); const rec0 = (d&&(d.records||d.list||(Array.isArray(d)?d:null))); if(rec0&&rec0.length) did=rec0[0].id; }
  if(did){
    const ach = await req("GET",`/api/dealer-profile/${did}/achievement`,null,token);
    rec("经销商画像业绩",ok(ach.json)?"PASS":"FAIL",`dealerId=${did} status=${ach.status} keys=${ach.json&&ach.json.data?Object.keys(ach.json.data).length:0}`);
  } else rec("经销商画像业绩","WARN","无经销商数据");

  // R3 合同：列表 + 详情
  const contracts = await req("GET","/api/contracts?page=1&size=5",null,token);
  if(ok(contracts.json)){ const d=dataOf(contracts.json); const recs=d&&(d.records||d.list)||[]; rec("合同列表","PASS","total="+(d&&d.total)+" returned="+recs.length);
    if(recs.length){ const cid=recs[0].id; const det=await req("GET",`/api/contracts/${cid}`,null,token); rec("合同详情",ok(det.json)?"PASS":"FAIL",`id=${cid} status=${det.status} hasSourceFile=${!!(det.json&&det.json.data&&det.json.data.sourceFileId)}`); }
    else rec("合同详情","WARN","无合同数据");
  } else rec("合同列表","FAIL",`status=${contracts.status} ${(contracts.json&&contracts.json.message)||""}`);

  // 合同模板列表
  const ct = await req("GET","/api/contract-templates?page=1&size=5",null,token);
  rec("合同模板列表",ok(ct.json)?"PASS":"FAIL",`status=${ct.status}`);

  // R4 销退：列表 + 可选发货单查询(不实际创建/提交)
  const sr = await req("GET","/api/sales-returns?page=1&size=5",null,token);
  if(ok(sr.json)){ const d=dataOf(sr.json); const recs=d&&(d.records||d.list)||[]; rec("销退列表","PASS","total="+(d&&d.total)+" returned="+recs.length); }
  else rec("销退列表","FAIL",`status=${sr.status} ${(sr.json&&sr.json.message)||""}`);
  // 可选发货单接口（创建销退时用）
  const shipped = await req("GET","/api/sales-returns/shipped-outs?page=1&size=5",null,token);
  rec("销退可选发货单",(shipped.status===200)?"PASS":"FAIL",`status=${shipped.status} ${(shipped.json&&shipped.json.message)||""}`);

  // R5 邮件：日志列表（不实际发测试邮件，避免依赖 SMTP；只验证列表可查 + 记录状态）
  const mail = await req("GET","/api/email-logs?page=1&size=5",null,token);
  if(ok(mail.json)){ const d=dataOf(mail.json); const recs=d&&(d.records||d.list)||[]; rec("邮件日志列表","PASS","total="+(d&&d.total)+" returned="+recs.length);
    // 统计成功/失败
    const succ=recs.filter(x=>x.success||x.status==="SUCCESS").length; const fail=recs.length-succ;
    rec("邮件日志状态分布","INFO",`样本=${recs.length} success=${succ} fail=${fail}`);
  } else rec("邮件日志列表","FAIL",`status=${mail.status} ${(mail.json&&mail.json.message)||""}`);

  // R6 手术报台列表 + 导入模板下载
  const surg = await req("GET","/api/surgery-reports?page=1&size=5",null,token);
  if(ok(surg.json)){ const d=dataOf(surg.json); const recs=d&&(d.records||d.list)||[]; rec("手术报台列表","PASS","total="+(d&&d.total)+" returned="+recs.length); }
  else rec("手术报台列表","FAIL",`status=${surg.status} ${(surg.json&&surg.json.message)||""}`);
  const surgTpl = await req("GET","/api/surgery-reports/actions/export/template",null,token);
  rec("手术报台导入模板",(surgTpl.status===200 && surgTpl.txt.length>1000)?"PASS":"FAIL",`status=${surgTpl.status} bytes=${surgTpl.txt.length} contentType=${surgTpl.headers["content-type"]||""}`);

  // 促销规则 + BOM 组合品
  const promo = await req("GET","/api/promotions?page=1&size=5",null,token);
  rec("促销规则列表",(promo.status===200)?"PASS":"FAIL",`status=${promo.status} ${(promo.json&&promo.json.message)||""}`);
  const bundles = await req("GET","/api/product-bundles?page=1&size=5",null,token);
  rec("产品组合/BOM",(bundles.status===200)?"PASS":"FAIL",`status=${bundles.status} ${(bundles.json&&bundles.json.message)||""}`);

  // 日志中心：操作日志 + API 调用日志
  const oplog = await req("GET","/api/operation-log/list/PRODUCT/1",null,token);
  rec("操作日志",(oplog.status===200)?"PASS":"FAIL",`status=${oplog.status} ${(oplog.json&&oplog.json.message)||""}`);
  const apilog = await req("GET","/api/api-call-logs?page=1&size=5",null,token);
  rec("API调用日志",(apilog.status===200)?"PASS":"FAIL",`status=${apilog.status} ${(apilog.json&&apilog.json.message)||""}`);

  // 审批流模板
  const apv = await req("GET","/api/approval/templates?page=1&size=30",null,token);
  if(ok(apv.json)){ const d=dataOf(apv.json); const recs=d&&(d.records||d.list)||[]; const enabled=recs.filter(x=>x.status==="ENABLED"||x.status==="enabled"||x.enabled).length;
    const hasSales = recs.some(x=>(x.bizType==="SALES_ORDER"||x.bizType==="SALES ORDER"||(x.name&&x.name.includes("销售订单"))) && (x.status==="ENABLED"||x.enabled));
    rec("审批模板","PASS",`总数=${recs.length} enabled=${enabled} SALES_ORDER_ENABLED=${hasSales}`);
    if(!hasSales) rec("销售订单审批模板","WARN","SALES_ORDER 无 ENABLED 模板，提交会自动通过（演示无待审批）");
  } else rec("审批模板","FAIL",`status=${apv.status}`);

  finish();

  function finish(){
    const summary={pass:results.filter(r=>r.status==="PASS").length,
      warn:results.filter(r=>r.status==="WARN"||r.status==="INFO").length,
      fail:results.filter(r=>r.status==="FAIL").length};
    const report={summary,results};
    fs.writeFileSync(path.join(OUT,"report.json"),JSON.stringify(report,null,2),"utf8");
    console.log("\n=== SUMMARY ===");
    console.log(JSON.stringify(summary));
    console.log("report: "+OUT);
  }
})();



