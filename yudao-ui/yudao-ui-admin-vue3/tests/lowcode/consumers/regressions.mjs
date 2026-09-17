import assert from 'node:assert/strict'

export const REGRESSION_SCENARIOS = 12

export async function runRegressionScenarios({ scenario, open, getStore, writes }) {
  for (const version of ['v1', 'v2']) {
    await scenario(`${version}-designer-copies-use-distinct-answer-keys`, version, async (page) => {
      const store = getStore()
      store.forms[1].status = 'DRAFT'
      await open(page, '/designer?id=1')
      await page.locator('.field-card').first().waitFor()
      const original = page.locator('.field-card').filter({ has: page.locator('.field-prop', { hasText: /^name$/ }) })
      // The third button is the existing copy action; no test-only API is used.
      await original.locator('.field-actions button').nth(2).click()
      await original.locator('.field-actions button').nth(2).click()
      const keys = await page.locator('.field-prop').allTextContents()
      assert.equal(new Set(keys).size, keys.length)
      assert.ok(keys.includes('name_copy'))
      assert.ok(keys.includes('name_copy2'))
      await page.getByRole('button', { name: '保存草稿', exact: true }).click()
      await page.getByText('保存成功', { exact: true }).waitFor()
      const saved = JSON.parse(store.forms[1].formConfig)
      assert.equal(new Set(saved.fields.map((field) => field.prop)).size, 4)
      await page.reload()
      await page.locator('.field-card').first().waitFor()
      assert.deepEqual(await page.locator('.field-prop').allTextContents(), keys)
    })
    await scenario(`${version}-designer-preview-treats-answers-as-text`, version, async (page) => {
      await open(page, '/designer?id=1')
      await page.locator('.field-card').first().waitFor()
      await page.getByRole('button', { name: '预览', exact: true }).click()
      const answer = '<b data-preview-probe="true">user-provided markup</b>'
      await page.getByPlaceholder('Enter name').fill(answer)
      await page.getByRole('button', { name: '校验并预览数据', exact: true }).click()
      const box = page.locator('.el-message-box')
      await box.waitFor()
      const value = JSON.parse(await box.locator('pre').innerText())
      assert.equal(value.name, answer)
      assert.equal(await box.locator('[data-preview-probe]').count(), 0)
      assert.equal(writes().length, 0)
      await box.getByRole('button', { name: '关闭', exact: true }).click()
      await page.getByRole('button', { name: '退出预览', exact: true }).click()
      await page.getByRole('button', { name: '预览', exact: true }).click()
      assert.equal(await page.getByPlaceholder('Enter name').inputValue(), '')
    })
    await scenario(`${version}-missing-record-cannot-open-edit`, version, async (page) => {
      await open(page, '/lowcode/form/form_device?mode=edit&id=999')
      await page.getByText('页面不存在', { exact: true }).waitFor()
      assert.equal(await page.getByRole('button', { name: '保存', exact: true }).count(), 0)
      assert.equal(writes().length, 0)
    })
  }
  await scenario('list-delete-business-error-and-double-click', 'v2', async (page) => {
    const store = getStore()
    store.listConfig = { operations: [{ label: '删除', action: 'delete', api: '/api/lowcode/data/device/{id}' }] }
    await open(page, '/lowcode/list/list_device')
    const row = page.locator('tr').filter({ hasText: 'Router' })
    await row.waitFor()
    store.failWrite = true
    await row.getByRole('button', { name: '删除', exact: true }).click()
    await page.getByText('fixture-write-rejected', { exact: true }).waitFor()
    assert.ok(store.records[1])
    assert.equal(await page.getByText('删除成功', { exact: true }).count(), 0)
    await row.getByRole('button', { name: '删除', exact: true }).evaluate((button) => { button.click(); button.click() })
    await page.getByText('删除成功', { exact: true }).waitFor()
    assert.equal(store.records[1], undefined)
    assert.equal(writes().filter((entry) => entry.method === 'DELETE').length, 2)
  })
  await scenario('list-export-error-does-not-download-json', 'v1', async (page) => {
    const store = getStore()
    store.listConfig = { toolbar: [], export: { enabled: true, api: '/api/lowcode/export/device' } }
    const downloads = []
    page.on('download', (download) => downloads.push(download.suggestedFilename()))
    await open(page, '/lowcode/list/list_device')
    await page.getByRole('button', { name: '导出', exact: true }).click()
    await page.getByText('fixture-export-rejected', { exact: true }).waitFor()
    assert.deepEqual(downloads, [])
    assert.equal(await page.getByText('导出成功', { exact: true }).count(), 0)
  })
  for (const lateFailure of [false, true]) {
    await scenario(`list-late-${lateFailure ? 'failure' : 'success'}-cannot-replace-latest-query`, 'v2', async (page) => {
      const store = getStore()
      store.listConfig = { filters: [{ id: 'name', prop: 'name', label: 'Name', type: 'input', placeholder: 'Filter name' }] }
      await open(page, '/lowcode/list/list_device')
      await page.locator('tr').filter({ hasText: 'Router' }).waitFor()
      store.deferNextList = true
      store.failNextList = lateFailure
      await page.getByPlaceholder('Filter name').fill('Router')
      await page.getByRole('button', { name: '查询', exact: true }).click()
      // Wait for the actual first HTTP request, not a guessed timing delay.
      for (let attempt = 0; !store.releaseList && attempt < 100; attempt++) {
        await new Promise((resolve) => setTimeout(resolve, 10))
      }
      assert.equal(typeof store.releaseList, 'function')
      await page.getByPlaceholder('Filter name').fill('Switch')
      const latest = page.waitForResponse((response) => response.url().includes('/data/device?') && response.url().includes('Switch'))
      await page.getByRole('button', { name: '查询', exact: true }).click()
      await (await latest).finished()
      await page.locator('tr').filter({ hasText: 'Router' }).waitFor({ state: 'detached' })
      await page.locator('tr').filter({ hasText: 'Switch' }).waitFor()
      const late = page.waitForResponse((response) => response.url().includes('/data/device?') && response.url().includes('Router'))
      store.releaseList()
      const response = await late
      await response.finished()
      await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))))
      assert.equal(await page.locator('tr').filter({ hasText: 'Switch' }).count(), 1)
      assert.equal(await page.locator('tr').filter({ hasText: 'Router' }).count(), 0)
    })
  }
  await scenario('list-honors-configured-page-size', 'v1', async (page) => {
    const store = getStore()
    store.listConfig = { pageSize: 1 }
    await open(page, '/lowcode/list/list_device')
    await page.locator('tr').filter({ hasText: 'Router' }).waitFor()
    const query = store.requests.find((entry) => entry.path === '/data/device').query
    assert.equal(query.size, '1')
    assert.equal(await page.locator('tr').filter({ hasText: 'Switch' }).count(), 0)
  })
  await scenario('permission-service-error-stops-data-access', 'v2', async (page) => {
    const store = getStore()
    store.failPermission = true
    await open(page, '/lowcode/form/form_device?mode=edit&id=1')
    await page.getByText('加载失败', { exact: true }).waitFor()
    assert.deepEqual(store.requests.map((entry) => entry.path), ['/permission/check'])
    assert.equal(writes().length, 0)
  })
}
