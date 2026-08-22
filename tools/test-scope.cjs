#!/usr/bin/env node
/**
 * DMS 范围深度测试调度器
 *
 * 用法：
 *   node tools/test-scope.cjs --scope=order,sales-return --dry-run
 *   node tools/test-scope.cjs --scope=order --core-flows=all
 *   node tools/test-scope.cjs --module=order  (module 形式，自动展开为 scope)
 *
 * 模式：
 *   --dry-run      仅输出待测清单 JSON，不执行（默认）
 *   --execute      真执行（暂未实现，需配合 Playwright + pytest）
 *
 * 退出码：
 *   0 - 清单生成成功
 *   1 - 参数错误 / scope 未识别
 */
const fs = require("fs");
const path = require("path");

const args = process.argv.slice(2);
function arg(name, def) {
  const a = args.find(x => x.startsWith("--" + name + "="));
  return a ? a.substring(name.length + 3) : def;
}
const SCOPE = (arg("scope", "") || "").split(",").map(s => s.trim()).filter(Boolean);
const MODULE = (arg("module", "") || "").split(",").map(s => s.trim()).filter(Boolean);
const INCLUDE = (arg("include", "") || "").split(",").map(s => s.trim()).filter(Boolean);
const CORE_FLOWS = (arg("core-flows", "auto") || "auto");
const DRY_RUN = !args.includes("--execute");
const BASE = (arg("base") || process.env.E2E_BASE || "http://43.128.145.141").replace(/\/$/, "");

const MAP_PATH = path.join(__dirname, "scope-map.json");
if (!fs.existsSync(MAP_PATH)) {
  console.error("FATAL: scope-map.json not found at " + MAP_PATH);
  process.exit(1);
}
const MAP = JSON.parse(fs.readFileSync(MAP_PATH, "utf8"));

// === 参数校验 ===
if (SCOPE.length === 0 && MODULE.length === 0) {
  console.error("ERROR: 必须指定 --scope=<flow1,flow2> 或 --module=<m1,m2>");
  console.error("");
  console.error("可用流程（来自 scope-map.json coreFlows）:");
  MAP.coreFlows.forEach(f => console.error("  " + f.id + " - " + f.name));
  console.error("");
  console.error("可用模块（来自 scope-map.json modules）:");
  Object.keys(MAP.modules).forEach(m => console.error("  " + m + " - " + MAP.modules[m].name));
  process.exit(1);
}

// === scope 可以是 F1-F5 流程 id 或 "coreFlows" 关键字 ===
const KNOWN_FLOWS = MAP.coreFlows.map(f => f.id);
const KNOWN_MODULES = Object.keys(MAP.modules);
const errors = [];
SCOPE.forEach(s => {
  if (!KNOWN_FLOWS.includes(s) && s !== "coreFlows") {
    errors.push("未识别的 scope: " + s + "（期望 " + KNOWN_FLOWS.join("/") + " 或 coreFlows）");
  }
});
MODULE.forEach(m => {
  if (!KNOWN_MODULES.includes(m)) {
    errors.push("未识别的 module: " + m + "（期望 " + KNOWN_MODULES.join("/") + "）");
  }
});
if (errors.length) {
  console.error("ERROR:"); errors.forEach(e => console.error("  " + e));
  process.exit(1);
}

// === 计算实际要测的流程集 ===
let targetFlows = new Set();

if (SCOPE.includes("coreFlows") || CORE_FLOWS === "all") {
  MAP.coreFlows.forEach(f => targetFlows.add(f.id));
}
SCOPE.forEach(s => { if (KNOWN_FLOWS.includes(s)) targetFlows.add(s); });

// --module 展开为 flows
MODULE.forEach(m => {
  const flows = MAP.moduleToFlows[m] || [];
  flows.forEach(f => targetFlows.add(f));
});

// --include 追加（不影响 coreFlows）
INCLUDE.forEach(s => { if (KNOWN_FLOWS.includes(s)) targetFlows.add(s); });

if (targetFlows.size === 0) {
  console.error("ERROR: 范围参数展开后为空。请确认 --scope/--module/--include 值。");
  process.exit(1);
}

// === 构建待测清单 ===
const plan = {
  generatedAt: new Date().toISOString(),
  targetBase: BASE,
  version: MAP.version,
  scopeInput: { scope: SCOPE, module: MODULE, include: INCLUDE, coreFlows: CORE_FLOWS },
  targetFlows: Array.from(targetFlows).sort(),
  flows: [],
  totals: { uiPaths: 0, apiEndpoints: 0, dbTables: 0, e2eSpecs: 0 }
};

const seenUIPaths = new Set();
const seenAPI = new Set();
const seenTables = new Set();
const seenSpecs = new Set();

MAP.coreFlows.filter(f => targetFlows.has(f.id)).forEach(f => {
  const flow = {
    id: f.id,
    name: f.name,
    modules: f.modules,
    uiPaths: f.uiPaths.map(p => ({ ...p, status: "TODO" })),
    apiEndpoints: f.apiEndpoints.map(a => ({ endpoint: a, status: "TODO" })),
    dbTables: f.dbTables.map(t => ({ table: t, status: "TODO" })),
    e2eSpecs: f.e2eSpecs.map(s => ({ spec: s, status: "TODO" })),
    fiveDimensionsChecklist: [
      "[前端 UI] 列表/新建/详情/状态动作/业务按钮 真实点击",
      "[后端 API] 主流程 + 边界 + 性能直调",
      "[数据库] 单据状态/关联行/业务量/不变量 直查回读",
      "[业务规则] BOM/促销/价格/库存/反向顺序",
      "[异常路径] 撤销/驳回/重复提交/并发"
    ]
  };
  f.uiPaths.forEach(p => seenUIPaths.add(JSON.stringify(p)));
  f.apiEndpoints.forEach(a => seenAPI.add(a));
  f.dbTables.forEach(t => seenTables.add(t));
  f.e2eSpecs.forEach(s => seenSpecs.add(s));
  plan.flows.push(flow);
});

plan.totals.uiPaths = seenUIPaths.size;
plan.totals.apiEndpoints = seenAPI.size;
plan.totals.dbTables = seenTables.size;
plan.totals.e2eSpecs = seenSpecs.size;

// === 风险提示 ===
plan.riskHints = [];
MODULE.forEach(m => {
  if (MAP.riskHints[m]) plan.riskHints.push(m + ": " + MAP.riskHints[m]);
});

// === 输出 ===
if (DRY_RUN) {
  console.log("=== DMS Scope Test Plan (dry-run) ===");
  console.log("");
  console.log("Target base:    " + plan.targetBase);
  console.log("Target flows:   " + plan.targetFlows.join(", "));
  console.log("Modules input:  " + (MODULE.join(", ") || "(none)"));
  console.log("Scope input:    " + (SCOPE.join(", ") || "(none)"));
  console.log("Include input:  " + (INCLUDE.join(", ") || "(none)"));
  console.log("Core flows:     " + CORE_FLOWS);
  console.log("");
  console.log("Totals: " + plan.totals.uiPaths + " UI paths, " +
              plan.totals.apiEndpoints + " API endpoints, " +
              plan.totals.dbTables + " DB tables, " +
              plan.totals.e2eSpecs + " E2E specs");
  console.log("");
  plan.flows.forEach(f => {
    console.log("--- " + f.id + " - " + f.name + " ---");
    console.log("  Modules: " + f.modules.join(", "));
    console.log("  UI (" + f.uiPaths.length + "): " + f.uiPaths.map(p => p.path).join(", "));
    console.log("  API (" + f.apiEndpoints.length + "): " + f.apiEndpoints.map(a => a.endpoint).join(", "));
    console.log("  DB (" + f.dbTables.length + "): " + f.dbTables.map(t => typeof t === "string" ? t : t.table).join(", "));
    console.log("  E2E (" + f.e2eSpecs.length + "): " + f.e2eSpecs.map(s => typeof s === "string" ? s : s.spec).join(", "));
    console.log("  5 维清单:");
    f.fiveDimensionsChecklist.forEach(c => console.log("    - " + c));
    console.log("");
  });
  if (plan.riskHints.length) {
    console.log("=== Risk Hints ===");
    plan.riskHints.forEach(h => console.log("  " + h));
    console.log("");
  }
  console.log("Plan JSON: " + JSON.stringify(plan, null, 2).substring(0, 200) + "...");
  console.log("");
  console.log("执行模式: --execute（暂未实现，会列出将要调用的命令清单）");
  process.exit(0);
} else {
  // --execute 模式：占位
  console.log("ERROR: --execute 模式尚未实现。当前仅支持 --dry-run。");
  console.log("请把 plan 交给 Playwright/Python 脚本来执行。");
  process.exit(2);
}
