/**
 * 移动 H5：真实登录 -> 移动首页渲染、无运行期错误；移动视口（iPhone）。
 */
const { test, expect, attachErrorGuard, authedPage } = require('../fixtures/test-fixtures')
const env = require('../helpers/env')

test('移动 H5 登录后进入首页', async ({ browser }) => {
  const { context, page } = await authedPage(browser, 'mobile')
  const guard = attachErrorGuard(page)
  await page.goto(env.paths.mobileHome, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(2500)
  expect(page.url()).not.toContain('/login')
  const domRefs = await page.locator('*').count()
  expect(domRefs).toBeGreaterThanOrEqual(10)
  guard.assertClean()
  await context.close()
})
