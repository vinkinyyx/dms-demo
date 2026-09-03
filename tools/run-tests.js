#!/usr/bin/env node
// 统一测试编排（每层只用最合适的唯一工具，见 docs/02_设计/test-strategy.md）：
//   L1 静态门禁      -> tools/lint-static.js（硬编码API/乱码/console/日期格式）
//   L2 后端单元+集成 -> mvn test（JUnit5+Mockito+嵌入式PG）；BACKEND_IT=1 时 mvn verify 追加 *IT 真实Redis(需Docker)
//   L3 前端单元      -> frontend-vue vitest（含覆盖率门禁）
//   L4 黑盒 API+UI   -> Playwright Test（三端 project + API + 部署GATE），需被测环境可达
const { execSync } = require('child_process');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const args = process.argv.slice(2);

function arg(name, def) {
  const prefix = '--' + name + '=';
  const hit = args.find(a => a.startsWith(prefix));
  return hit ? hit.slice(prefix.length) : def;
}

const levels = (arg('level', '1,2,3,4') || '1,2,3,4').split(',').map(Number);
const skip = (arg('skip', '') || '').split(',').filter(Boolean);
const onlyModule = arg('module', '');

const backendVerify = process.env.BACKEND_IT === '1'; // BACKEND_IT=1 才跑真实 Redis 的 *IT（需 Docker）
const steps = [
  { id: 1, key: 'static', name: 'L1 静态门禁 (lint-static)', cmd: 'node tools/lint-static.js', critical: true },
  { id: 2, key: 'backend', name: 'L2 后端单元+集成 (JUnit/嵌入式PG' + (backendVerify ? '+真实Redis IT' : '') + ')',
    cmd: backendVerify ? 'mvn -f backend/pom.xml verify' : 'mvn -f backend/pom.xml test', critical: true },
  { id: 3, key: 'frontend', name: 'L3 前端单元 (Vitest + 覆盖率)', cmd: 'npm --prefix frontend-vue run test:coverage', critical: true },
  { id: 4, key: 'e2e', name: 'L4 黑盒 API+UI 三端 + 部署GATE (Playwright Test)',
    cmd: 'npx playwright test --config tests/playwright.config.js', critical: false, needsEnv: true },
];

if (onlyModule) {
  steps[3].cmd += ' --grep "' + onlyModule + '"';
}

let allPass = true;
const results = [];

for (const step of steps) {
  if (!levels.includes(step.id) || skip.includes(step.key)) continue;
  if (step.needsEnv && process.env.SKIP_E2E === '1') {
    console.log('--- skip ' + step.name + '（SKIP_E2E=1）');
    continue;
  }
  console.log('\n' + '='.repeat(60));
  console.log('>>> ' + step.name);
  console.log('='.repeat(60));
  try {
    execSync(step.cmd, { stdio: 'inherit', cwd: ROOT, timeout: 1800000, env: process.env });
    results.push({ name: step.name, pass: true });
  } catch (error) {
    results.push({ name: step.name, pass: false });
    allPass = false;
    if (step.critical) {
      console.log('>>> Critical failure, stopping.');
      break;
    }
  }
}

console.log('\n' + '='.repeat(60));
console.log('=== TEST RUN SUMMARY ===');
for (const result of results) {
  console.log((result.pass ? 'PASS' : 'FAIL') + ' | ' + result.name);
}
console.log('='.repeat(60));
process.exit(allPass ? 0 : 1);
