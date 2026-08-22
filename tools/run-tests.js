#!/usr/bin/env node
// Unified test runner: L1 static -> L2 API -> L3 smoke -> L4 E2E
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

const steps = [
  { id: 1, key: 'static', name: 'L1 Static Analysis', cmd: 'node tools/lint-static.js', critical: true },
  { id: 2, key: 'api', name: 'L2 API Smoke', cmd: 'python automation_test/api_smoke.py', critical: true },
  { id: 3, key: 'smoke', name: 'L3 UI Smoke', cmd: 'node tools/smoke-test.cjs', critical: false },
  { id: 4, key: 'e2e', name: 'L4 E2E', cmd: 'node automation_test/e2e/run-all.js', critical: false },
];

if (onlyModule) {
  steps[2].cmd += ' --module=' + onlyModule;
  steps[3].cmd += ' --module=' + onlyModule;
}

let allPass = true;
const results = [];

for (const step of steps) {
  if (!levels.includes(step.id) || skip.includes(step.key)) continue;
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
