const fs = require('fs');
const errs = JSON.parse(fs.readFileSync('results/all-browser-errors.json','utf8'));
// group by message
const groups = {};
for (const e of errs) {
  const key = e.msg.slice(0,150);
  groups[key] = (groups[key]||0)+1;
}
const sorted = Object.entries(groups).sort((a,b)=>b[1]-a[1]);
for (const [msg,cnt] of sorted.slice(0,25)) console.log(`${cnt}\t${msg.slice(0,200)}`);
console.log('\n---Unique messages:',sorted.length,'Total:',errs.length);
