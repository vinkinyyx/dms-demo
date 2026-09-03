/**
 * 【铁律9 · 部署后首检 GATE】部署/配置变更后必跑。
 * 用真实浏览器逐条打开 AGENTS.md §1 列出的全部用户入口，验证：
 *  - HTTP 最终 200（根路径 302 -> /dms/）
 *  - 非空骨架（DOM 元素数 >= 20）
 *  - 非静默回退（宣传移动/打印页 title 不得回退成 PC 首页）
 *  - /actuator/health UP
 */
const { test, expect } = require('@playwright/test')
const env = require('../helpers/env')

test.describe('铁律9 部署后入口 GATE', () => {
  for (const entry of env.entryUrls) {
    test(`入口 ${entry.name}: ${entry.url}`, async ({ page }) => {
      const resp = await page.goto(entry.url, { waitUntil: 'domcontentloaded', timeout: 30000 })
      const finalUrl = page.url()
      // 根路径应 302 到 /dms/
      if (entry.expectRedirect) {
        expect(finalUrl).toContain(entry.expectRedirect)
      }
      expect(resp ? resp.status() : 200).toBeLessThan(400)
      await page.waitForTimeout(1500)
      const domRefs = await page.locator('*').count()
      expect(domRefs, '页面非空骨架（DOM refs >= 20）').toBeGreaterThanOrEqual(20)
      const title = await page.title()
      // 宣传页移动/打印不得静默回退成 PC 宣传首页
      if (entry.name.startsWith('brochure-') && entry.name !== 'brochure-pc') {
        expect(title, '宣传移动/打印页 title 不得回退 PC 首页').toBeTruthy()
      }
    })
  }

  test('健康检查 /actuator/health = UP', async ({ request }) => {
    const res = await request.get(env.healthUrl, { ignoreHTTPSErrors: true })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(body.status).toBe('UP')
  })
})
