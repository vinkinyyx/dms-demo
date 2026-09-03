/**
 * 统一 E2E / 黑盒测试配置（Playwright Test 唯一 runner）。
 *
 * 三个 project 对应三端：pc（业务前台）、admin（平台后台）、mobile（移动 H5）。
 * api 与 gate 用 Playwright 的 request fixture / 浏览器，不绑定具体端。
 *
 * 被测系统默认指向测试环境（见 AGENTS.md §1）。可用环境变量覆盖：
 *   E2E_BASE    目标根地址（默认 http://dms-dev.mysolmed.com）
 *   E2E_HEADED  设为 1 时有头运行（本地调试）
 *   PW_PROJECTS 逗号分隔，只跑指定 project，如 PW_PROJECTS=pc,mobile
 */
const { defineConfig, devices } = require('@playwright/test')

const BASE = (process.env.E2E_BASE || 'http://dms-dev.mysolmed.com').replace(/\/$/, '')
const headed = process.env.E2E_HEADED === '1'
const only = (process.env.PW_PROJECTS || '').split(',').map((s) => s.trim()).filter(Boolean)
const pick = (name) => only.length === 0 || only.includes(name)

module.exports = defineConfig({
  testDir: '.',
  timeout: 60000,
  expect: { timeout: 10000 },
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['list'], ['html', { outputFolder: 'reports/playwright/html', open: 'never' }]],
  outputDir: 'reports/playwright/test-results',
  use: {
    baseURL: BASE,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 15000,
    navigationTimeout: 30000,
    ignoreHTTPSErrors: true,
    launchOptions: { headless: !headed }
  },
  projects: [
    {
      name: 'api',
      testMatch: /tests\/api\/.*\.spec\.js/,
      use: { ...devices['Desktop Chrome'] }
    },
    pick('pc') && {
      name: 'pc',
      testMatch: /tests\/ui-pc\/.*\.spec\.js/,
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } }
    },
    pick('admin') && {
      name: 'admin',
      testMatch: /tests\/ui-admin\/.*\.spec\.js/,
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } }
    },
    pick('mobile') && {
      name: 'mobile',
      testMatch: /tests\/ui-mobile\/.*\.spec\.js/,
      // 统一用已安装的 chromium，配移动视口/UA（与既有 .cjs 审计脚本一致），避免依赖未安装的 webkit。
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 390, height: 844 },
        isMobile: true,
        hasTouch: true,
        userAgent:
          'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1'
      }
    },
    {
      name: 'gate',
      testMatch: /tests\/gate\/.*\.spec\.js/,
      use: { ...devices['Desktop Chrome'] }
    }
  ].filter(Boolean)
})

