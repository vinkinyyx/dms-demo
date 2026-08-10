const fs=require('fs');
const BASE='http://8.133.193.238:8082';
function parseCsv(text){const lines=text.trim().split(/\r?\n/);const headers=lines.shift().replace(/^"|"$/g,'').split('","');return lines.map(line=>{const vals=(line.match(/"([^"]*)"/g)||[]).map(s=>s.slice(1,-1));return Object.fromEntries(headers.map((h,i)=>[h,vals[i]||'']));});}
const rows=parseCsv(fs.readFileSync('tools/test-output/backend-endpoints.csv','utf8'));
async function req(method,path,token,body){const c=new AbortController();const t=setTimeout(()=>c.abort(),10000);try{const r=await fetch(BASE+path,{method,headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:c.signal});const txt=await r.text();let j;try{j=JSON.parse(txt)}catch{j=txt}return{status:r.status,msg:j?.message||String(j).slice(0,120)}}catch(e){return{status:0,msg:String(e)}}finally{clearTimeout(t)}}
(async()=>{
 const token=(await (await fetch(BASE+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'sys_admin',password:'Dms@123456'})})).json()).data.accessToken;
 const writes=rows.filter(r=>['POST','PUT','PATCH'].includes(r.Verb) && !r.Full.includes('{') && !r.Full.includes('/login') && !r.Full.includes('/logout') && !r.Full.includes('/refresh') && !r.Full.includes('/reset-password') && !r.Full.includes('/change-password') && !r.Full.includes('/unlock') && !r.Full.includes('/approve') && !r.Full.includes('/reject') && !r.Full.includes('/submit') && !r.Full.includes('/cancel') && !r.Full.includes('/execute') && !r.Full.includes('/disable') && !r.Full.includes('/publish') && !r.Full.includes('/withdraw') && !r.Full.includes('/terminate') && !r.Full.includes('/generate'));
 const out=[];
 for(const r of writes){const x=await req(r.Verb,r.Full,token,{}); if(x.status>=500) out.push({verb:r.Verb,path:r.Full,status:x.status,msg:x.msg,file:r.File}); await new Promise(res=>setTimeout(res,15));}
 fs.writeFileSync('tools/test-output/empty-body-probe.json',JSON.stringify(out,null,2));
 console.log('empty body 500 count',out.length); console.log(out.map(x=>`${x.verb} ${x.path} [${x.file}] ${x.status} ${x.msg}`).join('\n'));
})();
