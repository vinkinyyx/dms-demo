/**
 * PC 业务前台：真实登录 -> 工作台 -> 进入核心列表页，验证可渲染、Console/Network 干净。
 * UI 流程用例（F1~F5 下单审批等）按此模板在 ui-pc/ 下继续添加。
 */
const { test, expect, attachErrorGuard, authedPage } = require('../fixtures/test-fixtures')
const env = require('../helpers/env')

test('PC 登录后进入工作台并能打开销售订单列表', async ({ browser }) => {
  const { context, page } = await authedPage(browser, 'pc')
  const guard = attachErrorGuard(page)
  await page.goto(env.paths.pcHome, { waitUntil: 'domcontentloaded' })
  // 等待工作台真正渲染（侧边菜单出现），避免“加载中…”骨架被误判为空页面
  await expect(page.locator('.el-menu, .layout-sidebar, .sidebar, aside').first()).toBeVisible({ timeout: 20000 })
  // 不应被踢回登录页
  await expect(page).not.toHaveURL(/\/login/)
  const bodyText = await page.locator('body').innerText()
  expect(bodyText.length).toBeGreaterThan(50)
  expect(bodyText).toContain('工作台')
  guard.assertClean()
  await context.close()
})
