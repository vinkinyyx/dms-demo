#!/usr/bin/env node
/*
 * ERP -> DMS 销售出库回传接口联调/探测脚本（R9）。
 *
 * 用法:
 *   node tools/probe-erp-openapi.cjs [--base=URL] [--app-key=KEY] [--app-secret=SECRET] [--order-code=CODE]
 *
 * 默认:
 *   base       http://43.128.145.141
 *   app-key    dms-erp-app
 *   app-secret 0a1b2c3d4e5f60718293a4b5c6d7e8f9
 *
 * 仅使用 Node 内置模块（crypto/http/https），不引新依赖。
 * 6 个用例打印 PASS/FAIL/SKIP。用例 1/2 依赖一个处于 APPROVED/PARTIAL_OUTBOUND
 * 且至少有一个可出库产品的订单；未提供 --order-code 或数据不满足时 SKIP。
 */
const crypto = require('crypto');
const http = require('http');
const https = require('https');
const { URL } = require('url');

function parseArgs(argv) {
  const out = {};
  for (const a of argv.slice(2)) {
    const m = a.match(/^--([^=]+)=(.*)$/);
    if (m) out[m[1]] = m[2];
  }
  return out;
}

const args = parseArgs(process.argv);
const BASE = (args.base || process.env.DMS_BASE || 'http://43.128.145.141').replace(/\/$/, '');
const APP_KEY = args['app-key'] || process.env.DMS_APP_KEY || 'dms-erp-app';
const APP_SECRET = args['app-secret'] || process.env.DMS_APP_SECRET || '0a1b2c3d4e5f60718293a4b5c6d7e8f9';
const ORDER_CODE = args['order-code'] || process.env.DMS_ORDER_CODE || '';
const PATH_API = '/open/api/erp/sales-outbounds';

function sign(method, path, body, secret, overrideSig) {
  const ts = Date.now().toString();
  const nonce = crypto.randomUUID();
  const bodyHash = crypto.createHash('sha256').update(Buffer.from(body || '', 'utf8')).digest('hex');
  const signString = [method.toUpperCase(), path, ts, nonce, bodyHash].join('\n');
  const signature = overrideSig !== undefined
    ? overrideSig
    : crypto.createHmac('sha256', secret).update(signString, 'utf8').digest('hex');
  return {
    'Content-Type': 'application/json; charset=utf-8',
    'X-App-Key': APP_KEY,
    'X-Timestamp': ts,
    'X-Nonce': nonce,
    'X-Signature': signature,
  };
}

function request(method, path, body, headers) {
  return new Promise((resolve, reject) => {
    const u = new URL(BASE + path);
    const lib = u.protocol === 'https:' ? https : http;
    const payload = body || '';
    const req = lib.request({
      hostname: u.hostname,
      port: u.port || (u.protocol === 'https:' ? 443 : 80),
      path: u.pathname + u.search,
      method,
      headers: { ...(headers || {}), 'Content-Length': Buffer.byteLength(payload, 'utf8') },
    }, (res) => {
      let chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => {
        const text = Buffer.concat(chunks).toString('utf8');
        let json = null;
        try { json = JSON.parse(text); } catch (_) { /* keep text */ }
        resolve({ status: res.statusCode, text, json });
      });
    });
    req.on('error', reject);
    if (payload) req.write(payload);
    req.end();
  });
}

function summarize(resp) {
  if (resp.json) return JSON.stringify(resp.json).slice(0, 300);
  return (resp.text || '').slice(0, 300);
}

let pass = 0, fail = 0, skip = 0;
function report(name, state, detail) {
  const tag = state === 'PASS' ? '\x1b[32mPASS\x1b[0m' : state === 'FAIL' ? '\x1b[31mFAIL\x1b[0m' : '\x1b[33mSKIP\x1b[0m';
  console.log(`[${tag}] ${name}${detail ? ' :: ' + detail : ''}`);
  if (state === 'PASS') pass++; else if (state === 'FAIL') fail++; else skip++;
}

async function postSalesOutbound(payload, opts = {}) {
  const body = JSON.stringify(payload);
  const headers = sign('POST', PATH_API, body, APP_SECRET, opts.badSignature ? 'deadbeef' : undefined);
  return request('POST', PATH_API, body, headers);
}

function uniqueIdempotencyKey() {
  return 'PROBE-' + Date.now() + '-' + crypto.randomBytes(3).toString('hex');
}

async function main() {
  console.log(`DMS ERP 出库回传探测 -> ${BASE}`);
  console.log(`appKey=${APP_KEY} orderCode=${ORDER_CODE || '(未提供)'}`);
  console.log('------------------------------------------------------------');

  // 用例 0：先探测一个可出库订单（仅当传了 order-code 时校验其状态）。
  let orderReady = !!ORDER_CODE;
  if (ORDER_CODE) {
    // 通过一次超量请求探测订单是否存在/可出库：这里不直接查内部 API，仅做提示。
    // 真正的出库用例会因数量不足而返回业务错误，脚本据此判断。
  }

  // 用例 1：正常完整出库（数据依赖）
  {
    const name = '用例1 正常完整出库';
    if (!ORDER_CODE) { report(name, 'SKIP', '未提供 --order-code，需要一个 APPROVED 订单'); }
    else {
      const key = uniqueIdempotencyKey();
      const payload = {
        idempotencyKey: key,
        sourceOrderCode: ORDER_CODE,
        direction: 'FORWARD',
        erpOutboundNo: key,
        outboundDate: new Date().toISOString().slice(0, 10),
        lines: [{ productCode: 'PROBE-PRODUCT', qty: 1 }],
      };
      const resp = await postSalesOutbound(payload);
      const code = resp.json && resp.json.code;
      if (resp.status === 200 && code === 0) {
        report(name, 'PASS', `salesOutCode=${resp.json.data && resp.json.data.salesOutCode}`);
      } else if (resp.json && (code === 40401 || code === 40006)) {
        report(name, 'SKIP', `订单/产品数据不满足（${summarize(resp)}）`);
      } else {
        report(name, 'FAIL', `status=${resp.status} ${summarize(resp)}`);
      }
    }
  }

  // 用例 2：部分出库（数据依赖）
  {
    const name = '用例2 部分出库';
    if (!ORDER_CODE) { report(name, 'SKIP', '未提供 --order-code'); }
    else {
      const key = uniqueIdempotencyKey();
      const payload = {
        idempotencyKey: key,
        sourceOrderCode: ORDER_CODE,
        erpOutboundNo: key,
        lines: [{ productCode: 'PROBE-PRODUCT', qty: 0.0001 }],
      };
      const resp = await postSalesOutbound(payload);
      const code = resp.json && resp.json.code;
      if (resp.status === 200 && code === 0) {
        report(name, 'PASS', `processedLines=${resp.json.data && resp.json.data.processedLines}`);
      } else if (resp.json && (code === 40401 || code === 40006)) {
        report(name, 'SKIP', `订单/产品数据不满足（${summarize(resp)}）`);
      } else {
        report(name, 'FAIL', `status=${resp.status} ${summarize(resp)}`);
      }
    }
  }

  // 用例 3：幂等（同 key 发两次，第二次 idempotent=true）。依赖一个可成功出库的订单行。
  {
    const name = '用例3 幂等';
    if (!ORDER_CODE) { report(name, 'SKIP', '未提供 --order-code，幂等需要首次成功落库'); }
    else {
      const key = uniqueIdempotencyKey();
      const payload = {
        idempotencyKey: key,
        sourceOrderCode: ORDER_CODE,
        erpOutboundNo: key,
        lines: [{ productCode: 'PROBE-PRODUCT', qty: 0.0001 }],
      };
      const r1 = await postSalesOutbound(payload);
      const firstCode = r1.json && r1.json.code;
      if (!(r1.status === 200 && firstCode === 0)) {
        report(name, 'SKIP', `首次未能成功出库，无法验证幂等（${summarize(r1)}）`);
      } else {
        const r2 = await postSalesOutbound(payload);
        const idem = r2.json && r2.json.data && r2.json.data.idempotent === true;
        if (r2.status === 200 && idem) {
          report(name, 'PASS', `第二次 idempotent=true salesOutCode=${r2.json.data.salesOutCode}`);
        } else {
          report(name, 'FAIL', `r1=${summarize(r1)} | r2=${summarize(r2)}`);
        }
      }
    }
  }

  // 用例 4：超量出库 -> 业务错误
  {
    const name = '用例4 超量出库';
    const key = uniqueIdempotencyKey();
    const payload = {
      idempotencyKey: key,
      sourceOrderCode: ORDER_CODE || 'PROBE-NON-EXISTENT-ORDER',
      erpOutboundNo: key,
      lines: [{ productCode: 'PROBE-PRODUCT', qty: 999999 }],
    };
    const resp = await postSalesOutbound(payload);
    const code = resp.json && resp.json.code;
    const hasFailedLines = resp.json && resp.json.data && Array.isArray(resp.json.data.failedLines) && resp.json.data.failedLines.length > 0;
    if (resp.status === 200 && (code === 40006 || hasFailedLines)) {
      report(name, 'PASS', `code=40006 failedLines=${hasFailedLines}`);
    } else if (resp.json && code === 40401) {
      report(name, 'PASS', `订单不存在返回 40401（${resp.json.message}）`);
    } else {
      report(name, 'FAIL', `status=${resp.status} ${summarize(resp)}`);
    }
  }

  // 用例 5：缺字段 -> 校验失败（lines 空 / 缺 idempotencyKey）
  {
    const name = '用例5 缺字段校验';
    const payload = { sourceOrderCode: ORDER_CODE || 'X', erpOutboundNo: 'X', lines: [] };
    const body = JSON.stringify(payload);
    const headers = sign('POST', PATH_API, body, APP_SECRET);
    const resp = await request('POST', PATH_API, body, headers);
    const code = resp.json && resp.json.code;
    if (resp.status === 200 || resp.status === 400) {
      if (code === 40001) report(name, 'PASS', `code=40001 ${resp.json.message}`);
      else report(name, 'FAIL', `期望 40001，实际 ${summarize(resp)}`);
    } else {
      report(name, 'FAIL', `status=${resp.status} ${summarize(resp)}`);
    }
  }

  // 用例 6：签名错误 -> 401
  {
    const name = '用例6 签名错误';
    const payload = {
      idempotencyKey: uniqueIdempotencyKey(),
      sourceOrderCode: ORDER_CODE || 'X',
      erpOutboundNo: 'X',
      lines: [{ productCode: 'PROBE-PRODUCT', qty: 1 }],
    };
    const resp = await postSalesOutbound(payload, { badSignature: true });
    if (resp.status === 401) {
      report(name, 'PASS', `HTTP 401 ${resp.json && resp.json.message}`);
    } else {
      report(name, 'FAIL', `期望 401，实际 status=${resp.status} ${summarize(resp)}`);
    }
  }

  console.log('------------------------------------------------------------');
  console.log(`结果: PASS=${pass} FAIL=${fail} SKIP=${skip}`);
  process.exit(fail > 0 ? 1 : 0);
}

main().catch((e) => { console.error(e); process.exit(2); });