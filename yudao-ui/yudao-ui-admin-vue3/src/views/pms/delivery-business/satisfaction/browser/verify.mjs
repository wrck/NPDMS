import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import config, { browserRoot, dependencies, dependencyRoot, uiRoot } from './config.mjs'

const browserRequire = process.env.NPDMS_BROWSER_PACKAGES
  ? createRequire(resolve(process.env.NPDMS_BROWSER_PACKAGES, '../package.json'))
  : dependencies
const { chromium } = browserRequire('playwright')
const { createServer } = await import(pathToFileURL(dependencies.resolve('vite')).href)
const fixtureApi = resolve(browserRoot, 'fixtureApi.ts')
const mocks = [
  '@/api/pms/project/satisfaction',
  '@/api/pms/platform/file',
  '@/hooks/web/useMessage',
  '@/utils/auth',
  '@/components/Qrcode'
].map((find) => ({ find, replacement: fixtureApi }))
const output = resolve(uiRoot, '../../.run/delivery-demo/satisfaction-browser')
await mkdir(output, { recursive: true })
const server = await createServer({
  ...config,
  configFile: false,
  // Resolve the existing Sass installation without changing either worktree's dependencies.
  root: dependencyRoot,
  resolve: { ...config.resolve, alias: [...mocks, ...config.resolve.alias] },
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
  const prohibited = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (message) => {
    if (['error', 'warning'].includes(message.type())) errors.push(message.text())
  })
  await page.route('**/*', (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (url.origin !== origin || url.pathname.startsWith('/api/') || request.method() !== 'GET') {
      prohibited.push(url.pathname)
      return route.abort()
    }
    return route.continue()
  })
  await page.goto(`${origin}/@fs/${browserRoot.replaceAll('\\', '/')}/index.html`)
  await page.waitForLoadState('networkidle')
  await page.getByRole('heading', { name: '项目满意度', exact: true }).waitFor()
  assert.equal(await page.getByRole('tab', { name: '问卷模板' }).count(), 0)
  const calls = () => page.getByTestId('calls').textContent().then(JSON.parse)
  assert.deepEqual(
    (await calls()).filter((call) => call.name.startsWith('list')).map((call) => call.projectId),
    [2, 2]
  )
  await page.getByRole('button', { name: '指派', exact: true }).click()
  await page.getByRole('dialog', { name: '指派采集责任人' }).waitFor()
  await page.getByTestId('dirty').filter({ hasText: '有未结束操作' }).waitFor()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await page.locator('aside .el-switch').click()
  assert.equal(await page.getByRole('button', { name: '指派', exact: true }).count(), 0)
  await page.getByRole('tab', { name: '判定结果' }).click()
  assert.equal(await page.getByRole('button', { name: '失效', exact: true }).count(), 0)
  assert.equal(await page.getByRole('button', { name: '下载文件', exact: true }).count(), 1)
  for (const width of [320, 768, 1024, 1440]) {
    await page.setViewportSize({ width, height: 1000 })
    await page.screenshot({ path: resolve(output, `readonly-${width}.png`), fullPage: true })
    assert.equal(
      await page.evaluate(() => document.documentElement.scrollWidth > innerWidth),
      false,
      `overflow at ${width}`
    )
  }
  await page.getByRole('button', { name: '切换至项目1（慢响应）', exact: true }).click()
  await page.getByRole('button', { name: '切换至项目2', exact: true }).click()
  await page.getByRole('cell', { name: '201', exact: true }).waitFor()
  const count = (await calls()).length
  await page.getByRole('button', { name: '无效项目', exact: true }).click()
  await page.getByText('项目上下文无效，未查询其他项目。', { exact: true }).last().waitFor()
  assert.equal((await calls()).length, count)
  await page.getByRole('button', { name: '独立管理入口', exact: true }).click()
  await page.getByRole('tab', { name: '问卷模板' }).waitFor()
  await page.getByRole('tab', { name: '采集任务' }).click()
  await page.getByPlaceholder('项目ID').first().fill('42')
  await page
    .locator('.el-tab-pane:visible')
    .getByRole('button', { name: '查询', exact: true })
    .click()
  await page.getByRole('cell', { name: '421', exact: true }).waitFor()
  await page.getByRole('button', { name: '指派', exact: true }).click()
  await page.getByRole('dialog', { name: '指派采集责任人' }).getByRole('spinbutton').fill('72')
  await page.getByRole('button', { name: '确认指派', exact: true }).click()
  assert.ok(
    (await calls()).some(
      (call) => call.name === 'assignTask' && call.projectId === 42 && call.taskId === 421
    )
  )
  assert.deepEqual(errors, [])
  assert.deepEqual(prohibited, [])
  console.log(
    `SATISFACTION_COMPONENT_PASS: contextual and standalone entry, readonly, project switching, mocked assign, 4 widths. ${output}`
  )
} finally {
  await browser?.close()
  await server.close()
}
