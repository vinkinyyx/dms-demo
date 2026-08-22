#!/usr/bin/env node
// tools/lint-static.js
// L1 Static checks: scans frontend source for high-risk patterns.
// Usage: node tools/lint-static.js [--fix]
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..");
const FE_DIRS = [
  path.join(ROOT, "frontend-vue/src"),
  path.join(ROOT, "admin-vue/src"),
];

const results = [];

function check(name, file, line, lineNum, detail) {
  results.push({ severity: name.includes("CRITICAL") ? "error" : "warn", rule: name, file, line: lineNum, detail });
}

function walk(dir, exts, out) {
  if (!fs.existsSync(dir)) return;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (!["node_modules", ".git", "dist", ".vite"].includes(e.name)) walk(full, exts, out);
    } else if (exts.some(x => e.name.endsWith(x))) {
      out.push(full);
    }
  }
}

const files = [];
for (const d of FE_DIRS) walk(d, [".vue", ".js", ".ts"], files);

// Regex patterns to flag
const rules = [
  { name: "WARN: ISO date in code (verify it is not rendered directly)", re: /toISOString\s*\(\)|["'`]T00:00:00|\+08:00['"`]/g },
  { name: "WARN: console.log in production code", re: /console\.(log|debug)\s*\(/g },
  { name: "CRIT: debugger statement", re: /\bdebugger\b/g },
  { name: "WARN: hardcoded API URL", re: /(?:fetch|axios)\s*\(\s*["'`]https?:\/\/[^"'`]+\/api\//g },
  { name: "WARN: hardcoded localhost API", re: /["'`]http:\/\/localhost:\d+\/api\//g },
];

for (const file of files) {
  const src = fs.readFileSync(file, "utf8");
  const lines = src.split(/\r?\n/);
  const rel = path.relative(ROOT, file);

  for (const rule of rules) {
    rule.re.lastIndex = 0;
    for (const line of lines) {
      const idx = lines.indexOf(line);
      if (rule.re.test(line)) {
        // Filter out test/utility files for console.log
        if (rule.name.includes("console.log") && /(\.spec\.|test|__tests__|mock)/i.test(rel)) continue;
        check(rule.name, rel, line.trim().slice(0, 120), idx + 1);
      }
      rule.re.lastIndex = 0;
    }
  }

  // Check for garbled Chinese (replacement char)
  if (/\uFFFD/.test(src)) {
    const idx = src.indexOf("\uFFFD");
    const before = src.slice(Math.max(0, idx - 40), idx);
    check("CRIT: garbled character (replacement char U+FFFD)", rel, before, 0);
  }

  // Check for v-if=false placeholder buttons (anti-pattern)
  if (/v-if=["']false["']/.test(src)) {
    check("WARN: v-if=false placeholder", rel, "button/element hidden with v-if=false", 0);
  }
}

// Report
const errors = results.filter(r => r.severity === "error");
const warns = results.filter(r => r.severity === "warn");

console.log("=== L1 Static Analysis ===");
console.log("Scanned", files.length, "files");
console.log("Errors:", errors.length, "| Warnings:", warns.length);
console.log("");

if (results.length === 0) {
  console.log("PASS: no issues found");
  process.exit(0);
}

for (const r of results) {
  const icon = r.severity === "error" ? "ERROR" : "WARN ";
  console.log(`[${icon}] ${r.rule}`);
  console.log(`  ${r.file}:${r.line}`);
  if (r.detail) console.log(`  -> ${r.detail}`);
}

process.exit(errors.length > 0 ? 1 : 0);
