import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { mkdir, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { createServer as reservePort } from 'node:net'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'

const fixtureRoot = dirname(fileURLToPath(import.meta.url))
const uiRoot = resolve(fixtureRoot, '../..')
const output = resolve(uiRoot, '../../.run/cloud-20261006/file-reference-browser')
const browserRequire = process.env.NPDMS_BROWSER_PACKAGES
  ? createRequire(resolve(process.env.NPDMS_BROWSER_PACKAGES, '../package.json')) : createRequire(import.meta.url)
const { chromium } = browserRequire('playwright')
await mkdir(output, { recursive: true })
await writeFile(resolve(output, 'result.json'), JSON.stringify({ passed: false, status: 'RUNNING' }) + '\n')
const reservation = reservePort()
await new Promise((resolve, reject) => { reservation.once('error', reject); reservation.listen(0, '127.0.0.1', resolve) })
const port = reservation.address().port
await new Promise(resolve => reservation.close(resolve))
const server = await createServer({ configFile: false, root: uiRoot,
  plugins: [vue(), AutoImport({ imports: ['vue'], dts: false })],
  optimizeDeps: { entries: [resolve(fixtureRoot, 'index.html')] },
  cacheDir: resolve(output, 'vite-cache'), css: { postcss: { plugins: [] } },
  resolve: { alias: [
    { find: '@/config/axios', replacement: resolve(fixtureRoot, 'request.ts') },
    { find: '@/hooks/web/useMessage', replacement: resolve(fixtureRoot, 'message.ts') },
    { find: '@', replacement: resolve(uiRoot, 'src') }
  ] }, server: { host: '127.0.0.1', port, strictPort: true } })
let browser, page
const errors = [], requests = []
const key = { ownerContext: 'SOL', objectType: 'NATIVE_OWNER', objectId: '9007199254740997', purposeCode: 'EVIDENCE', referenceKey: 'fixed-slot' }
const reference = { ...key, artifactId: '9007199254740993', referenceId: '9007199254740995', versionNo: 1,
  referenceVersion: 0, scopeVersion: '9007199254740999', status: 'ACTIVE', sensitivityCode: 'INTERNAL' }
let exists = false
try {
  await server.listen()
  browser = await chromium.launch({ executablePath: process.env.NPDMS_BROWSER_EXECUTABLE || '/usr/bin/chromium', headless: true })
  page = await browser.newPage()
  page.setDefaultTimeout(15000)
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/v1/pms/**', async route => {
    const request = route.request(), url = new URL(request.url())
    requests.push({ method: request.method(), pathname: url.pathname, params: Object.fromEntries(url.searchParams) })
    let data
    if (url.pathname.endsWith('/file-references')) {
      assert.deepEqual(Object.fromEntries(url.searchParams), key)
      data = exists ? reference : null
    } else if (url.pathname.endsWith(':init-upload')) {
      const input = request.postDataJSON()
      assert.equal(input.objectId, key.objectId)
      assert.equal(input.referenceKey, key.referenceKey)
      assert.equal(input.modeCode, exists ? 'ADD_VERSION' : 'CREATE_ARTIFACT')
      if (exists) { assert.equal(input.artifactId, reference.artifactId); assert.equal(input.expectedReferenceVersion, reference.referenceVersion) }
      data = { artifactId: reference.artifactId, sessionId: '9007199254741011', expiresAt: '2026-10-06T10:00:00' }
    } else if (url.pathname.endsWith(':complete-upload')) {
      assert.equal(url.pathname, `/api/v1/pms/files/${reference.artifactId}:complete-upload`)
      assert.match(request.postData(), /9007199254741011/)
      if (exists) { reference.versionNo++; reference.referenceVersion++ }
      exists = true
      data = { ...reference, sha256: 'digest' }
    } else if (url.pathname === `/api/v1/pms/files/${reference.artifactId}`) {
      data = { artifactId: reference.artifactId, name: 'evidence.txt', categoryCode: 'EVIDENCE', ownerContext: key.ownerContext,
        lifecycleStatus: 'ACTIVE', artifactVersion: reference.versionNo, reference, allowedActions: [], createdAt: '2026-10-06T08:00:00' }
    } else throw new Error(`Unexpected file request: ${url.pathname}`)
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, data }) })
  })
  const url = `http://127.0.0.1:${port}/tests/file-reference-browser/index.html`
  await page.goto(url)
  await page.locator('input[type=file]').setInputFiles({ name: 'evidence.txt', mimeType: 'text/plain', buffer: Buffer.from('evidence') })
  await page.getByRole('button', { name: '上传并绑定', exact: true }).click()
  await page.getByTestId('status').filter({ hasText: 'registration rejected' }).waitFor()
  await page.reload()
  await page.getByTestId('identity').filter({ hasText: reference.artifactId }).waitFor()
  await page.getByRole('button', { name: '上传新版本', exact: true }).waitFor()
  await page.locator('input[type=file]').setInputFiles({ name: 'evidence.txt', mimeType: 'text/plain', buffer: Buffer.from('new evidence') })
  await page.getByRole('button', { name: '上传新版本', exact: true }).click()
  await page.getByTestId('status').filter({ hasText: 'registration rejected' }).waitFor()
  await page.reload()
  await page.getByText('V2', { exact: true }).waitFor()
  reference.status = 'DETACHED'
  reference.referenceVersion++
  await page.reload()
  await page.getByText('已解绑', { exact: true }).waitFor()
  assert.equal(await page.getByRole('button', { name: '解绑', exact: true }).count(), 0)
  assert.equal(await page.getByRole('button', { name: '下载', exact: true }).count(), 0)
  assert.equal(await page.getByRole('button', { name: '预览', exact: true }).count(), 0)
  assert.equal(await page.getByText(/单文件不超过 50MiB/).count(), 1)
  assert.deepEqual(errors, [])
  await page.screenshot({ path: resolve(output, 'detached.png'), fullPage: true })
  await writeFile(resolve(output, 'result.json'), JSON.stringify({ passed: true, cases: ['upload consumer rejection',
    'full-page stable-key recovery', 'exact string IDs', 'version-change recovery', 'detached state'],
    transport: 'Playwright file HTTP fixture; SQL/Owner registry verified separately by MySQL tests', requests, errors }, null, 2) + '\n')
} catch (error) {
  if (page) await page.screenshot({ path: resolve(output, 'failed.png'), fullPage: true }).catch(() => {})
  await writeFile(resolve(output, 'result.json'), JSON.stringify({ passed: false, error: String(error), requests, errors }, null, 2) + '\n')
  throw error
} finally { await browser?.close(); await server.close() }
