import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
const root = process.env.PLAYWRIGHT_PACKAGE_ROOT
if (!root) throw new Error('Set PLAYWRIGHT_PACKAGE_ROOT')
const { chromium } = createRequire(`${root}/package.json`)('playwright')
const artifacts = fileURLToPath(new URL('../../../artifacts/lowcode-consumers/', import.meta.url))
await mkdir(artifacts, { recursive: true })
let store
function reset(version) {
  const config = { fields: [
    { id: 'name', prop: 'name', type: 'input', label: 'Name', placeholder: 'Enter name', required: true },
    { id: 'note', prop: 'note', type: 'input', label: 'Note', placeholder: 'Enter note' }
  ], entityCode: 'device', ...(version === 'v2' ? { rendererVersion: 'v2' } : {}) }
  store = { forms: { 1: { id: 1, code: 'form_device', name: 'Device form', status: 'PUBLISHED', version: 1, formConfig: JSON.stringify(config) } },
    records: { 1: { id: 1, name: 'Router', note: 'old-note' }, 2: { id: 2, name: 'Switch', note: 'second' } },
    requests: [], failWrite: false, permission: true, delayId: false, nextId: 3, nextFormId: 2 }
}
// This HTTP contract server persists data across page navigation/reloads in each case.
// It is not the Spring application or a substitute for database/tenant integration tests.
const server = await createServer({ configFile: fileURLToPath(new URL('./vite.config.mjs', import.meta.url)), plugins: [{
  name: 'consumer-http-contract', configureServer(vite) {
    vite.middlewares.use('/api/lowcode', async (req, res) => {
      const url = new URL(req.url, 'http://fixture')
      let text = ''
      for await (const chunk of req) text += chunk
      const body = text ? JSON.parse(text) : undefined
      const entry = { method: req.method, path: url.pathname, body, headers: req.headers }
      store.requests.push(entry)
      const send = (data, code = 0, msg = '') => { res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify({ code, msg, data })) }
      if (req.headers.authorization !== 'Bearer consumer-fixture' || req.headers['tenant-id'] !== '7') return send(null, 401, 'Wrong project login/tenant')
      if (url.pathname.startsWith('/collaboration/')) return send(req.method === 'GET' ? [] : null)
      if (url.pathname === '/permission/check') return send(store.permission)
      if (store.failWrite && ['POST', 'PUT'].includes(req.method)) { store.failWrite = false; return send(null, 409, 'fixture-write-rejected') }
      if (url.pathname === '/form' && req.method === 'POST') {
        const id = store.nextFormId++
        const form = { ...body, id, version: 1, status: 'DRAFT' }
        store.forms[id] = form; return send(form)
      }
      const formId = /^\/form\/(\d+)$/.exec(url.pathname)
      if (formId) {
        const id = Number(formId[1])
        if (req.method === 'PUT') store.forms[id] = { ...body, id, version: store.forms[id].version + 1 }
        return send(store.forms[id] || null)
      }
      if (/^\/form\/\d+\/publish$/.test(url.pathname)) { store.forms[Number(url.pathname.split('/')[2])].status = 'PUBLISHED'; return send(null) }
      if (url.pathname.startsWith('/form/code/')) return send(Object.values(store.forms).find((form) => form.code === decodeURIComponent(url.pathname.slice(11))) || null)
      if (url.pathname.startsWith('/list/code/')) return send({ name: 'Devices', listConfig: JSON.stringify({ entityCode: 'device', formCode: store.forms[1].code, columns: [ { prop: 'name', label: 'Name', type: 'text' } ], toolbar: [ { label: '新增', action: 'create', type: 'primary' } ], operations: [ { label: '编辑', action: 'edit' }, { label: '详情', action: 'view' } ] }) })
      if (url.pathname === '/data/device') {
        if (req.method === 'POST') { const id = store.nextId++; store.records[id] = { ...body, id }; return send(id) }
        return send({ records: Object.values(store.records), total: Object.keys(store.records).length })
      }
      const record = /^\/data\/device\/(\d+)$/.exec(url.pathname)
      if (record) {
        const id = Number(record[1]); const state = store
        if (state.delayId && id === 1 && req.method === 'GET') await new Promise((r) => setTimeout(r, 250))
        if (req.method === 'PUT') state.records[id] = { ...state.records[id], ...body, id }
        return send(req.method === 'PUT' ? null : state.records[id])
      }
      return send(null, 404, `Unexpected fixture endpoint: ${req.method} ${url.pathname}`)
    })
  }
}] })
await server.listen()
const browser = await chromium.launch({ headless: true })
const results = []
const writes = () => store.requests.filter((r) => ['POST', 'PUT'].includes(r.method) && !r.path.startsWith('/collaboration/'))
async function open(page, path) {
  await page.goto(`http://127.0.0.1:4174/tests/lowcode/consumers/index.html#${path}`)
  await page.waitForFunction(() => !!window.consumerRouter)
}
async function navigate(page, path) { await page.evaluate((path) => window.consumerRouter.push(path), path) }
async function scenario(name, version, fn) {
  reset(version)
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  const page = await context.newPage(); page.setDefaultTimeout(12000)
  const errors = []; page.on('pageerror', (e) => errors.push(e.message))
  page.on('console', (message) => { if (message.type() === 'error') errors.push(message.text()) })
  await context.tracing.start({ screenshots: true, snapshots: true, sources: true })
  let failed = false
  try {
    await fn(page)
    assert.deepEqual(errors, [], 'No browser runtime errors')
    results.push({ name, status: 'passed', httpRequests: store.requests.map(({ method, path }) => ({ method, path })) })
    console.log(`PASS ${name}`)
  } catch (e) {
    failed = true; results.push({ name, status: 'failed', error: e.stack, errors })
    console.error(`FAIL ${name}: ${e.stack}`); console.error(errors)
    await page.screenshot({ path: `${artifacts}/${name}.png`, fullPage: true })
    console.error((await page.locator('main').innerText().catch(() => '')).slice(0, 2500))
  } finally {
    await context.tracing.stop(failed ? { path: `${artifacts}/${name}.zip` } : {})
    await context.close()
  }
}
try {
  for (const version of ['v1', 'v2']) {
    await scenario(`${version}-designer-save-reopen-preview-publish`, version, async (page) => {
      await open(page, '/designer?id=1')
      const name = page.getByPlaceholder('请输入表单名称')
      await page.waitForFunction(() => document.querySelector('input[placeholder="请输入表单名称"]')?.value === 'Device form')
      await name.fill(''); await page.getByRole('button', { name: '发布', exact: true }).click()
      assert.equal(writes().length, 0)
      await name.fill('Updated form'); await page.getByRole('button', { name: '保存草稿' }).click()
      await page.getByText('保存成功', { exact: true }).waitFor()
      assert.equal(store.forms[1].version, 2)
      assert.equal(JSON.parse(store.forms[1].formConfig).rendererVersion, version === 'v2' ? 'v2' : undefined)
      assert.equal(JSON.parse(store.forms[1].formConfig).entityCode, 'device')
      await page.reload(); await page.getByPlaceholder('请输入表单名称').waitFor()
      await page.waitForFunction(() => document.querySelector('input[placeholder="请输入表单名称"]')?.value === 'Updated form')
      await page.getByRole('button', { name: '预览', exact: true }).click()
      await page.locator(version === 'v2' ? '.low-code-form-renderer-v2' : '.low-code-form-renderer').waitFor()
      await page.getByRole('button', { name: '退出预览', exact: true }).click()
      await page.getByRole('button', { name: '发布', exact: true }).click()
      await page.getByText('发布成功', { exact: true }).waitFor()
      assert.equal(store.forms[1].version, 3)
      assert.equal(writes().at(-1).path, '/form/1/publish')
    })
    await scenario(`${version}-list-create-edit-detail-persistence`, version, async (page) => {
      if (version === 'v2') store.forms[1].code = 'device_entry'
      await open(page, '/lowcode/list/list_device')
      await page.getByRole('button', { name: '新增', exact: true }).click()
      const name = page.getByPlaceholder('Enter name')
      await name.waitFor(); await page.getByRole('button', { name: '保存', exact: true }).click()
      assert.equal(writes().length, 0)
      await name.fill('New device'); await page.getByPlaceholder('Enter note').fill('new-note')
      await page.getByRole('button', { name: '保存', exact: true }).evaluate((button) => { button.click(); button.click() })
      await page.getByText('New device', { exact: true }).waitFor()
      assert.equal(writes().filter((r) => r.path === '/data/device').length, 1)
      let row = page.locator('tr').filter({ hasText: 'New device' })
      await row.getByRole('button', { name: '编辑', exact: true }).click()
      await page.getByPlaceholder('Enter note').waitFor()
      assert.equal(await page.getByPlaceholder('Enter note').inputValue(), 'new-note')
      await page.getByPlaceholder('Enter name').fill('Edited device'); await page.getByPlaceholder('Enter note').fill('')
      store.failWrite = true
      await page.getByRole('button', { name: '保存', exact: true }).click()
      await page.getByText('fixture-write-rejected', { exact: true }).waitFor()
      assert.equal(store.records[3].name, 'New device')
      assert.equal(await page.getByPlaceholder('Enter name').inputValue(), 'Edited device')
      await page.getByRole('button', { name: '保存', exact: true }).click()
      await page.getByText('Edited device', { exact: true }).waitFor()
      assert.equal(store.records[3].note, null)
      row = page.locator('tr').filter({ hasText: 'Edited device' })
      await row.getByRole('button', { name: '详情', exact: true }).click()
      await page.getByPlaceholder('Enter name').waitFor()
      assert.equal(await page.getByPlaceholder('Enter name').inputValue(), 'Edited device')
      assert.equal(await page.getByPlaceholder('Enter name').isDisabled(), true)
      assert.equal(await page.getByRole('button', { name: '保存', exact: true }).count(), 0)
      await page.reload(); await page.getByPlaceholder('Enter name').waitFor()
      assert.equal(await page.getByPlaceholder('Enter name').inputValue(), 'Edited device')
    })
    await scenario(`${version}-runtime-route-race-and-invalid-edit`, version, async (page) => {
      await open(page, '/lowcode/form/form_device?mode=edit&id=2')
      await page.getByPlaceholder('Enter name').waitFor()
      store.delayId = true
      await navigate(page, '/lowcode/form/form_device?mode=edit&id=1')
      await page.waitForTimeout(40)
      await navigate(page, '/lowcode/form/form_device?mode=edit&id=2')
      await page.getByPlaceholder('Enter name').waitFor(); await page.waitForTimeout(350)
      assert.equal(await page.getByPlaceholder('Enter name').inputValue(), 'Switch')
      await navigate(page, '/lowcode/form/form_device?mode=edit')
      await page.getByText('页面不存在', { exact: true }).waitFor()
      assert.equal(writes().length, 0)
    })
  }
  await scenario('designer-create-keeps-persisted-identity', 'v1', async (page) => {
    await open(page, '/designer')
    await page.getByPlaceholder('如：tpl_project_create').fill('form_new')
    await page.getByPlaceholder('请输入表单名称').fill('New form')
    await page.locator('.comp-item').filter({ hasText: '单行文本' }).click()
    await page.getByRole('button', { name: '保存草稿' }).click()
    await page.waitForURL(/id=2/)
    await page.getByRole('button', { name: '保存草稿' }).click()
    await page.getByText('保存成功', { exact: true }).waitFor()
    assert.equal(writes().filter((r) => r.method === 'POST' && r.path === '/form').length, 1)
    assert.equal(writes().filter((r) => r.method === 'PUT' && r.path === '/form/2').length, 1)
    await page.reload(); await page.waitForFunction(() => document.querySelector('input[placeholder="请输入表单名称"]')?.value === 'New form')
  })
  await scenario('runtime-denied-permission-stops-data-access', 'v2', async (page) => {
    store.permission = false
    await open(page, '/lowcode/form/form_device?mode=edit&id=1')
    await page.getByText('无访问权限', { exact: true }).waitFor()
    assert.deepEqual(store.requests.map((r) => r.path), ['/permission/check'])
  })
} finally {
  await writeFile(`${artifacts}/results.json`, JSON.stringify(results, null, 2))
  await browser.close(); await server.close()
}
console.log(`${results.filter((r) => r.status === 'passed').length}/${results.length} consumer scenarios passed`)
if (results.length !== 8 || results.some((r) => r.status !== 'passed')) process.exitCode = 1
