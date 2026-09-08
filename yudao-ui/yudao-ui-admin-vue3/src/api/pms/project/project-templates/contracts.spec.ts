import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/config/axios'
import * as Template from './index'
import * as Definitions from './definitions'
import { commandIntent } from '@/views/pms/project/project-templates/editorModel'
vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }))
beforeEach(() => vi.clearAllMocks())
const save: Definitions.DefinitionSave = { definitionKind: 'TASK', definitionCode: 'TASK_A', schemaVersion: 1, payload: { name: '任务' }, references: [{ referenceKey: 'workBinding', targetRevisionId: 9 }] }
describe('PM-03 exact HTTP and historical protection contracts', () => {
  it('queries exact definition filters at the root, without /page', async () => {
    await Definitions.getDefinitionPage({ pageNo: 2, pageSize: 10, definitionKind: 'TASK', definitionCode: 'TASK_A', revisionState: 'PUBLISHED' })
    expect(request.get).toHaveBeenCalledWith({ url: '/api/v1/pms/delivery-definitions', params: { pageNo: 2, pageSize: 10, definitionKind: 'TASK', definitionCode: 'TASK_A', revisionState: 'PUBLISHED' } })
  })
  it('creates with idem only and removes server-owned metadata from Save', async () => {
    await Definitions.createDefinition({ ...save, id: 99, version: 8, disabledAt: 'yesterday' } as Definitions.DefinitionRevision, 'create-key')
    expect(request.post).toHaveBeenCalledWith({ url: '/api/v1/pms/delivery-definitions', data: save, headers: { 'Idempotency-Key': 'create-key' } })
  })
  it('updates with exact optimistic version and retries commands without invented bodies', async () => {
    await Definitions.updateDefinition(3, 7, save, 'save-key')
    expect(request.put).toHaveBeenCalledWith({ url: '/api/v1/pms/delivery-definitions/3', data: save, headers: { 'If-Match': '7', 'Idempotency-Key': 'save-key' } })
    for (const [action, call] of [['copy', Definitions.copyDefinition], ['publish', Definitions.publishDefinition], ['disable', Definitions.disableDefinition]] as const) {
      await call(3, 8, 'command-key')
      expect(request.post).toHaveBeenLastCalledWith({ url: `/api/v1/pms/delivery-definitions/3/actions/${action}`, headers: { 'If-Match': '8', 'Idempotency-Key': 'command-key' } })
    }
  })
  it('posts a Snowflake business-view reference as its exact decimal string on create and update', async () => {
    const id = '9223372036854775807'
    const binding: Definitions.DefinitionSave = { definitionKind: 'WORK_BINDING', definitionCode: 'BIND_BIG', schemaVersion: 1, payload: { bindingType: 'BUSINESS_COMPONENT', businessViewRevisionId: id }, references: [] }
    await Definitions.createDefinition(binding, 'big-create')
    const created = vi.mocked(request.post).mock.calls[0][0] as { data: Definitions.DefinitionSave }
    expect(created.data.payload.businessViewRevisionId).toBe(id)
    expect(JSON.parse(JSON.stringify(created.data)).payload.businessViewRevisionId).toBe(id)
    await Definitions.updateDefinition(3, 7, binding, 'big-update')
    const updated = vi.mocked(request.put).mock.calls[0][0] as { data: Definitions.DefinitionSave }
    expect(updated.data.payload.businessViewRevisionId).toBe(id)
    expect(JSON.parse(JSON.stringify(updated.data)).payload.businessViewRevisionId).toBe(id)
  })
  it('validates definitions and templates without headers or business writes', async () => {
    await Definitions.validateDefinition(3)
    expect(request.post).toHaveBeenLastCalledWith({ url: '/api/v1/pms/delivery-definitions/3/actions/validate' })
    await Template.validateProjectTemplate(4)
    expect(request.post).toHaveBeenLastCalledWith({ url: '/api/v1/pms/project-templates/4/actions/validate' })
  })
  it('copies the explicit historical revision using root version + stable intent', async () => {
    await Template.copyProjectTemplate(4, 12, { code: 'NEW', name: '新模板', sourceRevisionNo: 2 }, 'copy-key')
    expect(request.post).toHaveBeenCalledWith({ url: '/api/v1/pms/project-templates/4/actions/copy', data: { code: 'NEW', name: '新模板', sourceRevisionNo: 2 }, headers: { 'If-Match': '12', 'Idempotency-Key': 'copy-key' } })
  })
  it('strips client snapshots and PMS process versions without inventing graph facts', async () => {
    const content: Template.TemplateDefinitionContent = {
      processDefinitionKey: 'process', processDefinitionVersion: 'old', stages: [{ stageCode: 'S0', name: '旧阶段', sortOrder: 8, definitionSnapshot: { owner: 'forged' } }],
      tasks: [{ taskCode: 'OLD', name: '旧任务', workBindingTypeCode: 'TASK_NATIVE', definitionSnapshot: { fake: true } }], milestones: [], deliverables: [],
      gates: [{ gateCode: 'G', name: '门禁', gateType: 'EXIT', references: [{ refType: 'PROCESS', refCode: 'flow', refVersion: 'legacy' }] }]
    }
    await Template.updateProjectTemplate(4, { content })
    const sent = vi.mocked(request.put).mock.calls[0][0] as any
    expect(sent).not.toHaveProperty('headers')
    expect(sent.data.content).not.toHaveProperty('transitions')
    expect(sent.data.content).not.toHaveProperty('processDefinitionVersion')
    expect(sent.data.content.stages[0]).not.toHaveProperty('definitionSnapshot')
    expect(sent.data.content.tasks[0].workBindingTypeCode).toBe('TASK_NATIVE')
    expect(sent.data.content.gates[0].references[0]).not.toHaveProperty('refVersion')
    expect(content.stages[0].definitionSnapshot).toEqual({ owner: 'forged' })
    expect(content.processDefinitionVersion).toBe('old')
  })
  it('keeps existing publish header-free and preserves default branch fields', async () => {
    await Template.publishProjectTemplate(4)
    expect(request.post).toHaveBeenCalledWith({ url: '/api/v1/pms/project-templates/4/actions/publish' })
    const edge = { transitionCode: 'E', fromStageCode: 'S0', toStageCode: 'S4', priority: 3, default: true, revisionNo: 2 }
    expect(Template.templateSaveContent({ stages: [], transitions: [edge], tasks: [], milestones: [], deliverables: [], gates: [] }).transitions).toEqual([edge])
  })
  it('reuses idem only for unchanged retries, and changes key for a new version/intent', () => {
    const intent = commandIntent()
    const first = intent.key({ id: 1, version: 2 })
    expect(intent.key({ id: 1, version: 2 })).toBe(first)
    expect(intent.key({ id: 1, version: 3 })).not.toBe(first)
    intent.clear()
    expect(intent.key({ id: 1, version: 2 })).not.toBe(first)
  })
})
