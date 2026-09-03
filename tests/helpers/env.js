/**
 * 被测环境与账号配置。所有 E2E/API 测试唯一来源，禁止在 spec 里硬编码地址/账号。
 */
const BASE = (process.env.E2E_BASE || 'http://dms-dev.mysolmed.com').replace(/\/$/, '')

module.exports = {
  BASE,
  // 业务前端部署在 /dms/ 前缀下（见 AGENTS.md §1 部署基线）。
  appBase: BASE + '/dms',
  apiBase: BASE, // /api、/auth、/actuator 走根路径反代
  accounts: {
    pc: { tenant: 'default', username: 'sys_admin', password: 'Dms@123456' },
    admin: { username: 'admin', password: 'Sh123456' },
    mobile: { tenant: 'default', username: 'sys_admin', password: 'Dms@123456' }
  },
  paths: {
    pcLogin: '/dms/login',
    pcHome: '/dms/home',
    adminLogin: '/dms/admin/login',
    adminHome: '/dms/admin/',
    mobileLogin: '/dms/mobile/login',
    mobileRegister: '/dms/mobile/register',
    mobileHome: '/dms/mobile/home'
  },
  // 铁律9 必检用户入口（HTTP 状态 + 最终渲染）。
  entryUrls: [
    { name: 'root-302', url: BASE + '/', expectRedirect: '/dms/' },
    { name: 'pc', url: BASE + '/dms/' },
    { name: 'admin', url: BASE + '/dms/admin/' },
    { name: 'mobile-login', url: BASE + '/dms/mobile/login' },
    { name: 'mobile-register', url: BASE + '/dms/mobile/register' },
    { name: 'brochure-pc', url: BASE + '/brochure/', titleIncludes: '宣传' },
    { name: 'brochure-mobile', url: BASE + '/brochure/mobile.html' },
    { name: 'brochure-print', url: BASE + '/brochure/print.html' }
  ],
  healthUrl: BASE + '/actuator/health'
}

