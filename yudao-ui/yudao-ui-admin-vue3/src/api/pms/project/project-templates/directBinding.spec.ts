import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as Definitions from './definitions'
import * as Views from '@/api/pms/platform/business-view'
import * as Forms from '@/api/pms/platform/dynamic-form'
import {
  bindingContextMapping,
  createBindingSaveSession,
  prepareTaskBinding
} from './directBinding'
import type { TaskDef } from './index'
vi.mock('@/config/axios', () => ({ default: {} }))
vi.mock('./definitions', async (original) => ({
  ...(await original<typeof Definitions>()),
  getDefinition: vi.fn(),
  createDefinition: vi.fn(),
  publishDefinition: vi.fn()
}))
vi.mock('@/api/pms/platform/business-view', () => ({
  getBusinessView: vi.fn(),
  createBusinessView: vi.fn(),
  publishBusinessView: vi.fn()
}))
vi.mock('@/api/pms/platform/dynamic-form', () => ({ getRevision: vi.fn() }))
const view: Views.BusinessViewRegistrationVO = {
  id: '9223372036854775807',
  viewKey: '业务页面',
  entityType: 'SITE_SURVEY',
  ownerContext: 'SOL',
  viewSource: 'PAGE',
  componentKey: 'SOL_SITE_SURVEY',
  componentVersion: '1',
  revisionNo: 1,
  status: 'PUBLISHED',
  version: 1,
  contextSchema: { required: ['project'] },
  supportedActions: ['VIEW'],
  allowedActions: [],
  queryProviderKey: 'Q',
  commandProviderKey: 'C',
  permissionProviderKey: 'P'
}
const task: TaskDef = {
  taskCode: '需求',
  name: '需求分析',
  definitionRevisionId: 1,
  workBindingTypeCode: 'TASK_NATIVE',
  bindingConfig: 'old',
  componentKey: 'old',
  targetObjectKey: 'old',
  completionRuleTypeCode: 'TASK_NATIVE_STATUS'
}
let rows: Map<number, Definitions.DefinitionRevision>
let calls: string[]
beforeEach(() => {
  vi.clearAllMocks()
  calls = []
  rows = new Map()
  const revision = (
    id: number,
    definitionKind: Definitions.DefinitionKind,
    payload: Definitions.JsonObject
  ): Definitions.DefinitionRevision => ({
    id,
    definitionKind,
    definitionCode: `D${id}`,
    revisionNo: 1,
    revisionState: 'PUBLISHED',
    schemaVersion: 1,
    payload,
    references: [],
    version: 1
  })
  rows.set(1, {
    ...revision(1, 'TASK', {
      name: '原任务',
      workBinding: 'b',
      permissionPolicy: 'p',
      completionRule: 'c'
    }),
    references: [
      { referenceKey: 'b', targetRevisionId: 2 },
      { referenceKey: 'p', targetRevisionId: 3 },
      { referenceKey: 'c', targetRevisionId: 4 }
    ]
  })
  rows.set(2, revision(2, 'WORK_BINDING', { bindingType: 'TASK_NATIVE' }))
  rows.set(3, revision(3, 'PERMISSION_POLICY', { requiredActions: ['VIEW'] }))
  rows.set(
    4,
    revision(4, 'COMPLETION_RULE', {
      predicate: 'TASK_NATIVE_STATUS',
      parameters: { requiredStatus: 'DONE' }
    })
  )
  vi.mocked(Definitions.getDefinition).mockImplementation(async (id) =>
    structuredClone(rows.get(id)!)
  )
  vi.mocked(Definitions.createDefinition).mockImplementation(async (body) => {
    calls.push(`create:${body.definitionKind}`)
    expect(
      body.references.every((ref) => rows.get(ref.targetRevisionId)?.revisionState === 'PUBLISHED')
    ).toBe(true)
    const id = rows.size + 1
    rows.set(id, { ...body, id, revisionState: 'DRAFT', revisionNo: 1, version: 1 })
    return id
  })
  vi.mocked(Definitions.publishDefinition).mockImplementation(async (id) => {
    calls.push(`publish:${rows.get(id)!.definitionKind}`)
    rows.get(id)!.revisionState = 'PUBLISHED'
    return id
  })
  vi.mocked(Views.getBusinessView).mockResolvedValue(view)
})
describe('PM-03 direct binding durable preparation', () => {
  it('freezes the selected PRE-04 form source separately from the page view and canonical binding', async () => {
    const requirementView = { ...view, entityType: 'REQUIREMENT_ANALYSIS', componentKey: 'PROJ_REQUIREMENT_ANALYSIS' }
    vi.mocked(Views.getBusinessView).mockResolvedValue(requirementView)
    vi.mocked(Forms.getRevision).mockResolvedValue({ revisionId: 33, templateId: 22, revisionNo: 1,
      revisionVersion: 2, status: 'PUBLISHED' } as Forms.DynamicFormRevisionVO)
    const prepared = await prepareTaskBinding(task, { view: requirementView, strategy: 'REFERENCE_EXISTING',
      requirementFormRevisionId: 33, completion: { factCode: 'REQUIREMENT_ANALYSIS_COMPLETED', quantifier: 'ALL' } }, createBindingSaveSession())
    expect(Definitions.createDefinition).toHaveBeenCalledWith(expect.objectContaining({ definitionKind: 'WORK_BINDING',
      payload: expect.objectContaining({ bindingType: 'BUSINESS_OBJECT', targetObjectKey: 'PRE_04_REQUIREMENT_ANALYSIS',
        businessViewRevisionId: requirementView.id }) }), expect.any(String))
    expect(JSON.parse(prepared.bindingConfig!)).toEqual({ schemaVersion: 2, dynamicFormTemplateId: 22,
      dynamicFormTemplateRevisionId: 33, dynamicFormRevisionNo: 1, dynamicFormRevisionFactVersion: 2 })
    expect(prepared.dynamicFormRevisionId).toBeUndefined()
    expect(task.bindingConfig).toBe('old')
  })
  it('does not publish a requirement page-only binding or an unpublished form source', async () => {
    const requirementView = { ...view, entityType: 'REQUIREMENT_ANALYSIS', componentKey: 'PROJ_REQUIREMENT_ANALYSIS' }
    await expect(prepareTaskBinding(task, { view: requirementView, strategy: 'REFERENCE_EXISTING' }, createBindingSaveSession()))
      .rejects.toThrow('已发布表单')
    expect(Definitions.createDefinition).not.toHaveBeenCalled()
    vi.mocked(Forms.getRevision).mockResolvedValue({ revisionId: 33, templateId: 22, revisionNo: 1,
      revisionVersion: 2, status: 'DRAFT' } as Forms.DynamicFormRevisionVO)
    await expect(prepareTaskBinding(task, { view: requirementView, strategy: 'REFERENCE_EXISTING', requirementFormRevisionId: 33 }, createBindingSaveSession()))
      .rejects.toThrow('已发布修订')
    expect(Definitions.createDefinition).not.toHaveBeenCalled()
  })
  it('publishes dependency before TASK and retains exact original policy/completion while clearing assembler overrides', async () => {
    const original = structuredClone(task)
    const result = await prepareTaskBinding(
      task,
      { view, strategy: 'REFERENCE_EXISTING' },
      createBindingSaveSession()
    )
    expect(calls).toEqual([
      'create:WORK_BINDING',
      'publish:WORK_BINDING',
      'create:TASK',
      'publish:TASK'
    ])
    expect(Definitions.createDefinition).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({
        payload: expect.objectContaining({
          businessViewRevisionId: view.id,
          targetObjectKey: 'PROJECT_SITE_SURVEY',
          targetContextCode: 'SOL',
          targetObjectType: 'SITE_SURVEY',
          contextMapping: { project: 'project' }
        })
      }),
      expect.any(String)
    )
    expect(result).toMatchObject({
      definitionRevisionId: 6,
      workBindingRevisionId: 5,
      permissionPolicyRevisionId: 3,
      completionRuleRevisionId: 4
    })
    expect(result).not.toHaveProperty('bindingConfig')
    expect(result).not.toHaveProperty('targetObjectKey')
    expect(result).not.toHaveProperty('completionRuleTypeCode')
    expect(task).toEqual(original)
    expect(rows.get(4)?.payload.predicate).toBe('TASK_NATIVE_STATUS')
  })
  it('preserves original task on failure and resumes IDs/keys without another orphan', async () => {
    const publish = vi.mocked(Definitions.publishDefinition).getMockImplementation()!
    vi.mocked(Definitions.publishDefinition)
      .mockRejectedValueOnce(new Error('网络断开'))
      .mockImplementationOnce(publish)
      .mockImplementation(publish)
    const session = createBindingSaveSession()
    await expect(
      prepareTaskBinding(task, { view, strategy: 'REFERENCE_EXISTING' }, session)
    ).rejects.toThrow('网络断开')
    expect(task.bindingConfig).toBe('old')
    const firstKey = vi.mocked(Definitions.publishDefinition).mock.calls[0][2]
    await prepareTaskBinding(task, { view, strategy: 'REFERENCE_EXISTING' }, session)
    expect(Definitions.createDefinition).toHaveBeenCalledTimes(2)
    expect(vi.mocked(Definitions.publishDefinition).mock.calls[1][2]).toBe(firstKey)
    await prepareTaskBinding(task, { view, strategy: 'REFERENCE_EXISTING' }, session)
    expect(Definitions.createDefinition).toHaveBeenCalledTimes(2)
  })
  it('replays a lost create response using the exact same create idempotency key', async () => {
    vi.mocked(Definitions.createDefinition).mockRejectedValueOnce(new Error('响应丢失'))
    const session = createBindingSaveSession()
    await expect(
      prepareTaskBinding(task, { view, strategy: 'REFERENCE_EXISTING' }, session)
    ).rejects.toThrow('响应丢失')
    await prepareTaskBinding(task, { view, strategy: 'REFERENCE_EXISTING' }, session)
    expect(vi.mocked(Definitions.createDefinition).mock.calls[0][1]).toBe(
      vi.mocked(Definitions.createDefinition).mock.calls[1][1]
    )
  })
  it('registers published form with exact ID then binds its view and required context, never creates an instance', async () => {
    const form = {
      ...view,
      id: '9223372036854775806',
      entityType: 'DYNAMIC_FORM_INSTANCE',
      ownerContext: 'PLATFORM',
      componentKey: 'PLATFORM_DYNAMIC_FORM',
      viewSource: 'DYNAMIC_FORM' as const,
      dynamicFormRevisionId: '9223372036854775805',
      contextSchema: { required: ['project', 'instanceId'] }
    }
    vi.mocked(Views.createBusinessView).mockResolvedValue({
      ...form,
      status: 'DRAFT',
      allowedActions: ['PUBLISH']
    })
    vi.mocked(Views.getBusinessView).mockResolvedValue({
      ...form,
      status: 'DRAFT',
      allowedActions: ['PUBLISH']
    })
    vi.mocked(Views.publishBusinessView).mockResolvedValue(form)
    await prepareTaskBinding(
      task,
      {
        component: form,
        dynamicFormRevisionId: form.dynamicFormRevisionId,
        strategy: 'REFERENCE_EXISTING'
      },
      createBindingSaveSession()
    )
    expect(Views.createBusinessView).toHaveBeenCalledWith(
      expect.objectContaining({ dynamicFormRevisionId: form.dynamicFormRevisionId }),
      expect.any(String)
    )
    expect(Definitions.createDefinition).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({
        payload: expect.objectContaining({
          businessViewRevisionId: form.id,
          contextMapping: { project: 'project', instanceId: 'instanceId' },
          targetObjectKey: 'PROJECT_DYNAMIC_FORM_INSTANCE'
        })
      }),
      expect.any(String)
    )
  })
  it('does not treat supported actions as registration publication permission', async () => {
    const draft = {
      ...view,
      status: 'DRAFT' as const,
      supportedActions: ['PUBLISH'],
      allowedActions: []
    }
    vi.mocked(Views.createBusinessView).mockResolvedValue(draft)
    vi.mocked(Views.getBusinessView).mockResolvedValue(draft)
    await expect(
      prepareTaskBinding(
        task,
        { component: view, strategy: 'REFERENCE_EXISTING' },
        createBindingSaveSession()
      )
    ).rejects.toThrow('没有发布')
    expect(Views.publishBusinessView).not.toHaveBeenCalled()
    expect(Definitions.createDefinition).not.toHaveBeenCalled()
  })
  it('publishes a separately chosen business fact rule without mutating the original completion definition', async () => {
    const survey = {
      ...view,
      entityType: 'SITE_SURVEY',
      componentKey: 'SOL_SITE_SURVEY',
      contextSchema: { required: ['projectId'] }
    }
    vi.mocked(Views.getBusinessView).mockResolvedValue(survey)
    const selection = {
      view: survey,
      strategy: 'REFERENCE_EXISTING' as const,
      completion: { factCode: 'SURVEY_CONFIRMED', quantifier: 'ALL' as const }
    }
    const session = createBindingSaveSession()
    const result = await prepareTaskBinding(task, selection, session)
    expect(calls).toEqual([
      'create:WORK_BINDING',
      'publish:WORK_BINDING',
      'create:COMPLETION_RULE',
      'publish:COMPLETION_RULE',
      'create:TASK',
      'publish:TASK'
    ])
    expect(rows.get(result.completionRuleRevisionId!)?.payload).toEqual({
      predicate: 'BUSINESS_FACT',
      parameters: { factCode: 'SURVEY_CONFIRMED', quantifier: 'ALL' }
    })
    expect(rows.get(4)?.payload.predicate).toBe('TASK_NATIVE_STATUS')
    await prepareTaskBinding(task, selection, session)
    expect(Definitions.createDefinition).toHaveBeenCalledTimes(3)
  })
  it('fails closed for unknown context and unsupported create strategy', async () => {
    expect(() =>
      bindingContextMapping({ ...view, contextSchema: { required: ['unsupported'] } })
    ).toThrow('尚未接入')
    await expect(
      prepareTaskBinding(
        task,
        { view, strategy: 'CREATE_ON_FIRST_ACTION' as any },
        createBindingSaveSession()
      )
    ).rejects.toThrow('尚未支持')
    expect(Definitions.createDefinition).not.toHaveBeenCalled()
  })
})
