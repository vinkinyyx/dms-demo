const fs=require('fs');
const BASE='http://8.133.193.238:8082';
function parseCsv(text){const lines=text.trim().split(/\r?\n/);const headers=lines.shift().replace(/^"|"$/g,'').split('","');return lines.map(line=>{const vals=(line.match(/"([^"]*)"/g)||[]).map(s=>s.slice(1,-1));return Object.fromEntries(headers.map((h,i)=>[h,vals[i]||'']));});}
const rows=parseCsv(fs.readFileSync('tools/test-output/backend-endpoints.csv','utf8'));
async function req(method,path,token){const c=new AbortController();const t=setTimeout(()=>c.abort(),10000);try{const r=await fetch(BASE+path,{method,headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},signal:c.signal});const txt=await r.text();let j;try{j=JSON.parse(txt)}catch{j=txt}return{status:r.status,msg:j?.message||String(j).slice(0,120)}}catch(e){return{status:0,msg:String(e)}}finally{clearTimeout(t)}}
function badPath(p){
  if(!p.includes('{')) return null;
  let r=p;
  for(const m of p.matchAll(/\{(\w+)\}/g)){
    const name=m[1];
    let val='BADID';
    if(/pageKey|reportKey|type|businessType|tab|code|key|fileId/i.test(name)) continue;
    r=r.replace(m[0],val);
  }
  return r.includes('{')?null:r;
}
(async()=>{
 const login=await fetch(BASE+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'sys_admin',password:'Dms@123456'})});
 const token=(await login.json()).data.accessToken;
 const adminLogin=await fetch(BASE+'/api/admin/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'admin',password:'Sh123456'})});
 const adminToken=(await adminLogin.json()).data.accessToken;
 const gets=rows.filter(r=>r.Verb==='GET');
 const out=[];
 for(const r of gets){const p=badPath(r.Full); if(!p || p.includes('/api/admin/')) continue; const x=await req('GET',p,token); if(x.status>=500 || (x.status===200&&/内部错误/.test(x.msg))) out.push({method:'GET',path:p,status:x.status,msg:x.msg,file:r.File}); await new Promise(res=>setTimeout(res,10));}
 for(const r of gets.filter(x=>x.Full.startsWith('/api/admin/'))){const p=badPath(r.Full); if(!p) continue; const x=await req('GET',p,adminToken); if(x.status>=500) out.push({method:'GET',path:p,status:x.status,msg:x.msg,file:r.File}); await new Promise(res=>setTimeout(res,10));}
 fs.writeFileSync('tools/test-output/bad-path-probe.json',JSON.stringify(out,null,2));
 console.log('bad path failures',out.length); console.log(out.map(x=>`${x.status} ${x.path} [${x.file}] ${x.msg}`).join('\n'));
})();
