import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/config/axios'
import * as Template from './index'
import * as Definitions from './definitions'
import { commandIntent } from '@/views/pms/project/project-templates/editorModel'

vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }))
beforeEach(() => vi.clearAllMocks())

const save: Definitions.DefinitionSave = {
  definitionKind: 'TASK', definitionCode: 'TASK_A', schemaVersion: 1,
  payload: { name: '任务' }, references: [{ referenceKey: 'workBinding', targetRevisionId: 9 }]
}
const designer = (): Template.TemplateDesignerDocument => ({
  ...Template.emptyDesignerDocument(),
  match: { signingMethod: 'DIRECT' },
  stages: [{
    nodeKey: 'stage:S0', code: 'S0', name: '立项', start: true, terminal: false,
    workBinding: { type: 'STAGE_NATIVE', parameters: {} },
    permission: { policyRef: 'PROJECT_STAGE_NATIVE_DEFAULT' },
    completionRule: { expression: { predicate: 'STAGE_NATIVE_STATUS', parameters: { requiredStatus: 'DONE' } } }
  }, {
    nodeKey: 'stage:S4', code: 'S4', name: '实施', start: false, terminal: true,
    workBinding: { type: 'STAGE_NATIVE', parameters: {} },
    permission: { policyRef: 'PROJECT_STAGE_NATIVE_DEFAULT' },
    completionRule: { expression: { predicate: 'STAGE_NATIVE_STATUS', parameters: { requiredStatus: 'DONE' } } }
  }],
  transitions: [{
    edgeKey: 'transition:S0-S4', code: 'S0-S4', fromStageCode: 'S0', toStageCode: 'S4',
    priority: 0, defaultBranch: true
  }]
})

describe('PM-03 HTTP contracts after Designer V2 rewrite', () => {
  it('keeps reusable definition-library contracts as authoring assets', async () => {
    await Definitions.getDefinitionPage({
      pageNo: 2, pageSize: 10, definitionKind: 'TASK', definitionCode: 'TASK_A', revisionState: 'PUBLISHED'
    })
    expect(request.get).toHaveBeenCalledWith({
      url: '/api/v1/pms/delivery-definitions',
      params: { pageNo: 2, pageSize: 10, definitionKind: 'TASK', definitionCode: 'TASK_A', revisionState: 'PUBLISHED' }
    })

    await Definitions.createDefinition({ ...save, id: 99, version: 8, disabledAt: 'yesterday' } as Definitions.DefinitionRevision, 'create-key')
    expect(request.post).toHaveBeenLastCalledWith({
      url: '/api/v1/pms/delivery-definitions', data: save, headers: { 'Idempotency-Key': 'create-key' }
    })
  })

  it('preserves Snowflake business-view ids as decimal strings in reusable authoring assets', async () => {
    const id = '9223372036854775807'
    const binding: Definitions.DefinitionSave = {
      definitionKind: 'WORK_BINDING', definitionCode: 'BIND_BIG', schemaVersion: 1,
      payload: { bindingType: 'BUSINESS_COMPONENT', businessViewRevisionId: id }, references: []
    }
    await Definitions.createDefinition(binding, 'big-create')
    const created = vi.mocked(request.post).mock.calls[0][0] as { data: Definitions.DefinitionSave }
    expect(created.data.payload.businessViewRevisionId).toBe(id)
    expect(JSON.parse(JSON.stringify(created.data)).payload.businessViewRevisionId).toBe(id)
  })

  it('uses a dedicated Designer draft API as the only writable template-content contract', async () => {
    const document = designer()
    await Template.getProjectTemplateDraft(4)
    expect(request.get).toHaveBeenLastCalledWith({ url: '/api/v1/pms/project-templates/4/draft' })

    await Template.updateProjectTemplateDraft(4, document)
    expect(request.put).toHaveBeenLastCalledWith({
      url: '/api/v1/pms/project-templates/4/draft',
      data: document
    })
    expect(document.schemaVersion).toBe(2)
    expect(document.transitions[0]).toHaveProperty('edgeKey')
    expect(document.transitions[0]).not.toHaveProperty('conditionRuleRevisionId')
    expect(document.transitions[0]).not.toHaveProperty('revisionNo')
  })

  it('deep clones outbound Designer payloads so request preparation cannot mutate editor state', async () => {
    const document = designer()
    await Template.updateProjectTemplateDraft(4, document)
    const sent = vi.mocked(request.put).mock.calls[0][0] as { data: Template.TemplateDesignerDocument }
    sent.data.stages[0].name = 'changed in transport'
    expect(document.stages[0].name).toBe('立项')
  })

  it('validates and publishes from the saved Designer without client-created execution snapshots', async () => {
    await Template.validateProjectTemplate(4)
    expect(request.post).toHaveBeenLastCalledWith({ url: '/api/v1/pms/project-templates/4/actions/validate' })
    await Template.publishProjectTemplate(4)
    expect(request.post).toHaveBeenLastCalledWith({ url: '/api/v1/pms/project-templates/4/actions/publish' })
  })

  it('copies an explicit historical revision using root version and a stable idempotency intent', async () => {
    await Template.copyProjectTemplate(4, 12, { code: 'NEW', name: '新模板', sourceRevisionNo: 2 }, 'copy-key')
    expect(request.post).toHaveBeenCalledWith({
      url: '/api/v1/pms/project-templates/4/actions/copy',
      data: { code: 'NEW', name: '新模板', sourceRevisionNo: 2 },
      headers: { 'If-Match': '12', 'Idempotency-Key': 'copy-key' }
    })
  })

  it('keeps template identity updates separate from Designer content updates', async () => {
    await Template.updateProjectTemplate(4, { name: '新版名称', matchPriority: 10 })
    expect(request.put).toHaveBeenCalledWith({
      url: '/api/v1/pms/project-templates/4', data: { name: '新版名称', matchPriority: 10 }
    })
  })

  it('reuses idempotency only for unchanged command retries', () => {
    const intent = commandIntent()
    const first = intent.key({ id: 1, version: 2 })
    expect(intent.key({ id: 1, version: 2 })).toBe(first)
    expect(intent.key({ id: 1, version: 3 })).not.toBe(first)
    intent.clear()
    expect(intent.key({ id: 1, version: 2 })).not.toBe(first)
  })
})
