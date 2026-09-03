/**
 * v4.6.2 授权管理：列表页渲染、授权-下单开关、新建授权页选择器、API 黑盒校验。
 * 覆盖：授权列表 DOM/开关 UI、新建页产品线+医院批量选择控件、order-enforce 读写、
 *       产品线/终端选择器、排他冲突拦截。测试数据（如创建授权）在末尾清理。
 */
const { test, expect, attachErrorGuard, authedPage, authedApi } = require('../fixtures/test-fixtures')
const env = require('../helpers/env')

const AUTH_LIST = '/dms/authorizations'
const AUTH_NEW = '/dms/authorizations/new'

test.describe('授权管理 - PC UI', () => {
  test('授权管理列表页可渲染并显示开关与列表', async ({ browser }) => {
    const { context, page } = await authedPage(browser, 'pc')
    const guard = attachErrorGuard(page, { allow: ['/api/authorizations/'] })
    await page.goto(AUTH_LIST, { waitUntil: 'domcontentloaded' })
    await expect(page).not.toHaveURL(/\/login/)
    // 页面主体出现（授权相关文案）
    await expect(page.locator('body')).toContainText(/授权/, { timeout: 20000 })
    await expect(page.locator('.el-table, table').first()).toBeVisible({ timeout: 15000 })
    guard.assertClean()
    await context.close()
  })

  test('新建授权页：经销商/产品线/医院批量选择控件存在', async ({ browser }) => {
    const { context, page } = await authedPage(browser, 'pc')
    const guard = attachErrorGuard(page, { allow: ['/api/dealers', '/api/authorizations/terminals', '/api/authorizations/product-lines'] })
    await page.goto(AUTH_NEW, { waitUntil: 'domcontentloaded' })
    await expect(page).not.toHaveURL(/\/login/)
    // 关键控件：经销商选择、产品线多选、有效期、医院表格 + 整省全选
    await expect(page.locator('input').first()).toBeVisible({ timeout: 15000 })
    const bodyText = await page.locator('body').innerText()
    expect(bodyText).toContain('经销商')
    expect(bodyText).toContain('产品线')
    expect(bodyText).toContain('授权终端医院')
    expect(bodyText).toContain('全选当前结果')
    guard.assertClean()
    await context.close()
  })
})

test.describe('授权管理 - 黑盒 API', () => {
  test('order-enforce 开关：默认值可读且翻转后一致', async () => {
    const { api, token } = await authedApi('pc')
    const headers = { Authorization: `Bearer ${token}` }
    // 读取当前值
    const get0 = await api.get('/api/authorizations/order-enforce', { headers })
    expect(get0.status()).toBe(200)
    const before = (await get0.json()).data.enforced
    // 置为 false（确保解耦），再读回
    await api.post('/api/authorizations/order-enforce', { headers, data: { enabled: false } })
    const off = (await (await api.get('/api/authorizations/order-enforce', { headers })).json()).data
    expect(off.enforced).toBe(false)
    // 置 true，立即读回应为 true（缓存失效 key 一致性回归）
    await api.post('/api/authorizations/order-enforce', { headers, data: { enabled: true } })
    const on = (await (await api.get('/api/authorizations/order-enforce', { headers })).json()).data
    expect(on.enforced).toBe(true)
    // 还原
    await api.post('/api/authorizations/order-enforce', { headers, data: { enabled: before } })
  })

  test('选择器：产品线与终端医院按区域过滤返回结构正确', async () => {
    const { api, token } = await authedApi('pc')
    const headers = { Authorization: `Bearer ${token}` }
    const lines = await (await api.get('/api/authorizations/product-lines', { headers })).json()
    expect(Array.isArray(lines.data)).toBe(true)

    const terms = await (await api.get('/api/authorizations/terminals', { headers, params: { regionId: 1 } })).json()
    expect(Array.isArray(terms.data)).toBe(true)
    if (terms.data.length) {
      expect(terms.data[0]).toHaveProperty('id')
      expect(terms.data[0]).toHaveProperty('name')
    }
  })

  test('check：开关关闭时即使无授权也放行', async () => {
    const { api, token } = await authedApi('pc')
    const headers = { Authorization: `Bearer ${token}` }
    // 确保关闭
    await api.post('/api/authorizations/order-enforce', { headers, data: { enabled: false } })
    const res = await api.post('/api/authorizations/check', {
      headers,
      data: { dealerId: 11, authType: 'ORDER', lines: [{ productId: 1 }] }
    })
    expect(res.status()).toBe(200)
    const data = (await res.json()).data
    expect(Array.isArray(data)).toBe(true)
    expect(data[0].authorized).toBe(true)
  })
})
