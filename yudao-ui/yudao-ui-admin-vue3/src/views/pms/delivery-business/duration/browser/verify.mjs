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
  '@/api/pms/engineering/construction-plan',
  '@/api/bpm/processInstance',
  '@/store/modules/user',
  '@/hooks/web/useMessage',
  '@/utils/dict',
  '@/components/PmsFileArtifact',
  'vue-router'
].map((find) => ({
  find: new RegExp(`^${find.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`),
  replacement: fixtureApi
}))
const output = resolve(uiRoot, '../../.run/delivery-demo/duration-browser')
await mkdir(output, { recursive: true })
const server = await createServer({
  ...config,
  configFile: false,
  root: dependencyRoot,
  resolve: {
    ...config.resolve,
    alias: [
      {
        find: './ProjectDurationHistoryDrawer.vue',
        replacement: resolve(browserRoot, 'HistoryFixture.vue')
      },
      ...mocks,
      ...config.resolve.alias
    ]
  },
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
  await page.getByRole('button', { name: '录入首次工期', exact: true }).click()
  await page.getByRole('dialog', { name: '录入项目工期' }).waitFor()
  const dialog = page.getByRole('dialog', { name: '录入项目工期' })
  const dates = dialog.locator('.el-date-editor input')
  await dates.nth(0).fill('2026-09-01')
  await dates.nth(0).press('Tab')
  await dates.nth(1).fill('2026-09-10')
  await dates.nth(1).press('Tab')
  await page.getByRole('button', { name: '保存并生效', exact: true }).click()
  await page.getByRole('button', { name: '发起工期变更', exact: true }).waitFor()
  const calls = () => page.getByTestId('calls').textContent().then(JSON.parse)
  const created = (await calls()).find((call) => call.name === 'createInitial')
  assert.deepEqual(created.payload, {
    projectId: 1,
    expectedProjectVersion: 7,
    calculationBasis: 'DATE_RANGE',
    startDate: '2026-09-01',
    endDate: '2026-09-10'
  })
  await page.getByRole('button', { name: '项目2', exact: true }).click()
  await page.getByRole('button', { name: '编辑草稿', exact: true }).waitFor()
  await page.locator('aside .el-switch').click()
  assert.equal(await page.getByRole('button', { name: '编辑草稿', exact: true }).count(), 0)
  assert.equal(await page.getByRole('button', { name: '提交审批', exact: true }).count(), 0)
  assert.equal(await page.getByRole('button', { name: '查看历史', exact: true }).count(), 1)
  for (const width of [320, 768, 1024, 1440]) {
    await page.setViewportSize({ width, height: 1000 })
    await page.screenshot({ path: resolve(output, `readonly-${width}.png`), fullPage: true })
    assert.equal(
      await page.evaluate(() => document.documentElement.scrollWidth > innerWidth),
      false,
      `overflow at ${width}`
    )
  }
  await page.getByRole('button', { name: '项目1', exact: true }).click()
  await page.getByRole('button', { name: '项目2', exact: true }).click()
  await page.getByText('V2', { exact: true }).waitFor()
  const count = (await calls()).filter((call) => call.name === 'getByProjectId').length
  await page.getByRole('button', { name: '无效项目', exact: true }).click()
  await page.getByText('项目上下文无效，未查询工期。', { exact: true }).waitFor()
  assert.equal((await calls()).filter((call) => call.name === 'getByProjectId').length, count)
  assert.equal(await page.getByRole('button', { name: '录入首次工期', exact: true }).count(), 0)
  assert.deepEqual(errors, [])
  assert.deepEqual(prohibited, [])
  console.log(
    `DURATION_COMPONENT_PASS: original initial form and payload, readonly, project switch, invalid context, four widths. ${output}`
  )
} finally {
  await browser?.close()
  await server.close()
}
