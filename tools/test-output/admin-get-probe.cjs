const fs=require('fs');
const BASE='http://8.133.193.238:8082';
async function j(method,path,token,body){const c=new AbortController();const t=setTimeout(()=>c.abort(),12000);try{const r=await fetch(BASE+path,{method,headers:{Authorization:`Bearer ${token||''}`,'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined,signal:c.signal});const txt=await r.text();let data;try{data=JSON.parse(txt)}catch{data=txt}return{status:r.status,data}}catch(e){return{status:0,data:String(e)}}finally{clearTimeout(t)}}
(async()=>{
const admin=(await j('POST','/api/admin/auth/login',null,{username:'admin',password:'Sh123456'})).data.data.accessToken;
const biz=(await j('POST','/api/auth/login',null,{username:'sys_admin',password:'Dms@123456'})).data.data.accessToken;
const paths=['/api/admin/auth/me','/api/admin/role-templates','/api/admin/role-templates/resources','/api/admin/menus','/api/admin/page-configs','/api/admin/buttons','/api/admin/filter-configs','/api/admin/dicts/types','/api/admin/dicts/types/GENDER/items','/api/admin/tenant-admins','/api/admin/tenants/stats','/api/admin/tenants/manufacturers?page=1&size=5','/api/admin/tenants/dealers?page=1&size=5','/api/admin/logs/api?page=1&size=5','/api/admin/logs/platform-audits?page=1&size=5','/api/admin/tenants/1','/api/admin/tenants/1/bindings'];
const out=[];
for(const path of paths){const [a,b,n]=await Promise.all([j('GET',path,admin),j('GET',path,biz),j('GET',path,'')]); out.push({path,admin:a.status,biz:b.status,anon:n.status,adminMsg:a.data?.message||String(a.data).slice(0,80),bizMsg:b.data?.message||String(b.data).slice(0,80),anonMsg:n.data?.message||String(n.data).slice(0,80)});}
fs.writeFileSync('tools/test-output/admin-get-probe.json',JSON.stringify(out,null,2));
console.log(out.map(x=>`${x.adminStatus||x.admin}/${x.bizStatus||x.biz}/${x.anonStatus||x.anon} ${x.path} | admin=${x.adminMsg} biz=${x.bizMsg} anon=${x.anonMsg}`).join('\n'));
})();

