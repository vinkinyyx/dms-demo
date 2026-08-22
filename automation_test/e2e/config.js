// E2E test configuration
const ENV = process.env.E2E_BASE || "http://43.128.145.141";
module.exports = {
  BASE: ENV.replace(/\/$/, ""),
  HEADLESS: process.env.E2E_HEADED !== "1",
  TIMEOUT: 15000,
  RESULT_DIR: require("path").join(__dirname, "results", "run-" + Date.now()),

  pc: {
    loginPath: "/login",
    startPath: "/home",
    username: "admin",
    password: "Sh123456",
    tenant: "default",
  },
  admin: {
    loginPath: "/admin/login",
    startPath: "/admin/tenants/manufacturers",
    username: "admin",
    password: "Sh123456",
  },
  mobile: {
    loginPath: "/mobile/login",
    startPath: "/mobile/home",
    username: "admin",
    password: "Sh123456",
  },

  // Unique test data prefix to identify and clean up
  testPrefix: "E2E_" + Date.now().toString(36).toUpperCase() + "_",
};
