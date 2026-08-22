// E2E runner - runs all spec files sequentially
const { execSync } = require("child_process");
const fs = require("fs");
const path = require("path");

const specsDir = path.join(__dirname, "specs");
const args = process.argv.slice(2);
const onlyModule = args.find(a => a.startsWith("--module="));
const filter = onlyModule ? onlyModule.split("=")[1] : null;

let specs = fs.readdirSync(specsDir).filter(f => f.endsWith(".spec.js")).sort();
if (filter) {
  specs = specs.filter(s => s.toLowerCase().includes(filter.toLowerCase()));
}

console.log("=== E2E Test Suite ===");
console.log("Running", specs.length, "spec file(s)");
console.log("");

let passed = 0, failed = 0;
const failedSpecs = [];

for (const spec of specs) {
  const specPath = path.join(specsDir, spec);
  console.log("\n>>> Running: " + spec);
  try {
    execSync("node " + specPath, { stdio: "inherit", cwd: path.resolve(__dirname, "../.."), timeout: 600000 });
    passed++;
  } catch(e) {
    failed++;
    failedSpecs.push(spec);
    console.log(">>> FAILED: " + spec);
  }
}

console.log("\n=== E2E Summary ===");
console.log("Specs passed:", passed, "/", specs.length);
if (failedSpecs.length) {
  console.log("Failed specs:", failedSpecs.join(", "));
}
process.exit(failed > 0 ? 1 : 0);
