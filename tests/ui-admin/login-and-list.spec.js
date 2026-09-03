/**
 * 平台后台：真实登录 -> 后台首页渲染、无运行期错误。
 * 平台后台走独立登录端点 /api/admin/auth/login（authSource=PLATFORM），见 fixtures/test-fixtures.js。
 */
const { test, expect, attachErrorGuard, authedPage } = require('../fixtures/test-fixtures')
const env = require('../helpers/env')

test('平台后台登录后可进入后台首页', async ({ browser }) => {
  const { context, page } = await authedPage(browser, 'admin')
  const guard = attachErrorGuard(page)
  await page.goto(env.paths.adminHome, { waitUntil: 'domcontentloaded' })
  // 等待后台布局真实渲染（侧边菜单或后台标题），而非固定 sleep
  await expect(page.locator('.el-menu, .el-aside, .el-container, .layout').first()).toBeVisible({ timeout: 20000 })
  await expect(page).not.toHaveURL(/\/login/)
  const domRefs = await page.locator('*').count()
  expect(domRefs).toBeGreaterThanOrEqual(20)
  guard.assertClean()
  await context.close()
})
