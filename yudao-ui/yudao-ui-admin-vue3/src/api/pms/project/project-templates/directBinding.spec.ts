import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as Views from '@/api/pms/platform/business-view'
import * as Forms from '@/api/pms/platform/dynamic-form'
import {
  bindingContextMapping,
  createBindingSaveSession,
  prepareTaskBinding
} from './directBinding'
import type { DesignerTaskNode } from './index'

vi.mock('@/config/axios', () => ({ default: {} }))
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

const task = (): DesignerTaskNode => ({
  nodeKey: 'task:T1',
  code: 'T1',
  name: '需求分析',
  stageCode: 'S1',
  workBinding: { type: 'TASK_NATIVE', parameters: {} },
  permission: { policyRef: 'PROJECT_TASK_NATIVE_DEFAULT' },
  completionRule: {
    expression: { predicate: 'TASK_NATIVE_STATUS', parameters: { requiredStatus: 'DONE' } }
  },
  source: {
    definitionRevisionId: 1,
    workBindingRevisionId: 2,
    permissionPolicyRevisionId: 3,
    completionRuleRevisionId: 4
  }
})

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(Views.getBusinessView).mockResolvedValue(view)
})

describe('PM-03 Designer V2 direct binding', () => {
  it('embeds BusinessView and completion semantics without creating DefinitionRevision assets', async () => {
    const original = task()
    const result = await prepareTaskBinding(
      original,
      {
        view,
        strategy: 'REFERENCE_EXISTING',
        completion: { factCode: 'SURVEY_CONFIRMED', quantifier: 'ALL' }
      },
      createBindingSaveSession()
    )

    expect(result.workBinding).toMatchObject({
      type: 'BUSINESS_COMPONENT',
      targetContextCode: 'SOL',
      targetObjectType: 'SITE_SURVEY',
      targetObjectKey: 'PROJECT_SITE_SURVEY',
      componentKey: 'SOL_SITE_SURVEY',
      parameters: {
        instanceResolutionStrategy: 'REFERENCE_EXISTING',
        contextMapping: { project: 'project' },
        businessViewRevisionId: view.id
      }
    })
    expect(result.workBinding.businessViewSnapshot).toMatchObject({ id: view.id, viewKey: view.viewKey })
    expect(result.completionRule.expression).toEqual({
      predicate: 'BUSINESS_FACT',
      parameters: { factCode: 'SURVEY_CONFIRMED', quantifier: 'ALL' }
    })
    expect(result.source?.workBindingRevisionId).toBeUndefined()
    expect(result.source?.completionRuleRevisionId).toBeUndefined()
    expect(original.workBinding.type).toBe('TASK_NATIVE')
    expect(original.completionRule.expression).toEqual({
      predicate: 'TASK_NATIVE_STATUS',
      parameters: { requiredStatus: 'DONE' }
    })
  })

  it('freezes the selected PRE-04 form source inside WorkBinding parameters', async () => {
    const requirementView = {
      ...view,
      entityType: 'REQUIREMENT_ANALYSIS',
      componentKey: 'PROJ_REQUIREMENT_ANALYSIS'
    }
    vi.mocked(Views.getBusinessView).mockResolvedValue(requirementView)
    vi.mocked(Forms.getRevision).mockResolvedValue({
      revisionId: 33,
      templateId: 22,
      revisionNo: 1,
      revisionVersion: 2,
      status: 'PUBLISHED'
    } as Forms.DynamicFormRevisionVO)

    const result = await prepareTaskBinding(
      task(),
      {
        view: requirementView,
        strategy: 'REFERENCE_EXISTING',
        requirementFormRevisionId: 33,
        completion: { factCode: 'REQUIREMENT_ANALYSIS_COMPLETED', quantifier: 'ALL' }
      },
      createBindingSaveSession()
    )

    expect(result.workBinding).toMatchObject({
      type: 'BUSINESS_OBJECT',
      targetContextCode: 'SOL',
      targetObjectType: 'REQUIREMENT_ANALYSIS',
      targetObjectKey: 'PRE_04_REQUIREMENT_ANALYSIS',
      parameters: {
        schemaVersion: 2,
        dynamicFormTemplateId: 22,
        dynamicFormTemplateRevisionId: 33,
        dynamicFormRevisionNo: 1,
        dynamicFormRevisionFactVersion: 2
      }
    })
  })

  it('rejects requirement binding without an exact published form source', async () => {
    const requirementView = { ...view, entityType: 'REQUIREMENT_ANALYSIS' }
    await expect(
      prepareTaskBinding(
        task(),
        { view: requirementView, strategy: 'REFERENCE_EXISTING' },
        createBindingSaveSession()
      )
    ).rejects.toThrow('已发布表单')

    vi.mocked(Views.getBusinessView).mockResolvedValue(requirementView)
    vi.mocked(Forms.getRevision).mockResolvedValue({
      revisionId: 33,
      templateId: 22,
      revisionNo: 1,
      revisionVersion: 2,
      status: 'DRAFT'
    } as Forms.DynamicFormRevisionVO)
    await expect(
      prepareTaskBinding(
        task(),
        { view: requirementView, strategy: 'REFERENCE_EXISTING', requirementFormRevisionId: 33 },
        createBindingSaveSession()
      )
    ).rejects.toThrow('已发布修订')
  })

  it('registers and publishes only the BusinessView when a component has no registration yet', async () => {
    const component: Views.BusinessViewComponentVO = {
      ...view,
      id: undefined as never,
      viewKey: undefined as never,
      revisionNo: undefined as never,
      status: undefined as never,
      version: undefined as never,
      allowedActions: undefined as never,
      entityType: 'DYNAMIC_FORM_INSTANCE',
      ownerContext: 'PLATFORM',
      viewSource: 'DYNAMIC_FORM',
      componentKey: 'PLATFORM_DYNAMIC_FORM'
    }
    const draftView = {
      ...view,
      id: '9223372036854775806',
      entityType: component.entityType,
      ownerContext: component.ownerContext,
      viewSource: component.viewSource,
      componentKey: component.componentKey,
      dynamicFormRevisionId: '9223372036854775805',
      status: 'DRAFT' as const,
      allowedActions: ['PUBLISH']
    }
    const published = { ...draftView, status: 'PUBLISHED' as const }
    vi.mocked(Views.createBusinessView).mockResolvedValue(draftView)
    vi.mocked(Views.getBusinessView).mockResolvedValue(draftView)
    vi.mocked(Views.publishBusinessView).mockResolvedValue(published)

    const result = await prepareTaskBinding(
      task(),
      {
        component,
        dynamicFormRevisionId: draftView.dynamicFormRevisionId,
        strategy: 'REFERENCE_EXISTING'
      },
      createBindingSaveSession()
    )

    expect(Views.createBusinessView).toHaveBeenCalledTimes(1)
    expect(Views.publishBusinessView).toHaveBeenCalledTimes(1)
    expect(result.workBinding.businessViewSnapshot).toMatchObject({ id: published.id })
    expect(result.workBinding.parameters?.businessViewRevisionId).toBe(published.id)
  })

  it('reuses the same BusinessView registration intent after a lost publish response', async () => {
    const component = { ...view, status: undefined as never, allowedActions: undefined as never } as Views.BusinessViewComponentVO
    const draftView = { ...view, status: 'DRAFT' as const, allowedActions: ['PUBLISH'] }
    vi.mocked(Views.createBusinessView).mockResolvedValue(draftView)
    vi.mocked(Views.getBusinessView).mockResolvedValue(draftView)
    vi.mocked(Views.publishBusinessView)
      .mockRejectedValueOnce(new Error('响应丢失'))
      .mockResolvedValueOnce(view)

    const session = createBindingSaveSession()
    const selection = { component, strategy: 'REFERENCE_EXISTING' as const }
    await expect(prepareTaskBinding(task(), selection, session)).rejects.toThrow('响应丢失')
    await prepareTaskBinding(task(), selection, session)

    expect(Views.createBusinessView).toHaveBeenCalledTimes(1)
    expect(Views.publishBusinessView).toHaveBeenCalledTimes(2)
    expect(vi.mocked(Views.publishBusinessView).mock.calls[0][2]).toBe(
      vi.mocked(Views.publishBusinessView).mock.calls[1][2]
    )
  })

  it('fails closed for unknown context and unsupported create strategy', async () => {
    expect(() => bindingContextMapping({ ...view, contextSchema: { required: ['unsupported'] } })).toThrow('尚未接入')
    await expect(
      prepareTaskBinding(
        task(),
        { view, strategy: 'CREATE_ON_FIRST_ACTION' as any },
        createBindingSaveSession()
      )
    ).rejects.toThrow('尚未支持')
  })
})
