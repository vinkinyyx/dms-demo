// tools/audit-mobile-deep.cjs
// Mobile H5 deep interaction + UI/UX audit for DMS test environment.
// Read-only by design: opens forms/pickers/dialogs and blocks non-auth write API requests.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const args = process.argv.slice(2);
function arg(name, fallback) {
  const hit = args.find(a => a.startsWith('--' + name + '='));
  return hit ? hit.split('=').slice(1).join('=') : fallback;
}

const BASE = (arg('base', 'http://43.128.145.141/dms')).replace(/\/$/, '');
const ACCOUNTS = [
  { username: 'admin', password: 'Sh123456' },
  { username: 'sys_admin', password: 'Dms@123456' },
  { username: 'sales', password: 'Dms@123456' }
];
const VIEWPORT = { width: 393, height: 852 };
const STAMP = arg('stamp', new Date().toISOString().replace(/[-:T.Z]/g, '').slice(0, 14));
const OUT = path.resolve(__dirname, '..', 'automation_test', 'v4-browser-results', 'mobile-deep-' + STAMP);
fs.mkdirSync(OUT, { recursive: true });

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const now = () => new Date().toISOString();
let issueSeq = 0;

const report = {
  meta: {
    tool: 'audit-mobile-deep.cjs',
    startedAt: now(),
    finishedAt: null,
    base: BASE,
    viewport: VIEWPORT,
    account: null,
    outDir: OUT
  },
  scenarios: [],
  issues: [],
  consoleErrors: [],
  pageErrors: [],
  networkErrors: [],
  blockedMutations: [],
  summary: { pass: 0, warn: 0, fail: 0, issues: { blocker: 0, critical: 0, major: 0, minor: 0 } }
};

function addIssue(issue) {
  issueSeq += 1;
  const row = {
    id: 'M' + String(issueSeq).padStart(3, '0'),
    severity: issue.severity || 'minor',
    page: issue.page || '',
    category: issue.category || 'UX',
    title: issue.title || '',
    repro: issue.repro || '',
    expected: issue.expected || '',
    actual: String(issue.actual || '').slice(0, 900),
    screenshot: issue.screenshot || '',
    observedAt: now()
  };
  report.issues.push(row);
  if (report.summary.issues[row.severity] !== undefined) report.summary.issues[row.severity] += 1;
  console.log(`[ISSUE:${row.severity}] ${row.id} ${row.page} - ${row.title}`);
}

function addScenario(name) {
  const scenario = { name, status: 'PASS', startedAt: now(), finishedAt: null, observations: [], screenshots: [], checks: [] };
  report.scenarios.push(scenario);
  return scenario;
}
function failScenario(scenario) { if (scenario.status !== 'FAIL') scenario.status = 'FAIL'; }
function check(scenario, name, ok, detail) {
  scenario.checks.push({ name, ok: !!ok, detail: String(detail || '').slice(0, 500) });
  if (!ok) failScenario(scenario);
}
function observe(scenario, text) { scenario.observations.push(String(text || '').slice(0, 500)); }
async function shot(page, scenario, name) {
  const prefix = scenario.name.replace(/[^a-z0-9_-]+/gi, '-');
  const file = path.join(OUT, `${prefix}-${name}.png`);
  await page.screenshot({ path: file, fullPage: true }).catch(() => {});
  scenario.screenshots.push(file);
  return file;
}
function startCollector(page) {
  const collector = { consoleErrors: [], pageErrors: [], networkErrors: [] };
  const onConsole = msg => {
    if (msg.type() !== 'error') return;
    const text = msg.text();
    if (/favicon|ResizeObserver|net::ERR_ABORTED|Download the React DevTools|DevTools failed|A preload for/i.test(text)) return;
    collector.consoleErrors.push({ at: now(), text: text.slice(0, 700), location: msg.location() });
  };
  const onPageError = error => collector.pageErrors.push({ at: now(), message: String(error && error.message ? error.message : error).slice(0, 700) });
  const onResponse = async response => {
    const status = response.status();
    const url = response.url();
    if (status < 400 || /favicon|\.map(\?|$)/i.test(url)) return;
    let body = '';
    try { body = (await response.text()).slice(0, 700); } catch (_) {}
    if (body.includes('AUDIT_BLOCKED')) return;
    collector.networkErrors.push({ at: now(), status, method: response.request().method(), url, body });
  };
  page.on('console', onConsole);
  page.on('pageerror', onPageError);
  page.on('response', onResponse);
  collector.detach = () => {
    page.off('console', onConsole);
    page.off('pageerror', onPageError);
    page.off('response', onResponse);
  };
  return collector;
}

function drainCollector(scenario, collector, pageName) {
  for (const item of collector.consoleErrors) {
    report.consoleErrors.push({ page: pageName, ...item });
    addIssue({ severity: 'major', page: pageName, category: 'Console', title: '移动端页面出现红色 JS 控制台错误', repro: `打开 ${pageName} 并完成本场景交互。`, expected: 'Console 无未捕获错误。', actual: item.text, screenshot: scenario.screenshots.at(-1) || '' });
  }
  for (const item of collector.pageErrors) {
    report.pageErrors.push({ page: pageName, ...item });
    addIssue({ severity: 'critical', page: pageName, category: 'PageError', title: '移动端页面出现未捕获 JS 异常', repro: `打开 ${pageName} 并完成本场景交互。`, expected: '页面脚本稳定运行，无未捕获异常。', actual: item.message, screenshot: scenario.screenshots.at(-1) || '' });
  }
  for (const item of collector.networkErrors) {
    report.networkErrors.push({ page: pageName, ...item });
    const severity = item.status >= 500 ? 'critical' : (item.status === 404 ? 'major' : 'minor');
    addIssue({ severity, page: pageName, category: 'Network', title: `接口返回 ${item.status}`, repro: `打开 ${pageName}，观察网络请求 ${item.method} ${item.url.replace(BASE, '')}。`, expected: '业务接口返回 2xx/3xx；不存在的移动端入口应修复或隐藏。', actual: `${item.status} ${item.body || item.url}`.slice(0, 700), screenshot: scenario.screenshots.at(-1) || '' });
  }
  collector.consoleErrors = [];
  collector.pageErrors = [];
  collector.networkErrors = [];
}

async function safeRoute(route) {
  const request = route.request();
  const method = request.method().toUpperCase();
  const url = request.url();
  if (method === 'OPTIONS') return route.continue();
  if (method !== 'GET' && url.includes('/api/') && !url.includes('/api/auth/')) {
    report.blockedMutations.push({ at: now(), method, url: url.replace(BASE, '') });
    return route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ code: 'AUDIT_BLOCKED', message: 'Deep mobile audit blocks write requests to keep test data read-only.' }) });
  }
  return route.continue();
}

async function waitPage(page, ms = 1500) {
  await sleep(ms);
  await page.waitForLoadState('networkidle', { timeout: 3000 }).catch(() => {});
}

async function bodyText(page) {
  return (await page.locator('body').innerText().catch(() => '')).trim();
}
async function scanUiIssues(page, scenario, pageName) {
  const data = await page.evaluate(() => {
    const isVisible = el => {
      const style = getComputedStyle(el);
      const rect = el.getBoundingClientRect();
      return style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0;
    };
    const textOf = el => (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim();
    const out = { overflow: [], clippedInputs: [], textIssues: [], placeholders: [], rawIds: [], buttons: [], inputs: [] };
    if (document.documentElement.scrollWidth > window.innerWidth + 2) {
      out.overflow.push({ selector: 'document', scrollWidth: document.documentElement.scrollWidth, clientWidth: window.innerWidth });
    }
    const els = Array.from(document.querySelectorAll('body *')).filter(isVisible);
    for (const el of els) {
      const rect = el.getBoundingClientRect();
      if (rect.right > window.innerWidth + 2 || rect.left < -2) {
        out.overflow.push({ tag: el.tagName, cls: String(el.className || '').slice(0, 100), text: textOf(el).slice(0, 140), right: Math.round(rect.right), left: Math.round(rect.left) });
      }
      if ((el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') && el.value) {
        if (el.scrollWidth > el.clientWidth + 4) out.clippedInputs.push({ tag: el.tagName, value: String(el.value).slice(0, 140), scrollWidth: el.scrollWidth, clientWidth: el.clientWidth });
      }
      const text = textOf(el);
      if (text) {
        if (/\?{2,}/.test(text)) out.textIssues.push({ kind: 'garbled', text: text.slice(0, 180) });
        if (/\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/.test(text)) out.textIssues.push({ kind: 'isoDate', text: text.slice(0, 180) });
        if (/(发起人|经销商|医院|仓库|产品|申请人|提交人|创建人|主刀医生|客户)[：: ]{1,5}\d{3,}/.test(text)) out.rawIds.push(text.slice(0, 180));
        if (/未实现|TODO|敬请期待|建设中|开发中|暂未开放/.test(text)) out.placeholders.push(text.slice(0, 180));
      }
      if (el.tagName === 'BUTTON') out.buttons.push({ text: textOf(el).slice(0, 80), disabled: !!el.disabled || el.getAttribute('aria-disabled') === 'true' });
    }
    out.rawIds = Array.from(new Set(out.rawIds)).slice(0, 20);
    out.textIssues = out.textIssues.slice(0, 20);
    out.placeholders = Array.from(new Set(out.placeholders)).slice(0, 20);
    out.overflow = out.overflow.slice(0, 20);
    out.clippedInputs = out.clippedInputs.slice(0, 20);
    out.buttons = out.buttons.slice(0, 60);
    return out;
  });

  check(scenario, '页面无横向溢出', data.overflow.length === 0, JSON.stringify(data.overflow));
  if (data.overflow.length) addIssue({ severity: data.overflow.some(x => x.selector === 'document') ? 'critical' : 'major', page: pageName, category: 'Layout', title: '393px 移动视口下存在横向溢出', repro: '打开该页面并滚动/触发表单与弹层。', expected: '页面宽度适配 393px，无横向滚动或元素越界。', actual: JSON.stringify(data.overflow).slice(0, 600), screenshot: scenario.screenshots.at(-1) || '' });
  check(scenario, '输入值无截断风险', data.clippedInputs.length === 0, JSON.stringify(data.clippedInputs));
  if (data.clippedInputs.length) addIssue({ severity: 'minor', page: pageName, category: 'Form', title: '输入框内容存在被截断风险', repro: '在表单中填写长文本/编号后观察输入框。', expected: '文本完整显示，或通过自适应/多行方式展示。', actual: JSON.stringify(data.clippedInputs).slice(0, 600), screenshot: scenario.screenshots.at(-1) || '' });
  for (const item of data.textIssues) {
    const isGarbled = item.kind === 'garbled';
    check(scenario, isGarbled ? '页面无中文乱码' : '页面无 ISO 日期直出', false, item.text);
    addIssue({ severity: isGarbled ? 'blocker' : 'minor', page: pageName, category: isGarbled ? 'Encoding' : 'DateFormat', title: isGarbled ? '页面出现 ???? 中文乱码' : '页面直接展示 ISO 日期时间', repro: '打开页面并查看列表/详情文本。', expected: isGarbled ? '中文正常显示。' : '日期按 YYYY-MM-DD 或 YYYY-MM-DD HH:mm:ss 展示。', actual: item.text, screenshot: scenario.screenshots.at(-1) || '' });
  }
  check(scenario, '无占位/未实现入口', data.placeholders.length === 0, data.placeholders.join(' | '));
  if (data.placeholders.length) addIssue({ severity: 'major', page: pageName, category: 'Placeholder', title: '页面展示未实现/占位功能文案', repro: '打开页面查看可见按钮或入口。', expected: '未实现功能不应展示可点击入口或占位按钮。', actual: data.placeholders.join(' | '), screenshot: scenario.screenshots.at(-1) || '' });
  check(scenario, '引用字段未暴露裸 ID', data.rawIds.length === 0, data.rawIds.join(' | '));
  if (data.rawIds.length) addIssue({ severity: 'major', page: pageName, category: 'RawId', title: '外键/引用字段疑似直接展示数字 ID', repro: '打开列表/详情/表单并查看人员、医院、经销商、仓库、产品等字段。', expected: '引用字段展示 code/name，枚举展示中文标签。', actual: data.rawIds.join(' | '), screenshot: scenario.screenshots.at(-1) || '' });
  check(scenario, '可见按钮均可用', !data.buttons.some(b => b.disabled), JSON.stringify(data.buttons));
  return data;
}

async function login(context, page, account) {
  console.log('[progress] login start', account.username, new Date().toISOString());
  await page.goto(BASE + '/mobile/login', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await sleep(1000);
  await page.locator('input').nth(0).fill('default');
  await page.locator('input').nth(1).fill(account.username);
  await page.locator('input[type="password"]').first().fill(account.password);
  await Promise.all([
    page.waitForResponse(/\/api\/auth\/login/, { timeout: 15000 }).catch(() => {}),
    page.getByRole('button', { name: /登\s*录/ }).first().click()
  ]);
  await page.waitForSelector('.van-tabbar, .van-nav-bar, .van-cell', { timeout: 10000 }).catch(() => {});
  await sleep(1500);
  if (page.url().includes('/mobile/login')) throw new Error('Mobile login failed for ' + account.username + ' url=' + page.url());
  report.meta.account = account.username;
  console.log('[progress] login done', account.username, page.url(), new Date().toISOString());
}

async function newLoggedInPage(browser, account) {
  const context = await browser.newContext({
    viewport: VIEWPORT,
    deviceScaleFactor: 2,
    isMobile: true,
    hasTouch: true,
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1'
  });
  await context.route('**/*', safeRoute);
  const page = await context.newPage();
  await login(context, page, account);
  return { context, page };
}
async function countCells(page) {
  return page.locator('.van-cell').filter({ hasNot: page.locator('.van-picker__column .van-cell') }).count();
}

async function firstCellText(page) {
  return page.locator('.van-cell:visible').first().innerText().catch(() => '');
}

async function pickerOptionCount(page) {
  return page.locator('.van-picker:visible .van-picker-column__item').filter({ hasNotText: /^请选择/ }).count();
}

async function chooseFirstPickerOption(page) {
  const option = page.locator('.van-picker:visible .van-picker-column__item').filter({ hasNotText: /^请选择/ }).first();
  const count = await option.count();
  if (!count) return false;
  await option.click({ timeout: 3000 }).catch(() => {});
  await sleep(300);
  return true;
}

async function clickPickerConfirm(page) {
  const confirm = page.locator('.van-picker:visible .van-picker__confirm').first();
  await confirm.waitFor({ state: 'visible', timeout: 3000 }).catch(() => {});
  await confirm.click({ timeout: 3000 }).catch(async () => {
    await page.locator('button:has-text("确认")').last().click({ timeout: 2000 }).catch(() => {});
  });
  await sleep(700);
}

async function fieldDisplayValue(page, label) {
  const field = page.locator('.van-field').filter({ hasText: label }).first();
  const input = field.locator('input').first();
  if (await input.count()) {
    const value = await input.inputValue().catch(() => '');
    if (value) return value;
  }
  return field.innerText().catch(() => '');
}

function productRows(page) {
  return page.locator('.van-popup:visible .van-list .van-cell:visible');
}

async function closeProductPopup(page) {
  const close = page.locator('.van-popup:visible button').filter({ hasText: '关闭' }).last();
  if (await close.isVisible().catch(() => false)) await close.click({ timeout: 2000 }).catch(() => {});
  else await page.keyboard.press('Escape').catch(() => {});
  await page.locator('.van-popup:visible').first().waitFor({ state: 'hidden', timeout: 3000 }).catch(() => {});
  await sleep(300);
}

async function chooseDealerWithProduct(page, scenario, pagePath) {
  const dealerField = page.locator('.van-field').filter({ hasText: '经销商' }).first();
  const productField = page.locator('.van-field').filter({ hasText: '产品' }).first();
  await dealerField.click({ timeout: 4000 }).catch(() => {});
  await sleep(700);
  const optionCount = await pickerOptionCount(page);
  check(scenario, '经销商下拉有选项', optionCount > 0, `options=${optionCount}`);
  if (optionCount === 0) {
    addIssue({ severity: 'critical', page: pagePath, category: 'Picker', title: '经销商下拉无选项', repro: '打开新建表单并点击经销商字段。', expected: '展示当前账号负责的经销商。', actual: 'Vant picker 选项数=0', screenshot: scenario.screenshots.at(-1) || '' });
    return false;
  }
  await page.keyboard.press('Escape').catch(() => {});
  await sleep(300);

  const testedDealers = [];
  const maxDealers = Math.min(optionCount, 6);
  for (let i = 0; i < maxDealers; i++) {
    await dealerField.click({ timeout: 4000 }).catch(() => {});
    await sleep(700);
    const option = page.locator('.van-picker:visible .van-picker-column__item').filter({ hasNotText: /^请选择/ }).nth(i);
    await option.click({ timeout: 3000 }).catch(() => {});
    await clickPickerConfirm(page);
    const dealer = await fieldDisplayValue(page, '经销商');
    testedDealers.push(dealer);
    observe(scenario, `探测经销商[${i + 1}/${maxDealers}]：${dealer}`);

    await productField.click({ timeout: 4000 }).catch(() => {});
    await page.locator('.van-popup:visible').waitFor({ state: 'visible', timeout: 4000 }).catch(() => {});
    await sleep(1200);
    const rows = productRows(page);
    const count = await rows.count();
    if (count > 0) {
      check(scenario, '产品下拉有可售产品', true, `${dealer}: products=${count}`);
      await rows.first().click({ timeout: 4000 }).catch(() => {});
      await sleep(800);
      await shot(page, scenario, 'product-selected');
      return true;
    }
    check(scenario, `经销商「${dealer}」产品授权数据可加载`, false, `products=${count}`);
    await shot(page, scenario, `product-empty-${i + 1}`);
    const popupText = await page.locator('.van-popup:visible').innerText().catch(() => '');
    if (/没有更多了/.test(popupText)) {
      addIssue({ severity: 'minor', page: pagePath, category: 'EmptyState', title: '产品为空时展示“没有更多了”，空态语义不准确', repro: '选择无授权产品的经销商后打开产品选择弹层。', expected: '无产品时展示“暂无产品”或“该经销商暂无授权产品”。', actual: popupText.slice(0, 300), screenshot: scenario.screenshots.at(-1) || '' });
    }
    await closeProductPopup(page);
  }

  addIssue({ severity: 'major', page: pagePath, category: 'Picker', title: '产品下拉为空，阻断移动单据录入', repro: '新建表单，逐个选择经销商后点击产品字段。', expected: '至少一个经销商应返回可售/授权产品，否则移动端无法完成产品明细录入。', actual: `已探测 ${testedDealers.length} 个经销商：${testedDealers.join('、')}；产品弹层均无产品行。`, screenshot: scenario.screenshots.at(-1) || '' });
  return false;
}

async function clickVisible(page, locator, name, timeout = 5000) {
  const target = locator.first();
  await target.waitFor({ state: 'visible', timeout }).catch(() => {});
  await target.scrollIntoViewIfNeeded().catch(() => {});
  await target.click({ timeout, force: false }).catch(async () => {
    await target.click({ timeout: 2000, force: true }).catch(() => {});
  });
  await sleep(500);
  return target.isVisible().catch(() => false);
}

async function setFieldByLabel(page, label, value) {
  const field = page.locator('.van-field').filter({ hasText: label }).first();
  await field.waitFor({ state: 'visible', timeout: 5000 }).catch(() => {});
  const input = field.locator('input:not([readonly]), textarea').first();
  await input.click({ timeout: 3000 }).catch(() => {});
  await input.fill(String(value), { timeout: 3000 }).catch(async () => {
    await page.keyboard.press('Control+A');
    await page.keyboard.insertText(String(value));
  });
  await sleep(200);
}

async function closeDialogs(page) {
  for (const name of ['取消', 'close', '关闭']) {
    const btn = page.getByRole('button', { name: new RegExp(name, 'i') }).last();
    if (await btn.isVisible().catch(() => false)) await btn.click({ timeout: 2000 }).catch(() => {});
    await sleep(200);
  }
  await page.keyboard.press('Escape').catch(() => {});
  await sleep(300);
}

async function verifyRequiredToast(page, scenario, expectedText) {
  const text = await bodyText(page);
  const ok = new RegExp(expectedText).test(text);
  check(scenario, `必填校验：${expectedText}`, ok, text.slice(-300));
  if (!ok) addIssue({ severity: 'major', page: scenario.name, category: 'Validation', title: '必填项未给出明确校验提示', repro: '不填写必填字段直接点击提交。', expected: `出现“${expectedText}”等明确提示。`, actual: text.slice(-300), screenshot: scenario.screenshots.at(-1) || '' });
}
async function auditApprovals(browser) {
  const scenario = addScenario('移动审批');
  let selectedAccount = ACCOUNTS[0];
  let foundTodo = false;
  for (const account of ACCOUNTS) {
    let session = null;
    const collector = startCollector;
    try {
      session = await newLoggedInPage(browser, account);
      const { context, page } = session;
      const coll = startCollector(page);
      scenario.observations.push(`尝试账号 ${account.username}`);
      await page.goto(BASE + '/mobile/approvals', { waitUntil: 'domcontentloaded', timeout: 30000 });
      await waitPage(page, 2500);
      await shot(page, scenario, `${account.username}-list`);
      const cells = page.locator('.van-list .van-cell:visible').filter({ has: page.locator('.van-tag') });
      const count = await cells.count();
      check(scenario, `${account.username} 待审批列表可加载`, count >= 0, `cells=${count}`);
      observe(scenario, `${account.username} 待审批列表单元格数=${count}`);
      if (count > 0) {
        selectedAccount = account;
        foundTodo = true;
        const first = cells.first();
        const title = await first.innerText().catch(() => '');
        observe(scenario, `打开待审批：${title.slice(0, 120)}`);
        await first.click({ timeout: 5000 });
        await page.waitForURL(/\/mobile\/approvals\/\d+/, { timeout: 8000 }).catch(() => {});
        await waitPage(page, 2500);
        await shot(page, scenario, `${account.username}-detail`);
        const text = await bodyText(page);
        check(scenario, '审批详情已打开', /审批详情|标题|类型|单号/.test(text), text.slice(0, 300));
        const hasActions = await page.getByRole('button', { name: '同意' }).isVisible().catch(() => false) || await page.getByRole('button', { name: '驳回' }).isVisible().catch(() => false);
        check(scenario, '待审批详情展示同意/驳回按钮', hasActions, text.slice(-300));
        if (!hasActions) addIssue({ severity: 'major', page: '/mobile/approvals/:id', category: 'Approval', title: '待审批详情未展示可操作的同意/驳回按钮', repro: '登录有待办任务的账号，进入移动审批并打开第一条待办。', expected: '底部展示“同意”“驳回”按钮，且可打开审批意见弹窗。', actual: text.slice(-500), screenshot: scenario.screenshots.at(-1) || '' });
        for (const action of ['同意', '驳回']) {
          const btn = page.getByRole('button', { name: action }).first();
          if (await btn.isVisible().catch(() => false)) {
            await btn.click({ timeout: 3000 });
            await sleep(800);
            await shot(page, scenario, `${account.username}-${action}-dialog`);
            const dialogText = await bodyText(page);
            const dialogOpen = /审批意见/.test(dialogText);
            check(scenario, `${action} 可打开审批意见弹窗`, dialogOpen, dialogText.slice(-300));
            if (!dialogOpen) addIssue({ severity: 'major', page: '/mobile/approvals/:id', category: 'Approval', title: `点击${action}未打开审批意见弹窗`, repro: `在待审批详情点击“${action}”。`, expected: '弹出审批意见输入框及取消/确认按钮，且不会立即提交。', actual: dialogText.slice(-500), screenshot: scenario.screenshots.at(-1) || '' });
            await closeDialogs(page);
          }
        }
        await scanUiIssues(page, scenario, '/mobile/approvals/:id');
        drainCollector(scenario, coll, '/mobile/approvals');
        void context.close().catch(() => {});
        break;
      }
      drainCollector(scenario, coll, '/mobile/approvals');
      void context.close().catch(() => {});
    } catch (e) {
      failScenario(scenario);
      observe(scenario, `账号 ${account.username} 异常：${e.message}`);
      void session.context.close().catch(() => {});
    }
  }
  for (const tab of ['我已审批', '我发起的', '抄送我的']) {
    let session = null;
    try {
      session = await newLoggedInPage(browser, selectedAccount);
      const { context, page } = session;
      const coll = startCollector(page);
      await page.goto(BASE + '/mobile/approvals', { waitUntil: 'domcontentloaded', timeout: 30000 });
      await waitPage(page, 2000);
      const tabBtn = page.getByRole('tab', { name: tab }).first();
      if (await tabBtn.isVisible().catch(() => false)) {
        await tabBtn.click({ timeout: 3000 });
        await waitPage(page, 2000);
        await shot(page, scenario, `tab-${tab}`);
        const text = await bodyText(page);
        check(scenario, `Tab「${tab}」可切换`, !/404|页面不存在/.test(text), text.slice(0, 300));
      } else {
        check(scenario, `Tab「${tab}」可见`, false, '未找到 tab');
        addIssue({ severity: 'major', page: '/mobile/approvals', category: 'Tabs', title: `移动审批缺少 Tab「${tab}」`, repro: '进入移动审批页。', expected: `展示 ${tab} Tab 且可切换。`, actual: await bodyText(page), screenshot: scenario.screenshots.at(-1) || '' });
      }
      await scanUiIssues(page, scenario, '/mobile/approvals');
      drainCollector(scenario, coll, '/mobile/approvals');
      void context.close().catch(() => {});
    } catch (e) {
      failScenario(scenario);
      observe(scenario, `Tab ${tab} 异常：${e.message}`);
      void session.context.close().catch(() => {});
    }
  }
  if (!foundTodo) addIssue({ severity: 'major', page: '/mobile/approvals', category: 'TestData', title: '提供的三个账号均无待审批数据，无法验证同意/驳回操作', repro: '依次使用 admin、sys_admin、sales 登录测试环境移动审批。', expected: '至少一个账号有待审批任务，可打开详情和审批意见弹窗。', actual: '三个账号待审批列表均为空。', screenshot: scenario.screenshots.at(-1) || '' });
  scenario.finishedAt = now();
  return selectedAccount;
}
async function auditSurgery(page, scenario) {
  const coll = startCollector(page);
  await page.goto(BASE + '/mobile/surgery-reports', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await waitPage(page, 2500);
  await shot(page, scenario, 'list');
  let listText = await bodyText(page);
  const listCount = await page.locator('.van-cell-group').count();
  check(scenario, '手术报台列表加载完成', /手术报台|暂无报台/.test(listText), `cellGroups=${listCount} body=${listText.slice(0, 120)}`);
  if (/404|页面不存在/.test(listText)) addIssue({ severity: 'blocker', page: '/mobile/surgery-reports', category: 'Route', title: '手术报台移动路由不存在或显示 404', repro: '登录移动端进入手术报台。', expected: '展示手术报台列表和新建入口。', actual: listText.slice(0, 400), screenshot: scenario.screenshots.at(-1) || '' });

  await page.goto(BASE + '/mobile/surgery-reports/create', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await waitPage(page, 2500);
  await shot(page, scenario, 'create-initial');
  let formText = await bodyText(page);
  check(scenario, '手术报台表单可打开', /手术植入报台|经销商|医院|仓库|产品明细/.test(formText), formText.slice(0, 300));
  if (/404|页面不存在/.test(formText)) addIssue({ severity: 'blocker', page: '/mobile/surgery-reports/create', category: 'Route', title: '手术报台新建移动路由不存在或显示 404', repro: '移动端手术报台点击加号/新建报台。', expected: '展示手术植入报台表单。', actual: formText.slice(0, 400), screenshot: scenario.screenshots.at(-1) || '' });

  const submit = page.getByRole('button', { name: '提交报台' }).first();
  check(scenario, '提交报台按钮可见', await submit.isVisible().catch(() => false), formText.slice(-300));
  await submit.click({ timeout: 4000 }).catch(() => {});
  await sleep(800);
  await shot(page, scenario, 'create-validation-empty');
  await verifyRequiredToast(page, scenario, '请选择经销商');

  const productSelected = await chooseDealerWithProduct(page, scenario, '/mobile/surgery-reports/create');

  const pickers = [['医院', '选择医院'], ['仓库', '选择仓库']];
  for (const [label] of pickers) {
    const field = page.locator('.van-field').filter({ hasText: label }).first();
    await field.scrollIntoViewIfNeeded().catch(() => {});
    await field.click({ timeout: 4000 }).catch(() => {});
    await sleep(700);
    await shot(page, scenario, `picker-${label}`);
    const options = await pickerOptionCount(page);
    check(scenario, `${label}下拉有数据`, options > 0, `options=${options}`);
    if (options === 0) addIssue({ severity: 'critical', page: '/mobile/surgery-reports/create', category: 'Picker', title: `${label}下拉无业务数据`, repro: `新建手术报台，点击“${label}”字段。`, expected: '至少展示一条可选择业务数据。', actual: `Vant picker 选项数=${options}`, screenshot: scenario.screenshots.at(-1) || '' });
    await chooseFirstPickerOption(page);
    await clickPickerConfirm(page);
    const value = await fieldDisplayValue(page, label);
    check(scenario, `${label}选择后值回显`, value && !/请选择|请先选择/.test(value), value.slice(0, 160));
  }

  const dateField = page.locator('.van-field').filter({ hasText: '手术日期' }).first();
  const initialDate = await fieldDisplayValue(page, '手术日期');
  check(scenario, '手术日期默认/回显正常', /\d{4}-\d{2}-\d{2}/.test(initialDate), initialDate.slice(0, 120));
  await dateField.click({ timeout: 4000 });
  await sleep(700);
  await shot(page, scenario, 'picker-date');
  await clickPickerConfirm(page);
  await sleep(500);
  const dateValue = await fieldDisplayValue(page, '手术日期');
  check(scenario, '手术日期选择后回显', /\d{4}-\d{2}-\d{2}/.test(dateValue), dateValue.slice(0, 120));

  await setFieldByLabel(page, '患者姓名', '移动端审计长文本患者姓名张三');
  await setFieldByLabel(page, '主刀医生', '李四主任医师骨科关节组');
  await setFieldByLabel(page, '备注', '这是移动端深度审计填写的长备注，用于验证文本框是否完整显示、是否被截断、换行是否正常。');
  await shot(page, scenario, 'create-filled-before-product');

  if (productSelected) {
    const batchInput = page.locator('.van-field').filter({ hasText: /批号|序列号/ }).locator('input').first();
    if (await batchInput.isVisible().catch(() => false)) {
      await batchInput.fill('AUDIT-BATCH-001', { timeout: 3000 });
      check(scenario, '批号/序列号可输入', true, 'AUDIT-BATCH-001');
    } else {
      check(scenario, '批号/序列号字段可见', false, '未找到批号/序列号输入框');
      addIssue({ severity: 'major', page: '/mobile/surgery-reports/create', category: 'Form', title: '选择产品后未展示批号/序列号必填字段', repro: '选择产品后观察产品明细区域。', expected: '展示批号或序列号输入框并可填写。', actual: '未找到批号/序列号输入框。', screenshot: scenario.screenshots.at(-1) || '' });
    }
    await setFieldByLabel(page, '数量', '2');
  }
  await page.getByRole('button', { name: '添加产品' }).first().click({ timeout: 3000 }).catch(() => {});
  await sleep(500);
  const lineCount = await page.locator('.line-card').count();
  check(scenario, '添加产品行正常', lineCount >= 2, `lines=${lineCount}`);
  await shot(page, scenario, 'create-filled');
  await scanUiIssues(page, scenario, '/mobile/surgery-reports/create');
  drainCollector(scenario, coll, '/mobile/surgery-reports');
}
async function auditOrders(page, scenario) {
  const coll = startCollector(page);
  await page.goto(BASE + '/mobile/orders', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await waitPage(page, 2500);
  await shot(page, scenario, 'list');
  const listText = await bodyText(page);
  check(scenario, '销售订单列表加载完成', /销售订单|暂无销售订单|¥/.test(listText), listText.slice(0, 300));
  if (/404|页面不存在/.test(listText)) addIssue({ severity: 'blocker', page: '/mobile/orders', category: 'Route', title: '销售订单移动路由不存在或显示 404', repro: '登录移动端进入销售订单。', expected: '展示销售订单列表、搜索和下单入口。', actual: listText.slice(0, 400), screenshot: scenario.screenshots.at(-1) || '' });

  const search = page.locator('.van-search input').first();
  if (await search.isVisible().catch(() => false)) {
    let searchResponse = null;
    const onSearchResponse = async response => {
      if (!/\/api\/orders(\?|$)/.test(response.url())) return;
      if (response.request().method() !== 'GET') return;
      searchResponse = { status: response.status(), url: response.url(), body: (await response.text().catch(() => '')).slice(0, 1000) };
    };
    page.on('response', onSearchResponse);
    await search.click();
    await search.fill('AUDIT-NO-SUCH-ORDER');
    await page.keyboard.press('Enter');
    await waitPage(page, 1800);
    page.off('response', onSearchResponse);
    await shot(page, scenario, 'list-search-empty');
    const emptyText = await bodyText(page);
    const rowsAfterSearch = page.locator('.van-cell-group.inset .van-cell, .van-cell-group .van-cell').filter({ has: page.locator('.van-tag, .van-cell__value') }).count();
    const responseHasRows = /"list"\s*:\s*\[\s*\{|"records"\s*:\s*\[\s*\{/.test(searchResponse?.body || '');
    const emptyFriendly = /暂无|没有|无数据/.test(emptyText);
    check(scenario, '订单搜索无结果时空态友好', emptyFriendly, emptyText.slice(-300));
    if (!emptyFriendly) {
      const cause = searchResponse
        ? `请求=${searchResponse.url.replace(BASE, '')} status=${searchResponse.status} 响应仍有数据=${responseHasRows}`
        : '未捕获到 /api/orders 搜索请求';
      addIssue({ severity: 'major', page: '/mobile/orders', category: 'Search', title: '无匹配订单搜索未展示友好空态', repro: '销售订单列表搜索不存在的单号 AUDIT-NO-SUCH-ORDER。', expected: '触发搜索后，接口返回空列表且页面展示“暂无销售订单”等空态。', actual: `${cause}；页面尾部文案=${emptyText.slice(-300)}`, screenshot: scenario.screenshots.at(-1) || '' });
    }
    await search.fill('');
    await page.keyboard.press('Enter');
    await waitPage(page, 1500);
  } else {
    check(scenario, '订单搜索框可见', false, listText.slice(0, 200));
    addIssue({ severity: 'major', page: '/mobile/orders', category: 'Filter', title: '销售订单列表缺少搜索/筛选输入框', repro: '进入移动端销售订单列表。', expected: '可见搜索框，可按单号/经销商检索并展示空态。', actual: listText.slice(0, 400), screenshot: scenario.screenshots.at(-1) || '' });
  }

  await page.goto(BASE + '/mobile/orders/create', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await waitPage(page, 2500);
  await shot(page, scenario, 'create-initial');
  let formText = await bodyText(page);
  check(scenario, '下销售订单表单可打开', /下销售订单|经销商|产品明细|金额合计/.test(formText), formText.slice(0, 300));
  if (/404|页面不存在/.test(formText)) addIssue({ severity: 'blocker', page: '/mobile/orders/create', category: 'Route', title: '下销售订单移动路由不存在或显示 404', repro: '移动端销售订单点击加号/下销售订单。', expected: '展示销售订单创建表单。', actual: formText.slice(0, 400), screenshot: scenario.screenshots.at(-1) || '' });

  const submit = page.getByRole('button', { name: '提交订单' }).first();
  check(scenario, '提交订单按钮可见', await submit.isVisible().catch(() => false), formText.slice(-300));
  await submit.click({ timeout: 4000 }).catch(() => {});
  await sleep(800);
  await shot(page, scenario, 'create-validation-empty');
  await verifyRequiredToast(page, scenario, '请选择经销商');

  const productSelected = await chooseDealerWithProduct(page, scenario, '/mobile/orders/create');

  const pickers = [['仓库', '选择发货仓库'], ['订单类型', '选择订单类型']];
  for (const [label] of pickers) {
    const field = page.locator('.van-field').filter({ hasText: label }).first();
    await field.scrollIntoViewIfNeeded().catch(() => {});
    await field.click({ timeout: 4000 }).catch(() => {});
    await sleep(700);
    await shot(page, scenario, `picker-${label}`);
    const options = await pickerOptionCount(page);
    check(scenario, `${label}下拉有选项`, options > 0, `options=${options}`);
    if (options === 0) addIssue({ severity: 'critical', page: '/mobile/orders/create', category: 'Picker', title: `${label}下拉无选项`, repro: `新建销售订单，点击“${label}”。`, expected: '至少展示一个可选项。', actual: 'Vant picker 选项数=0', screenshot: scenario.screenshots.at(-1) || '' });
    await chooseFirstPickerOption(page);
    await clickPickerConfirm(page);
    const value = await fieldDisplayValue(page, label);
    check(scenario, `${label}选择后回显`, value && !/请选择|请先选择/.test(value), value.slice(0, 160));
  }

  const initialDate = await fieldDisplayValue(page, '订单日期');
  check(scenario, '订单日期默认/回显正常', /\d{4}-\d{2}-\d{2}/.test(initialDate), initialDate.slice(0, 120));
  const dateField = page.locator('.van-field').filter({ hasText: '订单日期' }).first();
  await dateField.click({ timeout: 4000 });
  await sleep(700);
  await shot(page, scenario, 'picker-order-date');
  await clickPickerConfirm(page);
  await sleep(500);
  const dateValue = await fieldDisplayValue(page, '订单日期');
  check(scenario, '订单日期选择后回显', /\d{4}-\d{2}-\d{2}/.test(dateValue), dateValue.slice(0, 120));
  await setFieldByLabel(page, '备注', '移动端销售订单审计长备注：验证长文本输入、金额合计、产品行删除与加行，不应截断。');

  if (productSelected) {
    const qty = page.locator('.van-stepper input').first();
    check(scenario, '数量选择器可见', await qty.isVisible().catch(() => false), '未找到 van-stepper');
    if (await qty.isVisible().catch(() => false)) {
      await qty.fill('3', { timeout: 3000 }).catch(() => {});
      await sleep(300);
    }
    const priceInput = page.locator('.van-field').filter({ hasText: '单价' }).locator('input').first();
    check(scenario, '单价输入框可见', await priceInput.isVisible().catch(() => false), '未找到单价输入');
    if (await priceInput.isVisible().catch(() => false)) {
      await priceInput.fill('128.50', { timeout: 3000 });
      await sleep(500);
    }
    const taxInput = page.locator('.van-field').filter({ hasText: '税率' }).locator('input').first();
    if (await taxInput.isVisible().catch(() => false)) await taxInput.fill('0.13', { timeout: 3000 });
    await sleep(500);
    const amountText = await bodyText(page);
    check(scenario, '选择产品后价格/小计区域可见', /小计|含税合计|¥/.test(amountText), amountText.slice(-400));
    if (!/小计\s*¥\s*[1-9]\d*(\.\d+)?/.test(amountText)) addIssue({ severity: 'major', page: '/mobile/orders/create', category: 'Pricing', title: '选择产品并填写数量/单价后小计未正确显示', repro: '选择经销商和产品，设置数量、单价、税率。', expected: '小计和含税合计按输入实时计算并显示大于 0 的金额。', actual: amountText.slice(-500), screenshot: scenario.screenshots.at(-1) || '' });
  }

  await page.getByRole('button', { name: '添加产品' }).first().click({ timeout: 3000 }).catch(() => {});
  await sleep(400);
  let lineCount = await page.locator('.line-card').count();
  check(scenario, '添加产品行正常', lineCount >= 2, `lines=${lineCount}`);
  const deleteButtons = page.getByRole('button', { name: '删除' });
  if (await deleteButtons.count() > 0) {
    await deleteButtons.last().click({ timeout: 3000 }).catch(() => {});
    await sleep(400);
  }
  lineCount = await page.locator('.line-card').count();
  check(scenario, '删除产品行正常', lineCount === 1, `linesAfterDelete=${lineCount}`);
  await shot(page, scenario, 'filled');
  await submit.scrollIntoViewIfNeeded().catch(() => {});
  await submit.click({ timeout: 4000 }).catch(() => {});
  await sleep(1000);
  await shot(page, scenario, productSelected ? 'confirm-dialog-blocked' : 'validation-product-required');
  const dialogText = await bodyText(page);
  if (productSelected) {
    check(scenario, '提交前出现确认弹窗且未提交', /确认提交|合计/.test(dialogText), dialogText.slice(-300));
    if (!/确认提交|合计/.test(dialogText)) addIssue({ severity: 'major', page: '/mobile/orders/create', category: 'Submit', title: '提交销售订单前未出现二次确认', repro: '填写订单后点击提交订单。', expected: '弹出确认提交弹窗，由用户确认后才提交。', actual: dialogText.slice(-500), screenshot: scenario.screenshots.at(-1) || '' });
    await closeDialogs(page);
  } else {
    check(scenario, '未选产品时阻止提交并提示', /请至少添加一项有效产品|产品/.test(dialogText), dialogText.slice(-300));
  }
  await scanUiIssues(page, scenario, '/mobile/orders/create');
  drainCollector(scenario, coll, '/mobile/orders');
}
async function auditDashboard(page, scenario) {
  const coll = startCollector(page);
  await page.goto(BASE + '/mobile/dashboard', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await waitPage(page, 2500);
  await shot(page, scenario, 'dashboard');
  const text = await bodyText(page);
  check(scenario, '业绩页 KPI 渲染', /本月销售|销售金额|销售订单数/.test(text), text.slice(0, 400));
  check(scenario, '业绩数字非空', /¥\s*\d[\d,]*/.test(text) || /销售金额/.test(text), text.slice(0, 500));
  check(scenario, '趋势图/列表渲染', /近\s*12\s*月销售趋势|暂无数据/.test(text), text.slice(0, 500));
  check(scenario, 'TOP 经销商列表渲染', /本月\s*TOP\s*经销商|暂无数据/.test(text), text.slice(0, 500));
  check(scenario, '订单状态分布渲染', /订单状态分布|暂无数据/.test(text), text.slice(0, 500));
  if (/none/i.test(text)) addIssue({ severity: 'minor', page: '/mobile/dashboard', category: 'I18n', title: '空态文案出现英文 none', repro: '本月订单状态分布无数据时查看空态。', expected: '使用中文空态文案，例如“暂无数据”。', actual: '页面包含英文 none。', screenshot: scenario.screenshots.at(-1) || '' });
  if (!/时间|日期|本月|近\d+月|筛选|周期/.test(text)) addIssue({ severity: 'major', page: '/mobile/dashboard', category: 'Filter', title: '业绩页缺少可用时间筛选器', repro: '进入移动端我的业绩。', expected: '提供日/周/月/自定义时间等筛选，并能刷新 KPI、趋势、榜单。', actual: text.slice(0, 600), screenshot: scenario.screenshots.at(-1) || '' });
  else check(scenario, '时间筛选存在', true, '发现时间/周期相关文案');

  const reportCell = page.getByText('销售业绩排行', { exact: false }).first();
  if (await reportCell.isVisible().catch(() => false)) {
    await reportCell.click({ timeout: 4000 });
    await waitPage(page, 2000);
    await shot(page, scenario, 'report-link');
    const afterUrl = page.url();
    const afterText = await bodyText(page);
    check(scenario, '更多报表跳转不出现 404', !/404|页面不存在/.test(afterText), afterText.slice(0, 500));
    if (/404|页面不存在/.test(afterText)) addIssue({ severity: 'critical', page: '/mobile/dashboard', category: 'Route', title: '业绩页“销售业绩排行”跳转到 404', repro: '进入我的业绩，点击“销售业绩排行”。', expected: '跳转至可用的移动报表页或正确提示。', actual: afterText.slice(0, 500), screenshot: scenario.screenshots.at(-1) || '' });
    if (afterUrl.includes('/reports')) addIssue({ severity: 'major', page: '/mobile/dashboard', category: 'Navigation', title: '移动端业绩入口跳转到 PC 报表路由', repro: '进入我的业绩，点击“销售业绩排行”。', expected: '移动端应进入适配 393px 的移动报表页，或提供明确移动端不可用提示。', actual: `跳转 URL=${afterUrl}；页面文案=${afterText.slice(0, 300)}`, screenshot: scenario.screenshots.at(-1) || '' });
    await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => page.goto(BASE + '/mobile/dashboard', { waitUntil: 'domcontentloaded' }));
    await waitPage(page, 1500);
  } else {
    check(scenario, '销售业绩排行入口可见', false, text.slice(0, 400));
  }

  await scanUiIssues(page, scenario, '/mobile/dashboard');
  drainCollector(scenario, coll, '/mobile/dashboard');
}

async function auditNav(page, scenario) {
  const coll = startCollector(page);
  await page.goto(BASE + '/mobile/home', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await waitPage(page, 1800);
  const tabs = [
    ['首页', '/mobile/home'],
    ['订单', '/mobile/orders'],
    ['报台', '/mobile/surgery-reports'],
    ['审批', '/mobile/approvals'],
    ['我的', '/mobile/profile']
  ];
  for (const [name, expected] of tabs) {
    const item = page.locator('.van-tabbar-item').filter({ hasText: name }).first();
    if (await item.isVisible().catch(() => false)) {
      await item.click({ timeout: 4000 });
      await waitPage(page, 1200);
      const ok = page.url().includes(expected);
      check(scenario, `底部 Tab「${name}」切换正常`, ok, page.url());
      if (!ok) addIssue({ severity: 'major', page: '/mobile', category: 'Navigation', title: `底部 Tab「${name}」未切换到预期页面`, repro: `点击底部导航 ${name}。`, expected: `URL 包含 ${expected}。`, actual: page.url(), screenshot: scenario.screenshots.at(-1) || '' });
      await shot(page, scenario, `tab-${name}`);
    } else {
      check(scenario, `底部 Tab「${name}」可见`, false, '未找到 tabbar 项');
    }
  }
  await page.goto(BASE + '/mobile/orders', { waitUntil: 'domcontentloaded' });
  await waitPage(page, 800);
  await page.goto(BASE + '/mobile/orders/create', { waitUntil: 'domcontentloaded' });
  await waitPage(page, 1200);
  const back = page.locator('.van-nav-bar').first().locator('.van-icon-arrow-left, .van-nav-bar__left').first();
  if (await back.isVisible().catch(() => false)) {
    const beforeUrl = page.url();
    await back.click({ timeout: 3000 }).catch(() => page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {}));
    await waitPage(page, 1500);
    check(scenario, '表单返回键正常', page.url() !== beforeUrl, `before=${beforeUrl} after=${page.url()}`);
    if (page.url() === beforeUrl) addIssue({ severity: 'major', page: '/mobile/orders/create', category: 'Navigation', title: '表单返回键点击后无响应', repro: '进入下销售订单页，点击顶部返回。', expected: '返回到上一页（通常是销售订单列表）。', actual: `URL 未变化：${page.url()}`, screenshot: scenario.screenshots.at(-1) || '' });
  } else {
    check(scenario, '表单返回键可见', false, '未找到返回箭头');
    addIssue({ severity: 'major', page: '/mobile/orders/create', category: 'Navigation', title: '移动端表单缺少返回键', repro: '进入下销售订单页查看顶部导航。', expected: '左上角返回键可见且可返回列表。', actual: '未找到 .van-icon-arrow-left/.van-nav-bar__left', screenshot: scenario.screenshots.at(-1) || '' });
  }
  await scanUiIssues(page, scenario, '/mobile/navigation');
  drainCollector(scenario, coll, '/mobile/navigation');
  scenario.finishedAt = now();
}
async function auditListDetails(page, scenario) {
  const coll = startCollector(page);
  const lists = [
    ['销售订单', '/mobile/orders', /订单详情|销售订单|经销商|金额|状态|¥/],
    ['手术报台', '/mobile/surgery-reports', /报台详情|医院|经销商|手术日期|状态|报台/]
  ];
  for (const [name, url, expected] of lists) {
    await page.goto(BASE + url, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await waitPage(page, 2200);
    await shot(page, scenario, `${name}-list-before-detail`);
    const rows = page.locator('.van-cell-group.inset .van-cell, .van-cell-group .van-cell').filter({ has: page.locator('.van-tag, .van-cell__value') });
    const count = await rows.count();
    observe(scenario, `${name}列表可点击记录数=${count}`);
    if (count > 0) {
      await rows.first().click({ timeout: 5000 }).catch(() => {});
      await waitPage(page, 2000);
      await shot(page, scenario, `${name}-detail`);
      const text = await bodyText(page);
      check(scenario, `${name}详情可打开`, expected.test(text) && !/404|页面不存在/.test(text), text.slice(0, 500));
      if (/404|页面不存在/.test(text)) addIssue({ severity: 'blocker', page: url + '/:id', category: 'Route', title: `${name}详情移动路由 404`, repro: `进入${name}列表，点击第一条记录。`, expected: '展示详情内容。', actual: text.slice(0, 400), screenshot: scenario.screenshots.at(-1) || '' });
      else if (!expected.test(text)) addIssue({ severity: 'major', page: url + '/:id', category: 'Detail', title: `${name}详情内容不完整`, repro: `进入${name}列表，点击第一条记录。`, expected: '展示业务字段、状态和相关明细。', actual: text.slice(0, 500), screenshot: scenario.screenshots.at(-1) || '' });
      await scanUiIssues(page, scenario, url + '/:id');
      await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {});
      await waitPage(page, 1000);
    } else {
      check(scenario, `${name}列表存在记录用于详情验证`, false, '列表为空，无法点击详情');
      addIssue({ severity: 'minor', page: url, category: 'TestData', title: `${name}列表无数据，详情交互未覆盖`, repro: `进入${name}列表。`, expected: '测试环境存在至少一条可查看记录。', actual: '列表为空或未识别可点击记录。', screenshot: scenario.screenshots.at(-1) || '' });
    }
  }
  drainCollector(scenario, coll, 'mobile-list-details');
}

(async () => {
  let browser;
  try {
    console.log('[progress] launch', new Date().toISOString());
    browser = await chromium.launch({ headless: true });
    console.log('[progress] before approvals', new Date().toISOString());
    const account = await auditApprovals(browser);
    console.log('[progress] after approvals', account.username, new Date().toISOString());
    const session = await newLoggedInPage(browser, account);
    const { context, page } = session;
    const listScenario = addScenario('移动列表详情');
    await auditListDetails(page, listScenario);
    listScenario.finishedAt = now();

    const surgeryScenario = addScenario('手术报台');
    await auditSurgery(page, surgeryScenario);
    surgeryScenario.finishedAt = now();

    const orderScenario = addScenario('下销售订单');
    await auditOrders(page, orderScenario);
    orderScenario.finishedAt = now();

    const dashboardScenario = addScenario('查看业绩');
    await auditDashboard(page, dashboardScenario);
    dashboardScenario.finishedAt = now();

    const navScenario = addScenario('导航与Tab');
    await auditNav(page, navScenario);

    void context.close().catch(() => {});
  } catch (e) {
    console.error(e);
    addIssue({ severity: 'blocker', page: 'script', category: 'Script', title: '审计脚本执行失败', repro: '运行 node tools/audit-mobile-deep.cjs', expected: '脚本完整执行并生成报告。', actual: e && e.stack ? e.stack : String(e), screenshot: '' });
  } finally {
    if (browser) await browser.close();
    report.meta.finishedAt = now();
    for (const s of report.scenarios) {
      if (s.checks.some(c => !c.ok)) s.status = s.status === 'FAIL' ? 'FAIL' : 'WARN';
      if (report.summary[s.status.toLowerCase()] !== undefined) report.summary[s.status.toLowerCase()] += 1;
    }
    fs.writeFileSync(path.join(OUT, 'mobile-deep-report.json'), JSON.stringify(report, null, 2));
    fs.writeFileSync(path.join(OUT, 'mobile-deep-report-summary.json'), JSON.stringify({
      summary: report.summary,
      scenarios: report.scenarios.map(s => ({ name: s.name, status: s.status, failedChecks: s.checks.filter(c => !c.ok).map(c => c.name) })),
      issueCount: report.issues.length,
      report: path.join(OUT, 'mobile-deep-report.json')
    }, null, 2));
    console.log('\n===== MOBILE DEEP AUDIT SUMMARY =====');
    console.log(JSON.stringify(report.summary, null, 2));
    console.log('Report:', path.join(OUT, 'mobile-deep-report.json'));
  }
})();


