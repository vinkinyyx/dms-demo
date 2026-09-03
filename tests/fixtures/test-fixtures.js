/**
 * 统一测试夹具：
 *  - authedContext(role)：已登录的浏览器 context + page（storageState 复用登录）
 *  - authedRequest(role)：已带 Bearer token 的 APIRequestContext（黑盒接口直调）
 *  - attachErrorGuard(page)：监听 console error 与 pageerror、network 5xx/4xx（预期外）
 *  - db：Node pg 连接池，用于关键操作后 SQL 回读（金额/状态/库存/回滚）
 */
const { test: base, expect, request } = require('@playwright/test')
const env = require('../helpers/env')

async function loginPayload(role) {
  const acc = env.accounts[role]
  // 平台后台(admin-vue)走独立登录端点 /api/admin/auth/login（返回 authSource=PLATFORM 的 token）；
  // PC/移动(frontend-vue)走业务端 /api/auth/login（需带租户编码）。两者 token 不通用，混用会导致 /api/admin/** 全 401。
  const isAdmin = role === 'admin'
  const url = isAdmin ? '/api/admin/auth/login' : '/api/auth/login'
  const body = isAdmin
    ? { username: acc.username, password: acc.password }
    : { tenantCode: acc.tenant, username: acc.username, password: acc.password }
  const api = await request.newContext({ baseURL: env.apiBase, ignoreHTTPSErrors: true })
  const res = await api.post(url, { data: body })
  const json = await res.json().catch(() => ({}))
  const token = json?.data?.accessToken
  if (!token) {
    throw new Error(`登录失败 role=${role} status=${res.status()} body=${JSON.stringify(json).slice(0, 200)}`)
  }
  return { token, api }
}

/** 角色 -> 已登录的 APIRequestContext + token（黑盒接口直调）。 */
async function authedApi(role) {
  return loginPayload(role)
}

// 各端前端实际读取的 localStorage token key（pc/mobile 同属 frontend-vue，admin 是独立 admin-vue）。
const TOKEN_KEY = { pc: 'dms_access_token', mobile: 'dms_access_token', admin: 'admin_access_token' }

/** 角色 -> 已登录浏览器 context + page（localStorage 注入 token，UI 测试复用）。 */
async function authedPage(browser, role) {
  const { token } = await loginPayload(role)
  const context = await browser.newContext({ ignoreHTTPSErrors: true })
  const key = TOKEN_KEY[role] || 'dms_access_token'
  await context.addInitScript(([t, k]) => {
    try { localStorage.setItem(k, t) } catch (e) {}
  }, [token, key])
  const page = await context.newPage()
  return { context, page, token }
}

/**
 * 注入 Console / 网络错误守卫：把未捕获错误与 5xx 收集起来，用 expectSoft 在测试末尾断言。
 * 预期内的 4xx（如权限/校验）可通过 allow 传入 URL 片段白名单。
 */
function attachErrorGuard(page, { allow = [] } = {}) {
  const errors = []
  page.on('console', (msg) => {
    if (msg.type() === 'error') errors.push(`console.error: ${msg.text().slice(0, 300)}`)
  })
  page.on('pageerror', (err) => errors.push(`pageerror: ${String(err).slice(0, 300)}`))
  page.on('response', (resp) => {
    const url = resp.url()
    const status = resp.status()
    if (status >= 500) errors.push(`network ${status}: ${url.split('?')[0]}`)
    else if (status >= 400 && !allow.some((a) => url.includes(a))) {
      // 4xx 默认也记录（静态资源/字体等除外），业务预期 4xx 用 allow 白名单
      if (/\/api\/|\/auth\//.test(url)) errors.push(`network ${status}: ${url.split('?')[0]}`)
    }
  })
  return {
    errors,
    assertClean() {
      if (errors.length) throw new Error('前端运行期错误:\n' + errors.join('\n'))
    }
  }
}

const test = base.extend({
  guard: async ({}, use) => {
    await use(attachErrorGuard)
  }
})

module.exports = { test, expect, attachErrorGuard, authedApi, authedPage, loginPayload }

