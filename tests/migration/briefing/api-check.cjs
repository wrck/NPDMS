const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const root = path.resolve(process.argv[2] || '.')
const apiPath = path.join(root, 'yudao-ui/yudao-ui-admin-vue3/src/api/pms/engineering/briefing/entity.ts')
const source = fs.readFileSync(apiPath, 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  reportDiagnostics: true
})
assert.equal((compiled.diagnostics || []).filter(x => x.category === ts.DiagnosticCategory.Error).length, 0)
const calls = []
const request = Object.fromEntries(['get', 'post', 'put', 'delete'].map(method => [method,
  input => { calls.push({ method, ...input }); return Promise.resolve({ ok: true }) }]))
const sandbox = { exports: {}, require(name) {
  assert.equal(name, '@/config/axios')
  return { default: request }
} }
vm.runInNewContext(compiled.outputText, sandbox, { filename: 'briefing-entity-api.js' })
const api = sandbox.exports
const data = { id: 7, projectId: 10, code: 'BR-7', name: '测试', version: 0, content: '' }
const cases = [
  ['getBriefingPage', 'get', 'page', { projectId: 10, pageNo: 1, pageSize: 10 }, 'params'],
  ['getBriefing', 'get', 'get', 7, 'id'],
  ['createBriefing', 'post', 'create', data, 'data'],
  ['updateBriefing', 'put', 'update', data, 'data'],
  ['deleteBriefing', 'delete', 'delete', 7, 'id'],
  ['generateBriefing', 'put', 'generate', { id: 7, templateId: 0, sourceSnapshot: '', version: 0 }, 'data'],
  ['approveBriefing', 'put', 'approve', { id: 7, approveAction: 'REJECT', approveOpinion: '', version: 0 }, 'data'],
  ['publishBriefing', 'put', 'publish', 7, 'id'],
  ['terminateBriefing', 'put', 'terminate', 7, 'id'],
  ['importLegacyBriefing', 'post', 'import-legacy', 7, 'id']
]
;(async () => {
  for (const [name, method, route, input, kind] of cases) {
    const before = JSON.stringify(input)
    await api[name](input)
    const call = calls.at(-1)
    assert.equal(call.method, method)
    assert.equal(call.url, '/api/v1/pms/engineering-briefings/' + route)
    if (kind === 'id') assert.equal(call.params.id, input)
    else assert.strictEqual(call[kind], input)
    assert.equal(JSON.stringify(input), before)
  }
  assert.equal(calls.length, cases.length)
  console.log('通过 10 项 API 调用场景（方法、路由、参数和输入不变性）')
})().catch(error => { console.error(error); process.exitCode = 1 })
