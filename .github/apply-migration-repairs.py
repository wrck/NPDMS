"""Apply the reviewed migration repair to exact source blobs, never an unknown base.

This temporary delivery script is removed with the verified source commit.
It does not change requirements, V1 renderer implementation, or application dependencies.
"""
from pathlib import Path
import re
import subprocess

F = Path('yudao-ui/yudao-ui-admin-vue3')
expected = {
 'src/components/ComponentSandbox/index.vue': 'decbdaf752fa3bfd66807b59b76a2a7dd3ebb0f4',
 'src/components/LowCodeListRenderer/index.vue': '303f1fb395fc3f1bfa9ac11be6e389822c3a8a73',
 'src/components/LowCodeRelatedPageRenderer/index.vue': 'fe25b5497c5a99b1c9ed49d47488268be4d4ece4',
 'src/components/LowCodeTabRenderer/index.vue': '1f6661cbd75085b00c3e5cc01e233c51944d887b',
 'src/components/LowCodeWidgets/SignaturePad.vue': '81ca48f779de1957d40a7b44276c91b9bc08f689',
 'src/views/lowcode/form-designer/index.vue': '35f718d5bbda09ef5c3400dfce74ad429a86d326',
 'src/views/lowcode/microflow-designer/index.vue': 'fda06d5a48b9fa4e2a5ab07ef40d85f80723688a',
 'src/views/lowcode/preview/index.vue': '868801d88659185736668b61193cdde695ed63ba',
 'src/views/lowcode/render/index.vue': '8a6c9d04bd0b730857ea1d2f5d6c65361d91f469',
 'src/views/pms/delivery-business/site-survey/index.vue': '6d8c089e6fc2177ca646e679007e9742b6a113e6',
 'src/views/pms/engineering/site-survey/index.vue': '7a88eeac4605ccafb3135c03b98f3bef0bd42709',
 'src/views/pms/project/inheritance/detail/ProjectFlowStageNavigation.vue': 'VERIFY_FROM_BASE',
 'src/views/pms/project/projects/index.spec.ts': 'VERIFY_FROM_BASE',
 'tests/lowcode/consumers/run.mjs': '5b8b3d7a09cb0c3391a7204920830822c3383258'
}
base = 'e1a4f923f6c66373caa7ee47c37429f26223059b'
for name, sha in expected.items():
    path = str(F / name)
    if sha == 'VERIFY_FROM_BASE':
        sha = subprocess.check_output(['git', 'rev-parse', f'{base}:{path}'], text=True).strip()
    actual = subprocess.check_output(['git', 'hash-object', path], text=True).strip()
    if actual != sha:
        raise SystemExit(f'Source changed, refusing to overwrite: {path}')

written = []
def change(name, old, new, count=1):
    p = F / name
    s = p.read_text()
    if s.count(old) != count:
        raise SystemExit(f'Expected {count} replacements in {name}: {old[:100]}')
    p.write_text(s.replace(old, new))

def write(name, content):
    p = F / name
    if p.exists():
        raise SystemExit(f'New source already exists: {name}')
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content)
    written.append(name)

D = 'src/views/lowcode/form-designer/index.vue'
change(D, 'import { computed, nextTick', 'import { computed, h, nextTick')
change(D, "import { useUndoRedo } from '@/composables/useUndoRedo'", "import { useUndoRedo } from '@/composables/useUndoRedo'\nimport { createPreviewData, uniqueCopyProp } from './designerState'")
change(D, 'const previewData = reactive<Record<string, unknown>>({})', 'const previewData = ref<Record<string, unknown>>({})\nconst previewRendererRef = ref<InstanceType<typeof LowCodeFormRendererFacade>>()')
change(D, '  copy.prop = `${src.prop}_copy`', '  copy.prop = uniqueCopyProp(src.prop, formConfig.fields)')
change(D, '  fieldSeq++\n  const id = `field_${fieldSeq}`', '  do {\n    fieldSeq++\n  } while (formConfig.fields.some((field) => field.id === `field_${fieldSeq}` || field.prop === `field${fieldSeq}`))\n  const id = `field_${fieldSeq}`')
change(D, "  // 清空预览数据\n  for (const k of Object.keys(previewData)) delete previewData[k]\n  // 写入默认值\n  for (const f of formConfig.fields) {\n    previewData[f.prop] = f.defaultValue ?? ''\n  }", '  // A fresh model isolates editable values from the saved schema/default arrays.\n  previewData.value = createPreviewData(formConfig.fields)')
change(D, '    `<pre style="max-height:400px;overflow:auto;">${JSON.stringify(val, null, 2)}</pre>`,', "    h('pre', { class: 'max-h-400px overflow-auto whitespace-pre-wrap' }, JSON.stringify(val, null, 2)),")
change(D, "    { dangerouslyUseHTMLString: true, confirmButtonText: '关闭' }", "    { confirmButtonText: '关闭' }")
change(D, ':key="comp.type"', ':key="comp.componentName || comp.type"')
change(D, '<el-button :icon="\'CopyDocument\'" @click.stop="duplicateField(field.id)" />', '<el-button aria-label="复制字段" :icon="\'CopyDocument\'" @click.stop="duplicateField(field.id)" />')
change(D, '          <el-button :icon="\'Back\'" @click="exitPreview">退出预览</el-button>', '          <el-button type="primary" @click="previewRendererRef?.submit()">校验并预览数据</el-button>\n          <el-button :icon="\'Back\'" @click="exitPreview">退出预览</el-button>')
change(D, '      <LowCodeFormRendererFacade\n        :config="formConfig"', '      <LowCodeFormRendererFacade\n        ref="previewRendererRef"\n        :config="formConfig"')
write('src/views/lowcode/form-designer/designerState.ts', '''interface DesignerField {
  prop: string
  defaultValue?: unknown
}

/** Copying a field must not bind the new control to an existing answer. */
export function uniqueCopyProp(prop: string, fields: readonly DesignerField[]): string {
  const used = new Set(fields.map((field) => field.prop))
  const base = `${prop}_copy`
  let candidate = base
  let suffix = 2
  while (used.has(candidate)) candidate = `${base}${suffix++}`
  return candidate
}

/** Form configs are JSON; clone defaults so preview edits never mutate them. */
export function createPreviewData(fields: readonly DesignerField[]): Record<string, unknown> {
  return Object.fromEntries(
    fields.map((field) => [field.prop, JSON.parse(JSON.stringify(field.defaultValue ?? ''))])
  )
}
''')
write('src/views/lowcode/form-designer/designerState.spec.ts', '''import { describe, expect, it } from 'vitest'
import { createPreviewData, uniqueCopyProp } from './designerState'

describe('designer editable state', () => {
  it('allocates a new answer key for repeated copies', () => {
    const fields = [{ prop: 'name' }]
    const first = uniqueCopyProp('name', fields)
    fields.push({ prop: first })
    const second = uniqueCopyProp('name', fields)
    expect(first).toBe('name_copy')
    expect(second).toBe('name_copy2')
  })
  it('respects existing manually assigned copy keys', () => {
    expect(uniqueCopyProp('name', [{ prop: 'name_copy' }, { prop: 'name_copy2' }])).toBe('name_copy3')
  })
  it('preserves false, zero and empty default values', () => {
    expect(createPreviewData([
      { prop: 'zero', defaultValue: 0 },
      { prop: 'flag', defaultValue: false },
      { prop: 'text', defaultValue: '' },
      { prop: 'missing' }
    ])).toEqual({ zero: 0, flag: false, text: '', missing: '' })
  })
  it('does not share nested defaults with the editable model or another preview', () => {
    const fields = [{ prop: 'items', defaultValue: [{ value: 'original' }] }]
    const first = createPreviewData(fields)
    ;(first.items as Array<{ value: string }>)[0].value = 'edited'
    expect(fields[0].defaultValue[0].value).toBe('original')
    expect(createPreviewData(fields).items).toEqual([{ value: 'original' }])
  })
})
''')
L = 'src/components/LowCodeListRenderer/index.vue'
change(L, 'watch, onMounted', 'watch, onMounted, onBeforeUnmount')
change(L, "import { TOKEN_KEY, get, post } from '@/utils/request'", "import request, { TOKEN_KEY, get, post } from '@/utils/request'")
change(L, 'const innerLoading = ref(false)', 'const innerLoading = ref(false)\nlet fetchSequence = 0\nconst pendingDeletes = new Set<string>()\nonBeforeUnmount(() => { fetchSequence++ })')
change(L, '    pageSize: 20,\n', '')
change(L, 'ref(props.pageSize || props.config.pageSize || 20)', 'ref(props.pageSize ?? props.config.pageSize ?? 20)')
change(L, '''          const token = localStorage.getItem(TOKEN_KEY) || ''
          await axios.request({ url, method, headers: url.startsWith('/api/lowcode/') ? lowcodeSessionHeaders() : { Authorization: `Bearer ${token}` } })
          ElMessage.success('删除成功')
          fetchData()''', '''          const key = `${method}:${url}`
          if (pendingDeletes.has(key)) return
          pendingDeletes.add(key)
          try {
            if (url.startsWith('/api/lowcode/')) {
              await request.request({ url, method })
            } else {
              const token = localStorage.getItem(TOKEN_KEY) || ''
              try {
                const response = await axios.request({ url, method, headers: { Authorization: `Bearer ${token}` } })
                const payload = response.data
                if (payload && typeof payload.code === 'number' && payload.code !== 0 && payload.code !== 200) {
                  throw new Error(payload.msg || payload.message || '删除失败')
                }
              } catch (error) {
                ElMessage.error(error instanceof Error ? error.message : '删除失败')
                throw error
              }
            }
            ElMessage.success('删除成功')
            await fetchData()
          } finally {
            pendingDeletes.delete(key)
          }''')
change(L, '  const api = effectiveSearchApi.value\n  if (!api) return', '  const sequence = ++fetchSequence\n  const api = effectiveSearchApi.value\n  if (!api) return')
change(L, '    innerData.value = page?.records', '    if (sequence !== fetchSequence) return\n    innerData.value = page?.records')
change(L, '  } catch (e) {\n    innerData.value = []', '  } catch (e) {\n    if (sequence !== fetchSequence) return\n    innerData.value = []')
change(L, '    innerLoading.value = false\n  }\n}', '    if (sequence === fetchSequence) innerLoading.value = false\n  }\n}')
change(L, '    triggerBlobDownload(response.data, fileName)', '''    // JSON business errors must never be downloaded as a successful workbook.
    const blob = response.data as Blob
    if (blob.type.toLowerCase().includes('json')) {
      const payload = JSON.parse(await blob.text())
      throw new Error(payload?.msg || payload?.message || '导出未返回有效文件')
    }
    triggerBlobDownload(blob, fileName)''')
change(L, "    console.warn('[LowCodeListRenderer] 导出失败', e)", "    ElMessage.error(e instanceof Error ? e.message : '导出失败')")
change(L, '  (api) => {\n    if (props.autoFetch && api)', '  (api) => {\n    fetchSequence++\n    innerLoading.value = false\n    innerData.value = []\n    innerTotal.value = 0\n    if (props.autoFetch && api)')
change(L, 'v-if="config.toolbar && config.toolbar.length"', 'v-if="config.toolbar?.length || config.export?.enabled"')
change('src/views/lowcode/render/index.vue', '      formDataModel.value = data\n', "      if (!data) {\n        state.value = 'not-found'\n        return\n      }\n      formDataModel.value = data\n")

# Only the nine invalid non-void self-closing elements found by ESLint.
for name, tag, count in [
 ('src/components/ComponentSandbox/index.vue', 'iframe', 1),
 ('src/components/LowCodeRelatedPageRenderer/index.vue', 'iframe', 3),
 ('src/components/LowCodeTabRenderer/index.vue', 'iframe', 1),
 ('src/components/LowCodeWidgets/SignaturePad.vue', 'canvas', 1),
 ('src/views/lowcode/microflow-designer/index.vue', 'div', 1),
 ('src/views/lowcode/preview/index.vue', 'iframe', 1),
 ('src/views/pms/project/inheritance/detail/ProjectFlowStageNavigation.vue', 'span', 1)
]:
    p = F / name
    s = p.read_text()
    # Attribute values may contain TypeScript generic angle brackets.
    pattern = r'<' + tag + r'\b(?:(?:"[^"]*"|\x27[^\x27]*\x27)|[^>])*?/>'
    s, actual = re.subn(pattern, lambda m: m[0][:-2].rstrip() + '></' + tag + '>', s, flags=re.S)
    if actual != count:
        raise SystemExit(f'Unexpected non-void element count in {name}: {actual}')
    p.write_text(s)

for name in ['src/views/pms/delivery-business/site-survey/index.vue', 'src/views/pms/engineering/site-survey/index.vue']:
    p = F / name
    template, rest = p.read_text().split('<script setup lang="ts">', 1)
    template = re.sub(r'\breadonly\b', 'formReadonly', template).replace(':formReadonly=', ':readonly=')
    rest = rest.replace('const readonly = computed(', 'const formReadonly = computed(').replace('readonly.value', 'formReadonly.value')
    p.write_text(template + '<script setup lang="ts">' + rest)
change('src/views/pms/project/projects/index.spec.ts', '/:disabled="readonly \\|\\| saving"/', '/:disabled="formReadonly \\|\\| saving"/')
change('src/views/pms/project/projects/index.spec.ts', '/v-if="!readonly" type="primary"', '/v-if="!formReadonly" type="primary"')

R = 'tests/lowcode/consumers/run.mjs'
change(R, "import { createServer } from 'vite'", "import { createServer } from 'vite'\nimport { runRegressionScenarios, REGRESSION_SCENARIOS } from './regressions.mjs'")
change(R, 'path: url.pathname, body, headers: req.headers', 'path: url.pathname, query: Object.fromEntries(url.searchParams), body, headers: req.headers')
change(R, "if (url.pathname === '/permission/check') return send(store.permission)", "if (url.pathname === '/permission/check') return store.failPermission ? send(null, 503, 'fixture-permission-unavailable') : send(store.permission)")
change(R, "store.failWrite && ['POST', 'PUT'].includes(req.method)", "store.failWrite && ['POST', 'PUT', 'DELETE'].includes(req.method)")
change(R, "{ label: '详情', action: 'view' } ] }) })", "{ label: '详情', action: 'view' } ], ...store.listConfig }) })\n      if (url.pathname === '/export/device') return send(null, 409, 'fixture-export-rejected')")
change(R, '        return send({ records: Object.values(store.records), total: Object.keys(store.records).length })', '''        const state = store
        const records = Object.values(state.records).filter((row) => !url.searchParams.get('name') || row.name.includes(url.searchParams.get('name')))
        const size = Number(url.searchParams.get('size') || 20)
        const page = Number(url.searchParams.get('page') || url.searchParams.get('current') || 1)
        const result = JSON.parse(JSON.stringify({ records: records.slice((page - 1) * size, page * size), total: records.length }))
        const reject = state.failNextList
        state.failNextList = false
        if (state.deferNextList) {
          state.deferNextList = false
          await new Promise((resolve) => { state.releaseList = resolve })
        }
        return reject ? send(null, 409, 'fixture-list-rejected') : send(result)''')
change(R, "        if (req.method === 'PUT') state.records[id]", "        if (req.method === 'DELETE') { delete state.records[id]; return send(null) }\n        if (req.method === 'PUT') state.records[id]")
change(R, "return send(req.method === 'PUT' ? null : state.records[id])", "return send(req.method === 'PUT' ? null : state.records[id] ?? null)")
change(R, "['POST', 'PUT'].includes(r.method)", "['POST', 'PUT', 'DELETE'].includes(r.method)")
change(R, '    await context.tracing.stop(', '    store.releaseList?.()\n    await context.tracing.stop(')
change(R, "} finally {\n  await writeFile(`${artifacts}/results.json`", "  await runRegressionScenarios({ scenario, open, getStore: () => store, writes })\n} finally {\n  await writeFile(`${artifacts}/results.json`")
change(R, 'results.length !== 8 ||', 'results.length !== 8 + REGRESSION_SCENARIOS ||')

write('tests/lowcode/consumers/regressions.mjs', '''import assert from 'node:assert/strict'

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
''')
subprocess.run(['git', 'add', '--', *(str(F / name) for name in [*expected, *written])], check=True)
subprocess.run(['git', 'diff', '--cached', '--check'], check=True)
print('Prepared reviewed source repair. Nothing has been committed or pushed.')
