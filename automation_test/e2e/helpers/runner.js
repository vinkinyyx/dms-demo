// Simple test runner with assertions and structured reporting
const fs = require("fs");
const path = require("path");
const config = require("../config");
const { createErrorCollector } = require("./errors");

function createRunner(suiteName) {
  const results = [];
  const logs = [];
  const errorCollector = createErrorCollector();

  function log(msg) {
    logs.push(msg);
    console.log("  " + msg);
  }

  function assert(name, condition, detail) {
    const row = { name, pass: !!condition, detail: String(detail || "").slice(0, 300) };
    results.push(row);
    console.log("  " + (condition ? "PASS" : "FAIL") + " | " + name + (detail ? " | " + row.detail : ""));
  }

  async function step(name, fn) {
    console.log("\n[" + name + "]");
    try {
      await fn();
    } catch(e) {
      assert(name, false, "EXCEPTION: " + e.message.slice(0, 200));
    }
  }

  function summary() {
    const failed = results.filter(r => !r.pass);
    const passed = results.length - failed.length;
    return { suite: suiteName, total: results.length, passed, failed: failed.length, failures: failed, logs };
  }

  function saveReport() {
    if (!fs.existsSync(config.RESULT_DIR)) fs.mkdirSync(config.RESULT_DIR, { recursive: true });
    const s = summary();
    const file = path.join(config.RESULT_DIR, suiteName.replace(/[^a-z0-9_-]/gi, "_") + ".json");
    fs.writeFileSync(file, JSON.stringify(s, null, 2));
    return file;
  }

  return { assert, step, log, summary, saveReport, errorCollector };
}

module.exports = { createRunner };
