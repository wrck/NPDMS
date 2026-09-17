import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'

// CI installs this pinned browser driver outside the application dependency tree.
// For local use set PLAYWRIGHT_PACKAGE_ROOT to a directory containing playwright.
const driverRoot = process.env.PLAYWRIGHT_PACKAGE_ROOT
if (!driverRoot) throw new Error('Set PLAYWRIGHT_PACKAGE_ROOT to the isolated Playwright installation')
const requireDriver = createRequire(`${driverRoot}/package.json`)
const { chromium } = requireDriver('playwright')
const artifactDir = fileURLToPath(new URL('../../../artifacts/lowcode-browser/', import.meta.url))
await mkdir(artifactDir, { recursive: true })
const server = await createServer({ configFile: fileURLToPath(new URL('./vite.config.mjs', import.meta.url)) })
await server.listen()
const browser = await chromium.launch({ headless: true })
const results = []

async function scenario(name, params, fn) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } })
  await context.tracing.start({ screenshots: true, snapshots: true, sources: true })
  const page = await context.newPage()
  page.setDefaultTimeout(12000)
  const errors = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (message) => { if (message.type() === 'error') errors.push(message.text()) })
  const started = Date.now()
  let failed = false
  try {
    await page.goto(`http://127.0.0.1:4173/tests/lowcode/browser/index.html?${new URLSearchParams(params)}`)
    await page.waitForFunction(() => !!window.lowcodeFixture)
    await fn(page)
    assert.deepEqual(errors, [], 'Browser must not raise runtime or console errors')
    results.push({ name, status: 'passed', durationMs: Date.now() - started })
    console.log(`PASS ${name}`)
  } catch (error) {
    failed = true
    results.push({ name, status: 'failed', message: error.stack || String(error), errors, durationMs: Date.now() - started })
    await page.screenshot({ path: `${artifactDir}/${name}.png`, fullPage: true })
    console.error(`FAIL ${name}: ${error.message}`)
  } finally {
    await context.tracing.stop(failed ? { path: `${artifactDir}/${name}.zip` } : {})
    await context.close()
  }
}
const snapshot = (page) => page.evaluate(() => window.lowcodeFixture.snapshot())
const call = (page, method, value) => page.evaluate(({ method, value }) => window.lowcodeFixture[method](value), { method, value })

try {
  for (const version of ['v1', 'v2']) {
    await scenario(`${version}-validate-submit-reset`, { version }, async (page) => {
      const title = page.getByPlaceholder('Enter title')
      await title.waitFor()
      await call(page, 'submit')
      let state = await snapshot(page)
      assert.equal(state.events.filter((event) => event.type === 'submit').length, 0)
      assert.equal(state.events.filter((event) => event.type === 'invalid').length, 1)
      await title.fill('edited-title')
      await title.blur()
      await call(page, 'submit')
      state = await snapshot(page)
      const submit = state.events.filter((event) => event.type === 'submit')
      assert.equal(submit.length, 1)
      assert.equal(submit[0].value.title, 'edited-title')
      assert.equal(submit[0].value.hidden, 'secret')
      assert.equal(submit[0].value.businessId, 42)
      await call(page, 'reset')
      await call(page, 'clear')
      assert.equal(await title.inputValue(), '')
      assert.equal((await snapshot(page)).data.note, 'default-note')
      assert.equal((await snapshot(page)).data.businessId, 42)
    })
    await scenario(`${version}-backfill-disabled`, { version }, async (page) => {
      const title = page.getByPlaceholder('Enter title')
      await title.fill('local')
      await call(page, 'setModel', { title: 'server', count: 0, enabled: false })
      await page.waitForFunction(() => window.lowcodeFixture.snapshot().data.title === 'server')
      assert.equal(await title.inputValue(), 'server')
      const state = await snapshot(page)
      assert.equal(state.data.note, 'initial-note')
      assert.equal(state.data.count, 0)
      assert.equal(state.data.enabled, false)
      await call(page, 'setDisabled', true)
      await page.waitForFunction(() => document.querySelector('input[placeholder="Enter title"]')?.disabled)
      assert.equal(await title.isDisabled(), true)
    })
    await scenario(`${version}-upload-contract`, { version, mode: 'upload' }, async (page) => {
      await page.getByText('PDF only', { exact: true }).waitFor()
      const file = page.locator('input[type="file"]')
      assert.equal(await file.getAttribute('accept'), '.pdf')
      assert.notEqual(await file.getAttribute('multiple'), null)
      await call(page, 'setDisabled', true)
      assert.equal(await page.getByRole('button', { name: '点击上传' }).isDisabled(), true)
    })
    for (const consumer of ['tab', 'related']) {
      await scenario(`${version}-consumer-${consumer}`, { version, mode: `consumer-${consumer}` }, async (page) => {
        const input = page.getByPlaceholder('Enter title')
        await input.waitFor()
        assert.equal(await input.inputValue(), 'nested-value')
        assert.equal(await page.locator('.low-code-form-renderer-v2').count(), version === 'v2' ? 1 : 0)
        await input.fill('nested-edit')
        await input.blur()
        assert.equal(await input.inputValue(), 'nested-edit')
      })
    }
  }
  await scenario('uncontrolled-version-roundtrip', { uncontrolled: 'true' }, async (page) => {
    const title = page.getByPlaceholder('Enter title')
    await title.fill('edited-in-v1')
    await call(page, 'setVersion', 'v2')
    await page.locator('.low-code-form-renderer-v2').waitFor()
    assert.equal(await title.inputValue(), 'edited-in-v1')
    await title.fill('edited-in-v2')
    await call(page, 'setVersion', 'v1')
    await page.locator('.low-code-form-renderer-v2').waitFor({ state: 'detached' })
    assert.equal(await title.inputValue(), 'edited-in-v2')
  })
  await scenario('v2-custom-instance-isolation', { version: 'v2', mode: 'custom' }, async (page) => {
    const first = page.locator('[data-testid="picker-a"] input')
    const second = page.locator('[data-testid="picker-b"] input')
    await first.fill('alpha')
    await first.blur()
    await second.fill('beta')
    await second.blur()
    const state = await snapshot(page)
    assert.equal(state.data.title, 'alpha')
    assert.equal(state.other.title, 'beta')
    const event = state.events.find((entry) => entry.type === 'handler')
    assert.deepEqual(event.value, { payload: { id: 'alpha', label: 'picker-a' }, title: 'alpha' })
  })
  await scenario('v2-tabs-retain-fields', { version: 'v2', mode: 'tabs' }, async (page) => {
    const title = page.getByPlaceholder('Enter title')
    await title.fill('retained')
    await page.getByRole('tab', { name: 'Second' }).click()
    await page.getByPlaceholder('Enter note').fill('second-edit')
    await page.getByRole('tab', { name: 'First' }).click()
    assert.equal(await title.inputValue(), 'retained')
    assert.equal((await snapshot(page)).data.note, 'second-edit')
  })
  await scenario('v2-collapse-retain-fields', { version: 'v2', mode: 'collapse' }, async (page) => {
    const title = page.getByPlaceholder('Enter title')
    await title.fill('retained')
    await page.getByRole('button', { name: 'First' }).click()
    await page.getByRole('button', { name: 'First' }).click()
    assert.equal(await title.inputValue(), 'retained')
  })
} finally {
  await writeFile(`${artifactDir}/results.json`, JSON.stringify({
    scope: 'Real renderer and nested preview consumers; transport/store/registry boundaries are fixtures. Not application or database E2E.',
    results
  }, null, 2))
  await browser.close()
  await server.close()
}
const failed = results.filter((result) => result.status === 'failed')
console.log(`${results.length - failed.length}/${results.length} browser contract scenarios passed`)
if (failed.length) process.exitCode = 1
