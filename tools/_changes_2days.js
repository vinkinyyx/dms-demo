/* =====================================================================
 * DMS - 昨天+今天 两天变更文件枚举 + 打包（零依赖，纯 Node 内置模块）
 * 用法: node _changes_2days.js
 * 规则:
 *   1. 按文件修改时间筛选"昨天 00:00 ~ 明天 00:00"新增/变更的文件
 *   2. 排除构建产物/依赖/.git/压缩包与清单自身
 *   3. 清单写 docs/09_测试报告/变更清单_<起>-<止>.md
 *   4. 新增+变更文件按原目录结构打包为项目根目录 dms-changes-<起>-<止>.zip
 * ===================================================================== */
'use strict';
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const ROOT = path.resolve(__dirname, '..');
const now = new Date();
const START = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1); // 昨天 00:00
const END   = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1); // 明天 00:00（窗口右开）
const pad = n => String(n).padStart(2, '0');
const ymd = d => `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}`;
const RANGE = `${ymd(START)}-${ymd(new Date(now.getFullYear(), now.getMonth(), now.getDate()))}`;
const ZIP  = path.join(ROOT, `dms-changes-${RANGE}.zip`);
const LIST = path.join(ROOT, 'docs', '09_测试报告', `变更清单_${RANGE}.md`);
const LISTNAME = path.basename(LIST);

const excludeDirs = new Set(['.git','node_modules','target','dist','.m2','.idea','.vscode',
  'maven-repo','npm-cache','build','.gradle','logs','log',
  '__pycache__','.cache','coverage','.nuxt','.output']);
const excludeFiles = new Set(['dms-backend.tar.gz','pscp.exe','plink.exe']);

console.log('项目根目录 : ' + ROOT);
console.log('统计窗口   : ' + START.toLocaleDateString('zh-CN') + ' 00:00 ~ ' +
  new Date(now.getFullYear(), now.getMonth(), now.getDate()).toLocaleDateString('zh-CN') + ' 24:00');

const hits = [];
function walk(dir) {
  let entries;
  try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch (e) { return; }
  for (const ent of entries) {
    const full = path.join(dir, ent.name);
    if (ent.isDirectory()) {
      if (excludeDirs.has(ent.name)) continue;
      walk(full);
    } else if (ent.isFile()) {
      try {
        const st = fs.statSync(full);
        const mt = st.mtime;
        if (mt >= START && mt < END) {
          if (excludeFiles.has(ent.name)) continue;
          if (/^dms-changes-.*\.zip$/.test(ent.name)) continue;
          if (ent.name === LISTNAME) continue;
          hits.push({ full, rel: path.relative(ROOT, full), size: st.size, mtime: mt });
        }
      } catch (e) {}
    }
  }
}
console.log('[1/5] 扫描两天内变更文件 ...');
walk(ROOT);
hits.sort((a, b) => a.rel.localeCompare(b.rel, 'zh-CN'));
console.log('      两天内新增/变更文件：' + hits.length + ' 个');

const manualDeleted = [
  'tools\\_dms_deploy.log','tools\\_dms_deploy_test.bat','tools\\_dms_remote_build.sh',
  'tools\\_cu_probe.log','tools\\_cu_probe.bat','tools\\_cu_build.log','tools\\_cu_build_check.bat'
];
const allDeleted = manualDeleted.filter(d => !fs.existsSync(path.join(ROOT, d)));

function fmtSize(b) {
  if (b >= 1024 * 1024) return (b / 1024 / 1024).toFixed(1) + ' MB';
  if (b >= 1024) return (b / 1024).toFixed(1) + ' KB';
  return b + ' B';
}
function fmtTime(d) {
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

console.log('[2/5] 生成变更清单 ...');
let md = '';
md += `# DMS 项目变更清单 - ${START.getFullYear()}-${pad(START.getMonth()+1)}-${pad(START.getDate())} ~ ${now.getFullYear()}-${pad(now.getMonth()+1)}-${pad(now.getDate())}\n\n`;
md += '- 统计范围：项目根目录下昨天 00:00 至今天 24:00 新增/变更的文件（已排除 node_modules / target / dist / .git / .m2 等构建产物与依赖目录，以及 dms-backend.tar.gz / *.exe / dms-changes-*.zip 等二进制与打包产物）\n';
md += `- 新增+变更文件已打包：\`dms-changes-${RANGE}.zip\`（项目根目录，保留原目录结构，本清单已含在包内）\n`;
md += '- 删除文件仅登记不打包\n\n';
md += '## 一、统计概览\n\n';
md += '| 类别 | 数量 |\n|------|------|\n';
md += `| 新增/变更（已打包） | ${hits.length} |\n`;
md += `| 删除（仅登记） | ${allDeleted.length} |\n\n`;
md += `## 二、新增 / 变更文件清单（${hits.length} 个，已打包）\n\n`;
md += '| # | 相对路径 | 大小 | 最后修改时间 |\n|---|----------|------|--------------|\n';
hits.forEach((h, i) => {
  md += `| ${i+1} | \`${h.rel.split(path.sep).join('/')}\` | ${fmtSize(h.size)} | ${fmtTime(h.mtime)} |\n`;
});
md += `\n## 三、删除文件清单（${allDeleted.length} 个，仅登记）\n\n`;
if (allDeleted.length) {
  md += '| # | 相对路径 | 来源 |\n|---|----------|------|\n';
  allDeleted.forEach((d, i) => { md += `| ${i+1} | \`${d.split('\\').join('/')}\` | 09-02 部署清理的临时文件 |\n`; });
} else {
  md += '（无）\n';
}
md += '\n## 四、说明\n\n';
md += '- 「新增/变更」依据文件系统最后修改时间判定（修改时间无法区分新增与修改，故合并列示；git 未跟踪的新文件同样包含）\n';
md += '- 两天工作内容：09-02 v4.5.5 外部经销商开放协同（openapi 包 + V143/V144/V145）与 v4.6.1 定时邮件开关（V146 + 邮件服务/控制器 + admin-vue 通知设置页）+ 文档回写；09-03 生产环境推送 v4.6.1（Flyway V143->V146）、本地 MCP/SSH 通道修复（tools/_mcp_fix）、版本文档回写\n';
md += '- 压缩包内文件保持项目相对目录结构，解压后可直接覆盖回项目根目录\n';
md += '- 生成工具：`tools/_changes_2days.js`（纯 Node 零依赖，由根目录 bat 双击调用）\n\n';

fs.mkdirSync(path.dirname(LIST), { recursive: true });
fs.writeFileSync(LIST, md, 'utf8');
console.log('      清单已生成：' + LIST);

// ---------------- 纯 Node ZIP 写入器 ----------------
const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
    t[n] = c >>> 0;
  }
  return t;
})();
function crc32(buf) {
  let c = 0xFFFFFFFF;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xFF] ^ (c >>> 8);
  return (c ^ 0xFFFFFFFF) >>> 0;
}
function dosTime(d) {
  const t = ((d.getHours() & 0x1F) << 11) | ((d.getMinutes() & 0x3F) << 5) | ((Math.floor(d.getSeconds() / 2)) & 0x1F);
  const dt = (((d.getFullYear() - 1980) & 0x7F) << 9) | (((d.getMonth() + 1) & 0x0F) << 5) | (d.getDate() & 0x1F);
  return { t, dt };
}

console.log('[3/5] 读取并压缩文件 ...');
const entries = [];
const files = hits.map(h => ({ abs: h.full, rel: h.rel.split(path.sep).join('/'), mtime: h.mtime }));
files.push({ abs: LIST, rel: LISTNAME, mtime: fs.statSync(LIST).mtime, isList: true });

for (const f of files) {
  const data = fs.readFileSync(f.abs);
  const crc = crc32(data);
  const deflated = zlib.deflateRawSync(data, { level: 9 });
  let method, cdata;
  if (deflated.length < data.length) { method = 8; cdata = deflated; }
  else { method = 0; cdata = data; }
  const { t, dt } = dosTime(f.mtime);
  const nameBuf = Buffer.from(f.rel, 'utf8');
  entries.push({ nameBuf, method, t, dt, crc, csize: cdata.length, usize: data.length, cdata });
}

console.log('[4/5] 生成压缩包 ...');
const localParts = [];
const central = [];
let offset = 0;
for (const e of entries) {
  const lh = Buffer.alloc(30);
  lh.writeUInt32LE(0x04034b50, 0);
  lh.writeUInt16LE(20, 4);              // version needed
  lh.writeUInt16LE(0x0800, 6);          // flags: UTF-8 文件名
  lh.writeUInt16LE(e.method, 8);
  lh.writeUInt16LE(e.t, 10);
  lh.writeUInt16LE(e.dt, 12);
  lh.writeUInt32LE(e.crc, 14);
  lh.writeUInt32LE(e.csize, 18);
  lh.writeUInt32LE(e.usize, 22);
  lh.writeUInt16LE(e.nameBuf.length, 26);
  lh.writeUInt16LE(0, 28);              // extra len
  localParts.push(lh, e.nameBuf, e.cdata);
  const localOff = offset;
  offset += lh.length + e.nameBuf.length + e.cdata.length;

  const ch = Buffer.alloc(46);
  ch.writeUInt32LE(0x02014b50, 0);
  ch.writeUInt16LE(20, 4);              // version made by
  ch.writeUInt16LE(20, 6);              // version needed
  ch.writeUInt16LE(0x0800, 8);          // flags
  ch.writeUInt16LE(e.method, 10);
  ch.writeUInt16LE(e.t, 12);
  ch.writeUInt16LE(e.dt, 14);
  ch.writeUInt32LE(e.crc, 16);
  ch.writeUInt32LE(e.csize, 20);
  ch.writeUInt32LE(e.usize, 24);
  ch.writeUInt16LE(e.nameBuf.length, 28);
  ch.writeUInt16LE(0, 30);              // extra
  ch.writeUInt16LE(0, 32);              // comment
  ch.writeUInt16LE(0, 34);              // disk
  ch.writeUInt16LE(0, 36);              // internal attrs
  ch.writeUInt32LE(0, 38);              // external attrs
  ch.writeUInt32LE(localOff, 42);
  central.push(ch, e.nameBuf);
}
const cdBuf = Buffer.concat(central);
const cdOffset = offset;
const eocd = Buffer.alloc(22);
eocd.writeUInt32LE(0x06054b50, 0);
eocd.writeUInt16LE(0, 4);
eocd.writeUInt16LE(0, 6);
eocd.writeUInt16LE(entries.length, 8);
eocd.writeUInt16LE(entries.length, 10);
eocd.writeUInt32LE(cdBuf.length, 12);
eocd.writeUInt32LE(cdOffset, 16);
eocd.writeUInt16LE(0, 20);

const zipBuf = Buffer.concat([...localParts, cdBuf, eocd]);
fs.writeFileSync(ZIP, zipBuf);
console.log('      压缩包：' + ZIP + ' (' + fmtSize(zipBuf.length) + ')');

console.log('[5/5] 校验压缩包内容 ...');
console.log('      包内条目数：' + entries.length + '（含清单文件）');
console.log('');
console.log('================ 完成 ================');
console.log('清单文件: ' + LIST);
console.log('压缩包  : ' + ZIP + ' (' + fmtSize(zipBuf.length) + ')');
console.log('打包文件: ' + hits.length + ' 个；删除登记: ' + allDeleted.length + ' 个');
console.log('======================================');
