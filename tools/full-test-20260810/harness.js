const fs = require('fs');
const path = require('path');

const BASE_FE = 'http://8.133.193.238:8083';
const BASE_BE = 'http://8.133.193.238:8082';
const OUT_DIR = path.join(__dirname, 'results');
if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });

const results = [];
const consoleErrors = [];

function record(scope, id, title, status, detail, evidence) {
  const r = {
    time: new Date().toISOString(),
    scope, id, title, status,
    detail: typeof detail === 'string' ? detail : JSON.stringify(detail),
    evidence
  };
  results.push(r);
  const tag = status === 'PASS' ? 'PASS' : status === 'FAIL' ? 'FAIL' : status === 'WARN' ? 'WARN' : 'INFO';
  const line = `[${tag}] ${scope} ${id} ${title} :: ${(r.detail || '').toString().slice(0, 300)}`;
  if (status === 'FAIL') console.error(line); else console.log(line);
}

function save() {
  const json = path.join(OUT_DIR, 'results.json');
  fs.writeFileSync(json, JSON.stringify(results, null, 2), 'utf8');
  const csv = [
    ['time','scope','id','title','status','detail'].join(','),
    ...results.map(r => [r.time,r.scope,r.id,r.title,r.status,r.detail].map(v => `"${String(v||'').replace(/"/g,'""').replace(/\n/g,' ')}"`).join(','))
  ].join('\n');
  fs.writeFileSync(path.join(OUT_DIR, 'results.csv'), csv, 'utf8');
  const fails = results.filter(r=>r.status==='FAIL');
  const warns = results.filter(r=>r.status==='WARN');
  const pass = results.filter(r=>r.status==='PASS');
  const summary = { total: results.length, pass: pass.length, fail: fails.length, warn: warns.length, scope: {} };
  for (const r of results) { summary.scope[r.scope] = summary.scope[r.scope] || {pass:0,fail:0,warn:0,info:0}; summary.scope[r.scope][r.status.toLowerCase()] = (summary.scope[r.scope][r.status.toLowerCase()]||0)+1; }
  fs.writeFileSync(path.join(OUT_DIR, 'summary.json'), JSON.stringify(summary,null,2), 'utf8');
  return summary;
}

async function loginAPI(account, password = 'Dms@123456', tenantCode = '') {
  const body = { username: account, password, tenantCode: tenantCode || undefined };
  const res = await fetch(`${BASE_FE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  let json = null;
  try { json = await res.json(); } catch(e) {}
  return { status: res.status, json, headers: res.headers };
}

async function apiGet(token, p) {
  const url = p.startsWith('http') ? p : `${BASE_FE}${p}`;
  const res = await fetch(url, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  let json; try { json = await res.json(); } catch(e) { json = null; }
  return { status: res.status, json, headers: res.headers };
}

async function apiPost(token, p, data) {
  const url = p.startsWith('http') ? p : `${BASE_FE}${p}`;
  const res = await fetch(url, { method:'POST', headers: { 'Content-Type':'application/json', ...(token?{Authorization:`Bearer ${token}`}:{}) }, body: JSON.stringify(data||{}) });
  let json; try { json = await res.json(); } catch(e) { json = null; }
  return { status: res.status, json };
}

async function apiPut(token, p, data) {
  const url = p.startsWith('http') ? p : `${BASE_FE}${p}`;
  const res = await fetch(url, { method:'PUT', headers: { 'Content-Type':'application/json', ...(token?{Authorization:`Bearer ${token}`}:{}) }, body: JSON.stringify(data||{}) });
  let json; try { json = await res.json(); } catch(e) { json = null; }
  return { status: res.status, json };
}

async function apiDelete(token, p) {
  const url = p.startsWith('http') ? p : `${BASE_FE}${p}`;
  const res = await fetch(url, { method:'DELETE', headers: token ? { Authorization: `Bearer ${token}` } : {} });
  let json; try { json = await res.json(); } catch(e) { json = null; }
  return { status: res.status, json };
}

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

module.exports = { BASE_FE, BASE_BE, OUT_DIR, record, save, loginAPI, apiGet, apiPost, apiPut, apiDelete, sleep };

