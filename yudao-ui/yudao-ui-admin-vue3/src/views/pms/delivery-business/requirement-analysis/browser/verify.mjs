import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import config, { dependencies, uiRoot } from './config.mjs'

const browserPackages = process.env.NPDMS_BROWSER_PACKAGES
const browserRequire = browserPackages
  ? createRequire(resolve(browserPackages, '../package.json'))
  : dependencies
const { chromium } = browserRequire('playwright')
const { createServer } = await import(pathToFileURL(dependencies.resolve('vite')).href)
const output = resolve(uiRoot, '../../.run/delivery-demo/browser')
await mkdir(output, { recursive: true })
const server = await createServer({
  ...config,
  configFile: false,
  server: { ...config.server, port: 0 }
})
let browser
try {
  await server.listen()
  const origin = `http://127.0.0.1:${server.httpServer.address().port}`
  browser = await chromium.launch({
    headless: true,
    channel: process.env.NPDMS_BROWSER_CHANNEL || 'msedge'
  })
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  page.setDefaultTimeout(10000)
  const errors = []
  const prohibitedRequests = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (message) => {
    if (message.type() === 'error' || message.type() === 'warning') errors.push(message.text())
  })
  await page.route('**/*', (route) => {
    const url = new URL(route.request().url())
    if (
      url.origin !== origin ||
      url.pathname.includes('/api/') ||
      route.request().method() !== 'GET'
    ) {
      prohibitedRequests.push(url.pathname)
      return route.abort()
    }
    return route.continue()
  })
  await page.goto(
    `${origin}/src/views/pms/delivery-business/requirement-analysis/browser/index.html`
  )
  await page.waitForLoadState('networkidle')
  await page.screenshot({ path: resolve(output, 'initial.png'), fullPage: true })
  assert.equal(await page.locator('.el-checkbox').count(), 11)
  await page.getByRole('button', { name: '校验表单' }).click()
  await page.getByTestId('validation').filter({ hasText: '必填项未完成' }).waitFor()
  for (const [label, value] of [
    ['1、项目背景', '<p>测试背景</p>'],
    ['2、项目目标', '<p>测试目标</p>'],
    ['3、网络拓扑', '<p>测试拓扑</p>']
  ]) {
    await page.locator('.el-form-item').filter({ hasText: label }).locator('textarea').fill(value)
  }
  for (const label of ['IPv6', 'MTU', 'SNMP', '堡垒机']) {
    await page.getByText(label, { exact: true }).click()
    assert.equal(await page.getByRole('checkbox', { name: label, exact: true }).isChecked(), true)
  }
  await page.getByLabel('流量现状：新建', { exact: true }).fill('0')
  await page.getByLabel('流量现状：并发', { exact: true }).fill('1000个连接')
  await page.getByLabel('流量现状：吞吐', { exact: true }).fill('1 Gbps')
  await page.getByLabel('管理IP', { exact: true }).fill('192.0.2.10')
  await page.getByLabel('公网IP资源', { exact: true }).fill('198.51.100.0/24')
  await page.locator('._fc-group-add').click()
  await page.getByLabel('设备名称', { exact: true }).fill('测试设备一')
  await page.getByLabel('序列号', { exact: true }).fill('0000123')
  await page.getByLabel('承载业务名称', { exact: true }).fill('业务A')
  await page.getByLabel('业务网段', { exact: true }).fill('192.0.2.0/24')
  await page.getByLabel('业务重要等级', { exact: true }).fill('按客户要求')
  await page.getByLabel('出入接口', { exact: true }).fill('GE0/1')
  await page.getByLabel('客户侧业务负责人', { exact: true }).fill('测试联系人')
  await page.getByLabel('业务明细备注', { exact: true }).fill('仅浏览器内存数据')
  await page.getByRole('button', { name: '校验表单' }).click()
  await page.getByTestId('validation').filter({ hasText: '校验通过' }).waitFor()
  await page.getByText('本地表单值（不保存到服务端）', { exact: true }).click()
  const values = () => page.getByTestId('values').textContent().then(JSON.parse)
  const filled = await values()
  assert.deepEqual(filled.TRANSMISSION_CURRENT_OPTIONS, ['IPv6', 'MTU'])
  assert.deepEqual(filled.OPERATIONS_MANAGEMENT_OPTIONS, ['SNMP', '堡垒机'])
  assert.equal(filled.TRAFFIC_NEW_CONNECTIONS, '0')
  assert.equal(filled.BUSINESS_DEVICE_DETAILS[0].serialNumber, '0000123')
  assert.equal(Object.keys(filled.BUSINESS_DEVICE_DETAILS[0]).length, 8)
  const groupControls = await page.locator('._fc-group-plus-minus').evaluateAll((elements) =>
    elements.map((element) => ({
      tag: element.tagName,
      role: element.getAttribute('role'),
      tabIndex: element.getAttribute('tabindex')
    }))
  )
  if (groupControls.some((element) => element.tag !== 'BUTTON' && element.tabIndex === null)) {
    console.log(
      'KNOWN_LIMITATION: FormCreate group add/remove controls lack keyboard focus; upstream/host acceptance remains open.'
    )
  }
  for (const width of [320, 768, 1024, 1440]) {
    await page.setViewportSize({ width, height: 1000 })
    await page.screenshot({ path: resolve(output, `filled-${width}.png`), fullPage: true })
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth > window.innerWidth
    )
    assert.equal(overflow, false, `page overflow at ${width}px`)
  }
  await page.locator('._fc-group').screenshot({ path: resolve(output, 'business-details.png') })
  await page.locator('._fc-group-btn:not(._fc-group-minus)').click()
  assert.equal(await page.getByLabel('设备名称', { exact: true }).count(), 2)
  await page.getByLabel('设备名称', { exact: true }).nth(1).fill('测试设备二')
  await page.locator('._fc-group-minus').first().click()
  assert.equal((await values()).BUSINESS_DEVICE_DETAILS[0].deviceName, '测试设备二')
  await page.locator('.el-switch').click()
  assert.equal(await page.getByLabel('流量现状：新建', { exact: true }).isDisabled(), true)
  assert.equal(await page.getByRole('checkbox', { name: 'IPv6', exact: true }).isDisabled(), true)
  assert.equal(await page.getByLabel('设备名称', { exact: true }).isDisabled(), true)
  const readonlyValues = await values()
  await page.locator('.el-switch').click()
  assert.deepEqual(await values(), readonlyValues)
  await page.locator('._fc-group-minus').click()
  assert.deepEqual((await values()).BUSINESS_DEVICE_DETAILS, [])
  for (const label of ['IPv6', 'MTU']) await page.getByText(label, { exact: true }).click()
  await page.getByLabel('公网IP资源', { exact: true }).fill('')
  assert.deepEqual((await values()).TRANSMISSION_CURRENT_OPTIONS, [])
  assert.equal((await values()).IP_PUBLIC_RESOURCES, '')
  await page.getByLabel('流量现状：新建', { exact: true }).focus()
  await page.keyboard.press('Tab')
  assert.equal(
    await page
      .getByLabel('流量现状：并发', { exact: true })
      .evaluate((element) => element === document.activeElement),
    true
  )
  assert.deepEqual(errors, [])
  assert.deepEqual(prohibitedRequests, [])
  console.log(
    `BROWSER_CONTENT_PASS: validation, choices, rows, clearing, readonly, keyboard input and 4 widths; no business/API requests. ${output}`
  )
} finally {
  await browser?.close()
  await server.close()
}
