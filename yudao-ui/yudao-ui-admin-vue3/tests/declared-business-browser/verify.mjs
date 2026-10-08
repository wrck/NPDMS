import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { createWriteStream } from 'node:fs'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { spawn } from 'node:child_process'
import { createServer } from 'vite'
import { createServer as createPortReservation } from 'node:net'
import config, { uiRoot } from './config.mjs'

assert.equal(process.env.NPDMS_DECLARED_EXCLUSIVE, 'true', 'Create and verify the exclusive MySQL/Redis fixture first')
const browserRequire = process.env.NPDMS_BROWSER_PACKAGES
  ? createRequire(resolve(process.env.NPDMS_BROWSER_PACKAGES, '../package.json')) : createRequire(import.meta.url)
const { chromium } = browserRequire('playwright')
const repoRoot = resolve(uiRoot, '../..')
const output = resolve(repoRoot, '.run/cloud-20261006/browser')
await mkdir(output, { recursive: true })
await writeFile(resolve(output, 'result.json'), JSON.stringify({ passed: false, status: 'RUNNING', startedAt: new Date().toISOString() }) + '\n')
const log = createWriteStream(resolve(output, 'backend.log'))
const args = ['-B', ...(process.env.NPDMS_MAVEN_SETTINGS ? ['-s', process.env.NPDMS_MAVEN_SETTINGS] : []),
  '-pl', 'pms-module-platform', '-am', 'test',
  '-Dtest=DeclaredLoginRuntimePersistenceTest#browserRealLoginFormAndRefreshRecovery',
  '-Dnpdms.declared.exclusive=true', '-Dnpdms.declared.browserLogin=true', '-Dsurefire.failIfNoSpecifiedTests=false']
const backend = spawn('mvn', args, { cwd: repoRoot, stdio: ['ignore', 'pipe', 'pipe'], detached: process.platform !== 'win32' })
backend.stdout.pipe(log); backend.stderr.pipe(log)
const ended = new Promise(resolve => backend.on('close', code => resolve(code)))
let backendExit
void ended.then(code => { backendExit = code })
// Vite treats port 0 as its default port. Reserve an OS-selected port and fail if it is taken.
let reservation, server, browser, page
const errors = []
const pendingRequests = new Map()
try {
  reservation = createPortReservation()
  await new Promise((resolve, reject) => { reservation.once('error', reject); reservation.listen(0, '127.0.0.1', resolve) })
  const port = reservation.address().port
  await new Promise((resolve, reject) => reservation.close(error => error ? reject(error) : resolve()))
  reservation = undefined
  server = await createServer({ ...config, configFile: false, server: { ...config.server, port, strictPort: true } })
  const deadline = Date.now() + 180000
  while (true) {
    if (backendExit !== undefined) throw new Error(`Backend fixture exited early (${backendExit}); see backend.log`)
    try { if ((await fetch('http://127.0.0.1:27462/fixture/credentials')).ok) break } catch {}
    if (Date.now() > deadline) throw new Error('Backend fixture did not become ready')
    await new Promise(resolve => setTimeout(resolve, 500))
  }
  await server.listen()
  const origin = `http://127.0.0.1:${server.httpServer.address().port}`
  browser = await chromium.launch({ executablePath: process.env.NPDMS_BROWSER_EXECUTABLE || '/usr/bin/chromium', headless: true })
  page = await browser.newPage()
  page.setDefaultTimeout(20000)
  page.on('pageerror', error => errors.push(error.message))
  let commands = 0, recoveries = 0
  page.on('request', request => {
    pendingRequests.set(request, request.url())
    if (/\/operations\/[^/]+(?:\?|$)/.test(request.url()) && request.method() === 'POST') commands++
    if (request.url().includes('/receipt?')) recoveries++
  })
  page.on('requestfinished', request => pendingRequests.delete(request))
  page.on('requestfailed', request => pendingRequests.delete(request))
  await page.goto(`${origin}/tests/declared-business-browser/index.html`, { waitUntil: 'domcontentloaded' })
  await page.getByTestId('login').click()
  await page.getByTestId('status').filter({ hasText: 'authenticated' }).waitFor()
  let responseLost
  const lostResponse = new Promise((resolve, reject) => {
    responseLost = { resolve, reject }
  })
  await page.route('**/operations/create', async route => {
    try {
    const committed = await route.fetch()
    assert.equal((await committed.json()).code, 0)
    await route.abort('connectionreset')
    responseLost.resolve()
    } catch (error) { responseLost.reject(error) }
  }, { times: 1 })
  await page.getByTestId('create').click()
  let responseDeadline
  try {
    await Promise.race([lostResponse, new Promise((_, reject) => {
      responseDeadline = setTimeout(() => reject(new Error('The create response was not committed and dropped')), 20000)
    })])
  } finally { clearTimeout(responseDeadline) }
  await page.waitForFunction(() => !document.querySelector('[data-testid="create"]').disabled)
  await page.getByTestId('pending').filter({ hasText: 'pending' }).waitFor()
  const locators = await page.evaluate(() => Object.entries(sessionStorage).filter(([key]) => key.startsWith('pms-business-intent:')))
  assert.equal(locators.length, 1)
  assert.ok(!JSON.stringify(locators).includes('private initial content'))
  assert.ok(!JSON.stringify(locators).includes('DO_NOT_PERSIST'))
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.getByTestId('recover').waitFor()
  await page.getByTestId('recover').click()
  await page.getByTestId('status').filter({ hasText: 'recovered' }).waitFor()
  assert.equal(commands, 1)
  assert.equal(recoveries, 1)
  assert.equal(await page.getByTestId('pending').textContent(), 'none')
  assert.ok(!(await page.getByTestId('editable-form').textContent()).includes('DO_NOT_PERSIST'))
  const field = label => page.getByTestId('editable-form').locator('.el-form-item').filter({ hasText: label }).locator('input').first()
  await field('Heading').fill('Browser form saved')
  await field('Detail').fill('Required extension value')
  await page.getByTestId('editable-form').locator('.el-checkbox').filter({ hasText: /^A$/ }).click()
  assert.equal(await page.getByTestId('editable-form').getByRole('checkbox', { name: 'A', exact: true }).isChecked(), true)
  await page.getByTestId('save').click()
  await page.getByTestId('status').filter({ hasText: 'saved' }).waitFor()
  const before = await page.evaluate(async () => {
    const response = await fetch('/fixture/counts'); return (await response.json()).data
  })
  assert.equal(before.it_declared_note, 1)
  assert.equal(before.plt_idempotency_record, 2)
  assert.equal(before.plt_operation_audit, 4)
  assert.equal(before.plt_outbox_event, 2)
  assert.equal(before.plt_entity_extension_value, 1)
  assert.equal(before.title, 'Browser form saved')
  const denial = await page.evaluate(async () => {
    const { entity, request } = window.declaredFixture
    const form = entity.formPresentation.value
    try {
      await request.post({ url: '/api/v1/pms/business-models/IT/declaredNote/operations/save',
        params: { entityId: entity.current.value.ref.entityId }, data: {
          idempotencyKey: 'browser-required-clear', concurrencyBasis: entity.current.value.concurrencyBasis,
          input: { title: 'must roll back', $extensions: { definitionRevisionId: String(form.layout.binding.extensionDefinitionRevisionId),
            expectedVersion: form.extensions.version, values: { detail: null } } }
        } })
      return 'ALLOWED'
    } catch (error) { return error.response.data.code }
  })
  assert.equal(denial, 1010004001, 'The server must reject the required extension value, not an unrelated failure')
  const after = await page.evaluate(async () => (await (await fetch('/fixture/counts')).json()).data)
  assert.deepEqual(after, before)
  await page.getByTestId('readonly').click()
  const runtime = page.getByTestId('runtime-view')
  await runtime.locator('.el-form-item').filter({ hasText: 'Heading' }).locator('input').waitFor()
  assert.equal(await runtime.getByRole('button', { name: '保存', exact: true }).isDisabled(), true)
  for (const input of await runtime.locator('input').all()) assert.equal(await input.isDisabled(), true)
  await page.screenshot({ path: resolve(output, 'readonly.png'), fullPage: true })
  assert.deepEqual(errors, [])
  await page.evaluate(() => fetch('/finish'))
  assert.equal(await ended, 0)
  const result = { passed: true, completedAt: new Date().toISOString(), origin, browser: 'Chromium', commands, successfulCommands: 2, requiredClearErrorCode: denial,
    recoveries, counts: after, pageErrors: errors,
    assertions: ['SQL/Redis login through production security filters', 'lost create response recovered by GET only',
      'no business input or Secret in recovery storage', 'actual form-create fixed/required/typed extension save',
      'explicit required clear rolls back all successful effects', 'actual declared view and generic Host enforce readonly'],
    limitations: ['MockMvc security transport, not full Tomcat', 'ProjectScopeApi is a deterministic fixture',
      'native Owner views and inactive delivery panel excluded', 'single browser; multiple browser concurrency not covered'] }
  await writeFile(resolve(output, 'result.json'), JSON.stringify(result, null, 2) + '\n')
  console.log(JSON.stringify(result, null, 2))
} catch (error) {
  const result = { passed: false, completedAt: new Date().toISOString(), error: String(error),
    pageErrors: errors, url: page?.url(), pendingRequests: [...pendingRequests.values()] }
  if (page) await page.screenshot({ path: resolve(output, 'failure.png'), timeout: 5000 }).catch(() => {})
  await writeFile(resolve(output, 'result.json'), JSON.stringify(result, null, 2) + '\n')
  throw error
} finally {
  if (reservation?.listening) await new Promise(resolve => reservation.close(resolve))
  if (browser) await browser.close()
  if (server) await server.close()
  if (backendExit === undefined) {
    if (process.platform === 'win32') backend.kill('SIGTERM')
    else process.kill(-backend.pid, 'SIGTERM')
    await ended
  }
  log.end()
}
