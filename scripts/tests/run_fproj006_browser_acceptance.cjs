const fs = require('node:fs')
const path = require('node:path')
const { chromium } = require('playwright')

const baseUrl = process.env.NPDMS_BROWSER_BASE_URL || 'http://127.0.0.1:18082'
const apiUpstream = process.env.NPDMS_BROWSER_API_UPSTREAM
const projectId = Number(process.env.NPDMS_FPROJ006_PROJECT_ID || 992002000030)
const username = process.env.NPDMS_BROWSER_USERNAME || 'admin'
const password = process.env.NPDMS_BROWSER_PASSWORD || 'admin123'
const outputDir = process.argv[2] || 'output/f-proj-006-v18/browser'

fs.mkdirSync(outputDir, { recursive: true })

;(async () => {
const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
if (apiUpstream) {
  await context.route('http://localhost:58080/admin-api/**', async (route) => {
    const originalUrl = new URL(route.request().url())
    const response = await route.fetch({
      url: `${apiUpstream}${originalUrl.pathname}${originalUrl.search}`
    })
    await route.fulfill({ response })
  })
}
const page = await context.newPage()
const consoleErrors = []
const pageErrors = []
const failedResponses = []
const governanceResponses = []

page.on('console', (message) => {
  if (message.type() === 'error') consoleErrors.push(message.text())
})
page.on('pageerror', (error) => pageErrors.push(error.message))
page.on('response', (response) => {
  const url = response.url()
  if (url.includes('/admin-api/') && response.status() >= 400) {
    failedResponses.push({ method: response.request().method(), status: response.status(), url })
  }
  if (/governance-guard|actions\/(rollback|close|reopen)|governance-history/.test(url)) {
    governanceResponses.push({
      method: response.request().method(),
      status: response.status(),
      path: new URL(url).pathname
    })
  }
})

const actionDialog = () => page.locator('.el-dialog').filter({ hasText: /回退项目|异常关闭项目|受控重开项目/ })
const formItem = (label) => actionDialog().locator('.el-form-item').filter({ hasText: label }).first()

async function chooseReason() {
  await formItem('原因编码').locator('.el-select').click()
  const option = page
    .locator('.el-select-dropdown__item:visible')
    .filter({ hasText: 'Browser Acceptance' })
  try {
    await option.waitFor({ timeout: 5000 })
  } catch {
    const optionTexts = await page.locator('.el-select-dropdown__item').allInnerTexts()
    throw new Error(`原因字典选项未渲染：${JSON.stringify(optionTexts)}`)
  }
  await option.click()
}

async function fillTextarea(label, value) {
  await formItem(label).locator('textarea').fill(value)
}

async function openAllowedAction(buttonName) {
  const button = page.getByRole('button', { name: buttonName, exact: true })
  if ((await button.count()) === 0) {
    const visibleButtons = await page.getByRole('button').allInnerTexts()
    const pageText = (await page.locator('body').innerText()).slice(0, 2000)
    throw new Error(`动作按钮未渲染：${buttonName}; buttons=${JSON.stringify(visibleButtons)}; body=${pageText}`)
  }
  const guardResponse = page.waitForResponse((response) =>
    response.url().includes('/governance-guard?') && response.request().method() === 'GET'
  )
  await button.click()
  const response = await guardResponse
  const responseBody = await response.json()
  if (responseBody.code !== 0) {
    throw new Error(`守卫接口失败：code=${responseBody.code}, msg=${responseBody.msg}`)
  }
  await actionDialog().waitFor()
  await actionDialog().locator('.el-alert').waitFor()
  const loadingMask = actionDialog().locator('.el-loading-mask')
  if (await loadingMask.isVisible()) await loadingMask.waitFor({ state: 'hidden' })
  const allowedMessage = actionDialog().getByText('守卫检查通过，可填写并提交动作')
  if (!(await allowedMessage.isVisible())) {
    throw new Error(
      `守卫未通过：${await actionDialog().innerText()}; failed=${JSON.stringify(failedResponses)}`
    )
  }
  const providerFactText = await actionDialog().getByText(/提供方事实/).locator('..').innerText()
  if (!providerFactText.includes('5 个')) {
    throw new Error(`守卫提供方事实数量异常：${providerFactText}`)
  }
}

async function submitAction(buttonName) {
  await actionDialog().getByRole('button', { name: buttonName, exact: true }).click()
  await page.getByText(`${buttonName.replace(/^确认/, '')}成功`, { exact: true }).waitFor()
  await actionDialog().waitFor({ state: 'hidden' })
}

try {
  await page.goto(`${baseUrl}/login`, { waitUntil: 'networkidle' })
  const usernameInput = page.locator('input[type="text"]:visible').first()
  await usernameInput.fill(username)
  await page.locator('input[type="password"]:visible').first().fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL((url) => !url.pathname.startsWith('/login'))

  const permissionSnapshot = await page.evaluate(async () => {
    const cached = localStorage.getItem('ACCESS_TOKEN')
    let accessToken = cached
    try {
      const item = JSON.parse(cached)
      accessToken = item?.v ? JSON.parse(item.v) : item
    } catch {}
    const response = await fetch('http://localhost:58080/admin-api/system/auth/get-permission-info', {
      headers: { Authorization: `Bearer ${accessToken}` }
    })
    const body = await response.json()
    const required = [
      'pms:project:governance:query',
      'pms:project:rollback',
      'pms:project:close',
      'pms:project:reopen'
    ]
    return {
      status: response.status,
      code: body.code,
      required: Object.fromEntries(required.map((permission) => [
        permission,
        body.data?.permissions?.includes(permission) || false
      ]))
    }
  })
  if (!Object.values(permissionSnapshot.required).every(Boolean)) {
    throw new Error(`治理权限未进入登录态：${JSON.stringify(permissionSnapshot)}`)
  }

  await page.goto(
    `${baseUrl}/pms/project-management/project-master-detail?projectId=${projectId}`,
    { waitUntil: 'networkidle' }
  )
  await page.getByRole('button', { name: '异常治理', exact: true }).click()
  await page.getByText('服务端守卫与权限是动作提交的唯一判断依据', { exact: true }).waitFor()

  const before = {
    lifecycleActive: await page.getByText('进行中', { exact: true }).isVisible(),
    historyEmpty: await page.getByText('暂无治理动作历史', { exact: true }).first().isVisible()
  }

  await openAllowedAction('回退至 S0')
  await chooseReason()
  await fillTextarea('原因说明', 'Task 10真实浏览器回退验收')
  await fillTextarea('重新指派要求', '回退后由服务管理重新指派')
  await submitAction('确认回退')
  await page.getByText('共 1 条', { exact: true }).first().waitFor()

  await openAllowedAction('异常关闭')
  await chooseReason()
  await fillTextarea('原因说明', 'Task 10真实浏览器异常关闭验收')
  await fillTextarea('业务依据', '验收专用项目验证异常关闭与冻结快照')
  await submitAction('确认异常关闭')
  await page.getByRole('button', { name: '受控重开', exact: true }).waitFor()
  await page.getByText('共 2 条', { exact: true }).first().waitFor()

  await openAllowedAction('受控重开')
  await chooseReason()
  await fillTextarea('原因说明', 'Task 10真实浏览器受控重开验收')
  await formItem('异常关闭快照').locator('.el-select').click()
  await page.locator('.el-select-dropdown__item:visible').filter({ hasText: 'BROWSER_ACCEPTANCE' }).click()
  await submitAction('确认受控重开')
  await page.getByText('共 3 条', { exact: true }).first().waitFor()

  await page.reload({ waitUntil: 'networkidle' })
  await page.getByRole('button', { name: '异常治理', exact: true }).click()
  await page.getByText('服务端守卫与权限是动作提交的唯一判断依据', { exact: true }).waitFor()
  await page.getByText('共 3 条', { exact: true }).first().waitFor()
  await page.getByRole('button', { name: '异常关闭', exact: true }).waitFor()

  const historyRows = await page.locator('.desktop-list .el-table__body-wrapper tbody tr').allInnerTexts()
  const screenshot = path.resolve(outputDir, 'fproj006-task10-positive-chain.png')
  await page.screenshot({ path: screenshot, fullPage: true })

  const result = {
    capturedAt: new Date().toISOString(),
    browserVersion: browser.version(),
    baseUrl,
    apiUpstream: apiUpstream || 'http://localhost:58080',
    projectId,
    before,
    actions: ['ROLLBACK', 'EXCEPTION_CLOSE', 'REOPEN'],
    finalLifecycleActive: await page.getByText('进行中', { exact: true }).isVisible(),
    historyCount: historyRows.length,
    historyRows,
    governanceResponses,
    consoleErrors,
    pageErrors,
    failedResponses,
    screenshot,
    pass:
      before.lifecycleActive &&
      before.historyEmpty &&
      historyRows.length === 3 &&
      consoleErrors.length === 0 &&
      pageErrors.length === 0 &&
      failedResponses.length === 0
  }
  fs.writeFileSync(
    path.join(outputDir, 'fproj006-task10-positive-chain.json'),
    `${JSON.stringify(result, null, 2)}\n`
  )
  console.log(JSON.stringify(result, null, 2))
  if (!result.pass) process.exitCode = 1
} finally {
  if (apiUpstream) await context.unrouteAll({ behavior: 'wait' })
  await browser.close()
}
})().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
