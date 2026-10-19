import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import type { Task } from '@/api/pms/integration'
import TaskEditor from './TaskEditor.vue'

const api = vi.hoisted(() => ({
  getConnections: vi.fn(),
  getAdapters: vi.fn(),
  getTask: vi.fn(),
  getEhrTemplate: vi.fn(),
  getDppmsOrganizationTemplate: vi.fn(),
  getDppmsDirectOrganizationTemplate: vi.fn(),
  getDppmsAccountFoundationTemplate: vi.fn(),
  getDppmsDirectAccountTemplate: vi.fn(),
  getDppmsDirectDictionaryTemplate: vi.fn(),
  getCrmDirectCustomerTemplate: vi.fn(),
  getDppmsDirectProductTemplate: vi.fn(),
  getDppmsDirectExecutionOrderTemplate: vi.fn(),
  getDppmsDirectReceivableTemplate: vi.fn(),
  getDppmsDirectWarrantyTemplate: vi.fn(),
  getDppmsDirectProjectTemplate: vi.fn(),
  getDppmsDirectProductScopeTemplate: vi.fn(),
  getDppmsDirectRealProductTemplate: vi.fn(),
  getDppmsDirectDeviceBaseTemplate: vi.fn(),
  getDppmsDirectMemberHistoryTemplate: vi.fn(),
  getDppmsDirectInstallationTemplate: vi.fn(),
  getDppmsDirectFactoryHistoryTemplate: vi.fn(),
  getDppmsDirectSoftwareHistoryTemplate: vi.fn(),
  getDppmsDirectLicenseHistoryTemplate: vi.fn(),

  getDppmsDirectProjectFactsTemplate: vi.fn(),
  getDppmsDirectProjectRelationTemplate: vi.fn(),
  saveTask: vi.fn(),
  checkTaskConfiguration: vi.fn()
}))
const checks = vi.hoisted(() => ({ form: vi.fn(), source: vi.fn() }))
vi.mock('@/api/pms/integration', () => api)
vi.mock('./SourceEditor.vue', async () => {
  const { defineComponent, h } = await import('vue')
  return {
    default: defineComponent({
      setup(_, { expose }) {
        expose({ validate: checks.source })
        return () => h('div', '来源编辑器测试替身')
      }
    })
  }
})

const task: Task = {
  id: '10001',
  name: '组织同步',
  version: 7,
  definition: {
    adapter: 'ehr',
    connectionId: '20001',
    sourceSystem: 'DPPMS',
    mode: 'SNAPSHOT',
    missingPolicy: 'DISABLE',
    cron: '0 0 * * * ?',
    fullCron: '0 0 2 * * ?',
    overlapSeconds: 300,
    retryCount: 0,
    retryIntervalSeconds: 60,
    maxRows: 10000,
    maxBytes: 67108864,
    sources: []
  }
}
const mounted: Array<{ unmount: () => void }> = []
beforeEach(() => {
  vi.clearAllMocks()
  checks.form.mockResolvedValue(true)
  checks.source.mockReturnValue(true)
  api.checkTaskConfiguration.mockResolvedValue({ allowed: true })
  api.getConnections.mockResolvedValue({ list: [{ id: '20001', name: '来源' }] })
  api.getAdapters.mockResolvedValue([
    {
      key: 'ehr',
      objects: [],
      missingPolicies: ['DISABLE'],
      loadingModes: ['UPSERT', 'INSERT_ONLY', 'INSERT_IGNORE']
    }
  ])
  api.getTask.mockImplementation(async () => structuredClone(task))
  api.saveTask.mockResolvedValue('10001')
})
afterEach(() => mounted.splice(0).forEach((app) => app.unmount()))

const renderEditor = async () => {
  const Form = defineComponent({
    setup(_, { expose, slots }) {
      expose({ validate: checks.form })
      return () => h('form', slots.default?.())
    }
  })
  const components = Object.fromEntries(
    ['ElDialog', 'ElTabs', 'ElTabPane', 'ElInput', 'ElSelect', 'ElOption', 'ElInputNumber'].map(
      (name) => [name, passthrough]
    )
  )
  const { app, vm } = mount(TaskEditor, {}, { ...components, ElForm: Form })
  mounted.push(app)
  const state = (
    vm as unknown as {
      $: {
        setupState: {
          open: (id: string) => Promise<void>
          save: () => Promise<void>
          selectMappingReset: () => void
          syncLegacyPaging: () => void
          changeAdapter: () => Promise<void>
          section: string
          draft: Task
          visible: boolean
          saving: boolean
        }
      }
    }
  ).$.setupState
  await state.open('10001')
  await nextTick()
  return state
}

describe('task configuration groups preserve the existing save contract', () => {
  it('selecting mapping reset keeps target rows, enables source IDs and submits the complete definition', async () => {
    const state = await renderEditor()
    state.draft.definition.sources = [
      {
        object: 'COMPANY',
        sourceObject: 'ehr_company',
        sourceKey: 'compID',
        readMode: 'TABLE',
        table: 'ehr_company',
        columns: [],
        filters: [],
        mappings: [],
        parameters: {},
        syncPrimaryKey: false
      }
    ]
    state.draft.definition.clearBeforeLoad = true
    state.draft.definition.loadingMode = 'INSERT_IGNORE'
    state.draft.definition.resetMappingsBeforeLoad = true
    state.selectMappingReset()
    expect(state.draft.definition.clearBeforeLoad).toBe(false)
    expect(state.draft.definition.loadingMode).toBe('UPSERT')
    expect(state.draft.definition.sources[0].syncPrimaryKey).toBe(true)
    await state.save()
    expect(api.saveTask).toHaveBeenCalledWith(
      expect.objectContaining({
        definition: expect.objectContaining({
          resetMappingsBeforeLoad: true,
          clearBeforeLoad: false,
          loadingMode: 'UPSERT'
        })
      })
    )
  })
  it('keeps the draft open and does not insert when preflight identifies the existing task', async () => {
    const state = await renderEditor()
    api.checkTaskConfiguration.mockResolvedValue({
      allowed: false,
      existingTaskId: '10001',
      existingTaskName: '组织同步',
      message: '已有同来源任务'
    })
    await state.save()
    expect(api.saveTask).not.toHaveBeenCalled()
    expect(state.visible).toBe(true)
    expect(state.saving).toBe(false)
  })
  it('passes clear-before-load in the complete definition through preflight and save', async () => {
    const state = await renderEditor()
    state.draft.definition.clearBeforeLoad = true
    await state.save()
    expect(api.checkTaskConfiguration).toHaveBeenCalledWith(
      expect.objectContaining({ definition: expect.objectContaining({ clearBeforeLoad: true }) })
    )
    expect(api.saveTask).toHaveBeenCalledWith(
      expect.objectContaining({ definition: expect.objectContaining({ clearBeforeLoad: true }) })
    )
  })
  it('saves the complete task entity, including fields outside the active group', async () => {
    const state = await renderEditor()
    state.section = 'schedule'
    state.draft.definition.retryCount = 2
    await state.save()
    expect(api.saveTask).toHaveBeenCalledWith({
      ...task,
      definition: { ...task.definition, loadingMode: 'UPSERT', retryCount: 2,
        readStrategy: 'SNAPSHOT', evidencePolicy: 'FULL', fetchSize: 2000, chunkSize: 1000,
        restartPolicy: 'RESTART_ALL', queryTimeoutSeconds: 0, autoPaging: false }
    })
    expect(state.visible).toBe(false)
  })

  it('defaults old configurations to full evidence and clears reuse when leaving streaming', async () => {
    const state = await renderEditor()
    expect(state.draft.definition.evidencePolicy).toBe('FULL')
    state.draft.definition.evidencePolicy = 'REUSE_UNCHANGED'
    state.draft.definition.readStrategy = 'KEYSET_PAGING'
    state.syncLegacyPaging()
    expect(state.draft.definition.evidencePolicy).toBe('FULL')
    expect(state.draft.definition.autoPaging).toBe(true)
  })

  it('preserves an explicit reuse policy when opening a streaming task', async () => {
    api.getTask.mockResolvedValue({ ...structuredClone(task), definition: {
      ...structuredClone(task.definition), adapter: 'TABLE_MAPPING', missingPolicy: 'RETAIN',
      readStrategy: 'STREAMING_CURSOR', evidencePolicy: 'REUSE_UNCHANGED'
    } })
    const state = await renderEditor()
    expect(state.draft.definition.evidencePolicy).toBe('REUSE_UNCHANGED')
    state.syncLegacyPaging()
    expect(state.draft.definition.evidencePolicy).toBe('REUSE_UNCHANGED')
    expect(state.draft.definition.autoPaging).toBe(false)
  })

  it('resets reuse when switching to the product catalog adapter that keeps the draft fields', async () => {
    api.getAdapters.mockResolvedValue([
      { key: 'TABLE_MAPPING', objects: [], missingPolicies: ['RETAIN'], loadingModes: ['UPSERT'] },
      { key: 'DPPMS_PRODUCT_CATALOG', objects: [], missingPolicies: ['RETAIN'], loadingModes: ['UPSERT'] }
    ])
    api.getTask.mockResolvedValue({ ...structuredClone(task), definition: {
      ...structuredClone(task.definition), adapter: 'TABLE_MAPPING', missingPolicy: 'RETAIN',
      readStrategy: 'STREAMING_CURSOR', evidencePolicy: 'REUSE_UNCHANGED'
    } })
    const state = await renderEditor()
    state.draft.definition.adapter = 'DPPMS_PRODUCT_CATALOG'
    await state.changeAdapter()
    expect(state.draft.definition.readStrategy).toBe('STREAMING_CURSOR')
    expect(state.draft.definition.evidencePolicy).toBe('FULL')
  })

  it('returns to the basic group when its hidden required field is invalid', async () => {
    const state = await renderEditor()
    state.section = 'sources'
    checks.form.mockRejectedValue(new Error('required'))
    await state.save()
    expect(state.section).toBe('basic')
    expect(api.saveTask).not.toHaveBeenCalled()
  })

  it('returns to source configuration without submitting invalid mappings', async () => {
    const state = await renderEditor()
    state.section = 'schedule'
    checks.source.mockReturnValue(false)
    await state.save()
    expect(state.section).toBe('sources')
    expect(api.saveTask).not.toHaveBeenCalled()
  })

  it('retains the draft and version when the server rejects a stale save', async () => {
    const state = await renderEditor()
    state.draft.name = '待保存的任务名称'
    api.saveTask.mockRejectedValue(new Error('version conflict'))
    await expect(state.save()).rejects.toThrow('version conflict')
    expect(state.visible).toBe(true)
    expect(state.saving).toBe(false)
    expect(state.draft.name).toBe('待保存的任务名称')
    expect(state.draft.version).toBe(7)
  })
})


describe('P01 original and direct task types stay independent', () => {
  it('loads each dedicated template when switching types without saving or replacing the original task', async () => {
    const keys = ['EHR_ORGANIZATION', 'DPPMS_ORGANIZATION', 'DPPMS_ORGANIZATION_DIRECT']
    api.getAdapters.mockResolvedValue(keys.map((key) => ({
      key, label: key, objects: [], missingPolicies: ['RETAIN'], loadingModes: ['UPSERT']
    })))
    const definition = (adapter: string) => ({ ...structuredClone(task.definition), adapter, mode: 'ONCE', missingPolicy: 'RETAIN' })
    api.getEhrTemplate.mockImplementation(async () => definition(keys[0]))
    api.getDppmsOrganizationTemplate.mockImplementation(async () => definition(keys[1]))
    api.getDppmsDirectOrganizationTemplate.mockImplementation(async () => definition(keys[2]))
    const state = await renderEditor()
    for (const key of [keys[1], keys[2], keys[0], keys[1]]) {
      state.draft.definition.adapter = key
      await nextTick()
      await state.changeAdapter()
      expect(state.draft.definition.adapter).toBe(key)
      expect(state.draft.definition.connectionId).toBe('20001')
    }
    expect(api.getDppmsOrganizationTemplate).toHaveBeenCalledTimes(2)
    expect(api.getDppmsDirectOrganizationTemplate).toHaveBeenCalledTimes(1)
    expect(api.getEhrTemplate).toHaveBeenCalledTimes(1)
    expect(api.saveTask).not.toHaveBeenCalled()
    expect(task.definition.adapter).toBe('ehr')
  })
})

describe('P04 original and direct task types stay independent', () => {
  it('loads the approved original or independent direct template without saving on selection', async () => {
    const keys = ['DPPMS_ACCOUNT_FOUNDATION', 'DPPMS_ACCOUNT_FOUNDATION_DIRECT']
    api.getAdapters.mockResolvedValue(keys.map((key) => ({
      key, label: key, objects: [], missingPolicies: ['RETAIN'], loadingModes: ['UPSERT']
    })))
    const definition = (adapter: string) => ({ ...structuredClone(task.definition), adapter, mode: 'ONCE', missingPolicy: 'RETAIN' })
    api.getDppmsAccountFoundationTemplate.mockImplementation(async () => definition(keys[0]))
    api.getDppmsDirectAccountTemplate.mockImplementation(async () => definition(keys[1]))
    const state = await renderEditor()
    for (const key of [keys[0], keys[1], keys[0]]) {
      state.draft.definition.adapter = key
      await nextTick()
      await state.changeAdapter()
      expect(state.draft.definition.adapter).toBe(key)
      expect(state.draft.definition.connectionId).toBe('20001')
    }
    expect(api.getDppmsAccountFoundationTemplate).toHaveBeenCalledTimes(2)
    expect(api.getDppmsDirectAccountTemplate).toHaveBeenCalledTimes(1)
    expect(api.saveTask).not.toHaveBeenCalled()
  })
})

describe('P08 direct task selection', () => {
  it('loads the direct dictionary template without saving the original task', async () => {
    api.getAdapters.mockResolvedValue([{ key: 'DPPMS_DICTIONARIES_DIRECT', label: '字典直写', objects: [], missingPolicies: ['RETAIN'], loadingModes: ['UPSERT'] }])
    api.getDppmsDirectDictionaryTemplate.mockResolvedValue({ ...structuredClone(task.definition), adapter: 'DPPMS_DICTIONARIES_DIRECT', mode: 'ONCE', missingPolicy: 'RETAIN' })
    const state = await renderEditor()
    state.draft.definition.adapter = 'DPPMS_DICTIONARIES_DIRECT'
    await nextTick()
    await state.changeAdapter()
    expect(api.getDppmsDirectDictionaryTemplate).toHaveBeenCalledWith('20001')
    expect(state.draft.definition.adapter).toBe('DPPMS_DICTIONARIES_DIRECT')
    expect(state.draft.name).toContain('直写任务')
    expect(api.saveTask).not.toHaveBeenCalled()
  })
})

describe('P10 direct task selection', () => {
  it('keeps the CRM source identity when selecting the independent direct customer template', async () => {
    api.getAdapters.mockResolvedValue([{ key: 'CRM_CUSTOMER_DIRECT', label: 'CRM客户直写', objects: [], missingPolicies: ['RETAIN'], loadingModes: ['UPSERT'] }])
    api.getCrmDirectCustomerTemplate.mockResolvedValue({ ...structuredClone(task.definition), adapter: 'CRM_CUSTOMER_DIRECT', sourceSystem: 'CRM', mode: 'ONCE', missingPolicy: 'RETAIN' })
    const state = await renderEditor()
    state.draft.definition.adapter = 'CRM_CUSTOMER_DIRECT'
    await nextTick()
    await state.changeAdapter()
    expect(api.getCrmDirectCustomerTemplate).toHaveBeenCalledWith('20001')
    expect(state.draft.definition.adapter).toBe('CRM_CUSTOMER_DIRECT')
    expect(state.draft.definition.sourceSystem).toBe('CRM')
    expect(api.saveTask).not.toHaveBeenCalled()
  })
})

describe('P12 direct task selection', () => {
  it('loads the complete native-id product template without saving on selection', async () => {
    api.getAdapters.mockResolvedValue([{ key: 'DPPMS_PRODUCT_CATALOG_DIRECT', label: '产品补齐直写', objects: [], missingPolicies: ['RETAIN'], loadingModes: ['UPSERT'] }])
    api.getDppmsDirectProductTemplate.mockResolvedValue({ ...structuredClone(task.definition), adapter: 'DPPMS_PRODUCT_CATALOG_DIRECT', sourceSystem: 'DPPMS', mode: 'ONCE', missingPolicy: 'RETAIN' })
    const state = await renderEditor()
    state.draft.definition.adapter = 'DPPMS_PRODUCT_CATALOG_DIRECT'
    await nextTick()
    await state.changeAdapter()
    expect(api.getDppmsDirectProductTemplate).toHaveBeenCalledWith('20001')
    expect(state.draft.definition.adapter).toBe('DPPMS_PRODUCT_CATALOG_DIRECT')
    expect(api.saveTask).not.toHaveBeenCalled()
  })
})

describe('P13 direct task selection', () => {
  it('loads the reviewed native-id execution order template without saving on selection', async () => {
    api.getAdapters.mockResolvedValue([{ key: 'DPPMS_CRM_EXECUTION_ORDER_DIRECT', label: '执行单直写', objects: [], missingPolicies: ['RETAIN'], loadingModes: ['UPSERT'] }])
    api.getDppmsDirectExecutionOrderTemplate.mockResolvedValue({ ...structuredClone(task.definition), adapter: 'DPPMS_CRM_EXECUTION_ORDER_DIRECT', sourceSystem: 'DPPMS', mode: 'ONCE', missingPolicy: 'RETAIN' })
    const state = await renderEditor()
    state.draft.definition.adapter = 'DPPMS_CRM_EXECUTION_ORDER_DIRECT'
    await nextTick()
    await state.changeAdapter()
    expect(state.draft.name).toBe('P13 执行单主档（直写任务）')
    expect(api.getDppmsDirectExecutionOrderTemplate).toHaveBeenCalledWith('20001')
    expect(state.draft.definition.adapter).toBe('DPPMS_CRM_EXECUTION_ORDER_DIRECT')
    expect(api.saveTask).not.toHaveBeenCalled()
  })
})

describe('P24 direct task selection', () => {
  it('loads the reviewed native-id receivable template without saving on selection', async () => {
    api.getAdapters.mockResolvedValue([{ key: 'DPPMS_CONTRACT_RECEIVABLE_DIRECT', label: '回款信息直写', objects: [], missingPolicies: ['RETAIN'], loadingModes: ['UPSERT'] }])
    api.getDppmsDirectReceivableTemplate.mockResolvedValue({ ...structuredClone(task.definition), adapter: 'DPPMS_CONTRACT_RECEIVABLE_DIRECT', sourceSystem: 'DPPMS', mode: 'ONCE', missingPolicy: 'RETAIN' })
    const state = await renderEditor()
    state.draft.definition.adapter = 'DPPMS_CONTRACT_RECEIVABLE_DIRECT'
    await nextTick()
    await state.changeAdapter()
    expect(state.draft.name).toBe('P24 合同应收信息（直写任务）')
    expect(api.getDppmsDirectReceivableTemplate).toHaveBeenCalledWith('20001')
    expect(state.draft.definition.adapter).toBe('DPPMS_CONTRACT_RECEIVABLE_DIRECT')
    expect(api.saveTask).not.toHaveBeenCalled()
  })
})


describe('P25 direct task selection', () => {
  it('loads the reviewed native-id warranty renewal template without saving on selection', async () => {
    api.getAdapters.mockResolvedValue([{ key: 'DPPMS_WARRANTY_RENEWAL_DIRECT', label: '维保延期直写', objects: [], missingPolicies: ['RETAIN'], loadingModes: ['UPSERT'] }])
    api.getDppmsDirectWarrantyTemplate.mockResolvedValue({ ...structuredClone(task.definition), adapter: 'DPPMS_WARRANTY_RENEWAL_DIRECT', sourceSystem: 'DPPMS', mode: 'ONCE', missingPolicy: 'RETAIN' })
    const state = await renderEditor()
    state.draft.definition.adapter = 'DPPMS_WARRANTY_RENEWAL_DIRECT'
    await nextTick()
    await state.changeAdapter()
    expect(state.draft.name).toBe('P25 设备续保记录（直写任务）')
    expect(api.getDppmsDirectWarrantyTemplate).toHaveBeenCalledWith('20001')
    expect(state.draft.definition.adapter).toBe('DPPMS_WARRANTY_RENEWAL_DIRECT')
    expect(api.saveTask).not.toHaveBeenCalled()
  })
})

describe('P16 direct task selection', () => {
  it('loads the reviewed native-id project master template without saving on selection', async () => {
    api.getAdapters.mockResolvedValue([{ key: 'DPPMS_PROJECT_MASTER_DIRECT', label: '项目直写', objects: [], missingPolicies: ['RETAIN'], loadingModes: ['UPSERT'] }])
    api.getDppmsDirectProjectTemplate.mockResolvedValue({ ...structuredClone(task.definition), adapter: 'DPPMS_PROJECT_MASTER_DIRECT', sourceSystem: 'DPPMS', mode: 'ONCE', missingPolicy: 'RETAIN' })
    const state = await renderEditor()
    state.draft.definition.adapter = 'DPPMS_PROJECT_MASTER_DIRECT'
    await nextTick()
    await state.changeAdapter()
    expect(state.draft.name).toBe('P16 项目主档与来源身份（直写任务）')
    expect(api.getDppmsDirectProjectTemplate).toHaveBeenCalledWith('20001')
    expect(state.draft.definition.adapter).toBe('DPPMS_PROJECT_MASTER_DIRECT')
    expect(api.saveTask).not.toHaveBeenCalled()
  })
})

describe('P18 direct task selection', () => {
  it('loads the reviewed reviewed project contract relation template without saving on selection', async () => {
    api.getAdapters.mockResolvedValue([{ key: 'DPPMS_PROJECT_CONTRACT_RELATION_DIRECT', label: '项目直写', objects: [], missingPolicies: ['RETAIN'], loadingModes: ['UPSERT'] }])
    api.getDppmsDirectProjectRelationTemplate.mockResolvedValue({ ...structuredClone(task.definition), adapter: 'DPPMS_PROJECT_CONTRACT_RELATION_DIRECT', sourceSystem: 'DPPMS', mode: 'ONCE', missingPolicy: 'RETAIN' })
    const state = await renderEditor()
    state.draft.definition.adapter = 'DPPMS_PROJECT_CONTRACT_RELATION_DIRECT'
    await nextTick()
    await state.changeAdapter()
    expect(state.draft.name).toBe('P18 项目合同关系（直写任务）')
    expect(api.getDppmsDirectProjectRelationTemplate).toHaveBeenCalledWith('20001')
    expect(state.draft.definition.adapter).toBe('DPPMS_PROJECT_CONTRACT_RELATION_DIRECT')
    expect(api.saveTask).not.toHaveBeenCalled()
  })
})

describe('P19 direct task selection', () => {
  it('loads the reviewed native-id project facts template without saving on selection', async () => {
    api.getAdapters.mockResolvedValue([{ key: 'DPPMS_PROJECT_FACTS_DIRECT', label: '项目直写', objects: [], missingPolicies: ['RETAIN'], loadingModes: ['UPSERT'] }])
    api.getDppmsDirectProjectFactsTemplate.mockResolvedValue({ ...structuredClone(task.definition), adapter: 'DPPMS_PROJECT_FACTS_DIRECT', sourceSystem: 'DPPMS', mode: 'ONCE', missingPolicy: 'RETAIN' })
    const state = await renderEditor()
    state.draft.definition.adapter = 'DPPMS_PROJECT_FACTS_DIRECT'
    await nextTick()
    await state.changeAdapter()
    expect(state.draft.name).toBe('P19 项目相关方与状态（直写任务）')
    expect(api.getDppmsDirectProjectFactsTemplate).toHaveBeenCalledWith('20001')
    expect(state.draft.definition.adapter).toBe('DPPMS_PROJECT_FACTS_DIRECT')
    expect(api.saveTask).not.toHaveBeenCalled()
  })
})

describe('P22 direct task selection', () => {
  it('loads the reviewed native-id product scope template without saving on selection', async () => {
    api.getAdapters.mockResolvedValue([{ key: 'DPPMS_PROJECT_PRODUCT_SCOPE_DIRECT', label: '项目直写', objects: [], missingPolicies: ['RETAIN'], loadingModes: ['UPSERT'] }])
    api.getDppmsDirectProductScopeTemplate.mockResolvedValue({ ...structuredClone(task.definition), adapter: 'DPPMS_PROJECT_PRODUCT_SCOPE_DIRECT', sourceSystem: 'DPPMS', mode: 'ONCE', missingPolicy: 'RETAIN' })
    const state = await renderEditor()
    state.draft.definition.adapter = 'DPPMS_PROJECT_PRODUCT_SCOPE_DIRECT'
    await nextTick()
    await state.changeAdapter()
    expect(state.draft.name).toBe('P22 项目产品实施范围（直写任务）')
    expect(api.getDppmsDirectProductScopeTemplate).toHaveBeenCalledWith('20001')
    expect(state.draft.definition.adapter).toBe('DPPMS_PROJECT_PRODUCT_SCOPE_DIRECT')
    expect(api.saveTask).not.toHaveBeenCalled()
  })
})

describe('P20 direct task selection', () => {
  it('loads the reviewed native-id real product template without saving on selection', async () => {
    api.getAdapters.mockResolvedValue([{ key: 'DPPMS_PROJECT_REAL_PRODUCT_DIRECT', label: '项目直写', objects: [], missingPolicies: ['RETAIN'], loadingModes: ['UPSERT'] }])
    api.getDppmsDirectRealProductTemplate.mockResolvedValue({ ...structuredClone(task.definition), adapter: 'DPPMS_PROJECT_REAL_PRODUCT_DIRECT', sourceSystem: 'DPPMS', mode: 'ONCE', missingPolicy: 'RETAIN' })
    const state = await renderEditor()
    state.draft.definition.adapter = 'DPPMS_PROJECT_REAL_PRODUCT_DIRECT'
    await nextTick()
    await state.changeAdapter()
    expect(state.draft.name).toBe('P20 项目真实产品清单（直写任务）')
    expect(api.getDppmsDirectRealProductTemplate).toHaveBeenCalledWith('20001')
    expect(state.draft.definition.adapter).toBe('DPPMS_PROJECT_REAL_PRODUCT_DIRECT')
    expect(api.saveTask).not.toHaveBeenCalled()
  })
})

describe('P30 direct task selection', () => {
  it('loads the reviewed approved P30 template without saving on selection', async () => {
    api.getAdapters.mockResolvedValue([{ key: 'DPPMS_DEVICE_BOOTSTRAP_DIRECT', label: '项目直写', objects: [], missingPolicies: ['RETAIN'], loadingModes: ['UPSERT'] }])
    api.getDppmsDirectDeviceBaseTemplate.mockResolvedValue({ ...structuredClone(task.definition), adapter: 'DPPMS_DEVICE_BOOTSTRAP_DIRECT', sourceSystem: 'DPPMS', mode: 'ONCE', missingPolicy: 'RETAIN' })
    const state = await renderEditor()
    state.draft.definition.adapter = 'DPPMS_DEVICE_BOOTSTRAP_DIRECT'
    await nextTick()
    await state.changeAdapter()
    expect(state.draft.name).toBe('P30 设备基础（直写任务）')
    expect(api.getDppmsDirectDeviceBaseTemplate).toHaveBeenCalledWith('20001')
    expect(state.draft.definition.adapter).toBe('DPPMS_DEVICE_BOOTSTRAP_DIRECT')
    expect(api.saveTask).not.toHaveBeenCalled()
  })
})

describe('P31 direct task selection', () => {
  it('loads the reviewed approved P31 template without saving on selection', async () => {
    api.getAdapters.mockResolvedValue([{ key: 'DPPMS_MEMBER_HISTORY_DIRECT', label: '项目直写', objects: [], missingPolicies: ['RETAIN'], loadingModes: ['UPSERT'] }])
    api.getDppmsDirectMemberHistoryTemplate.mockResolvedValue({ ...structuredClone(task.definition), adapter: 'DPPMS_MEMBER_HISTORY_DIRECT', sourceSystem: 'DPPMS', mode: 'ONCE', missingPolicy: 'RETAIN' })
    const state = await renderEditor()
    state.draft.definition.adapter = 'DPPMS_MEMBER_HISTORY_DIRECT'
    await nextTick()
    await state.changeAdapter()
    expect(state.draft.name).toBe('P31 成员与联系人历史（直写任务）')
    expect(api.getDppmsDirectMemberHistoryTemplate).toHaveBeenCalledWith('20001')
    expect(state.draft.definition.adapter).toBe('DPPMS_MEMBER_HISTORY_DIRECT')
    expect(api.saveTask).not.toHaveBeenCalled()
  })
})

describe('P33 direct task selection', () => {
  it('loads the reviewed approved P33 template without saving on selection', async () => {
    api.getAdapters.mockResolvedValue([{ key: 'DPPMS_INSTALLATION_HISTORY_DIRECT', label: '项目直写', objects: [], missingPolicies: ['RETAIN'], loadingModes: ['UPSERT'] }])
    api.getDppmsDirectInstallationTemplate.mockResolvedValue({ ...structuredClone(task.definition), adapter: 'DPPMS_INSTALLATION_HISTORY_DIRECT', sourceSystem: 'DPPMS', mode: 'ONCE', missingPolicy: 'RETAIN' })
    const state = await renderEditor()
    state.draft.definition.adapter = 'DPPMS_INSTALLATION_HISTORY_DIRECT'
    await nextTick()
    await state.changeAdapter()
    expect(state.draft.name).toBe('P33 普通安装历史（直写任务）')
    expect(api.getDppmsDirectInstallationTemplate).toHaveBeenCalledWith('20001')
    expect(state.draft.definition.adapter).toBe('DPPMS_INSTALLATION_HISTORY_DIRECT')
    expect(api.saveTask).not.toHaveBeenCalled()
  })
})

describe('P34 direct task selection', () => {
  it('loads the reviewed approved P34 template without saving on selection', async () => {
    api.getAdapters.mockResolvedValue([{ key: 'DPPMS_FACTORY_HISTORY_DIRECT', label: '项目直写', objects: [], missingPolicies: ['RETAIN'], loadingModes: ['UPSERT'] }])
    api.getDppmsDirectFactoryHistoryTemplate.mockResolvedValue({ ...structuredClone(task.definition), adapter: 'DPPMS_FACTORY_HISTORY_DIRECT', sourceSystem: 'DPPMS', mode: 'ONCE', missingPolicy: 'RETAIN' })
    const state = await renderEditor()
    state.draft.definition.adapter = 'DPPMS_FACTORY_HISTORY_DIRECT'
    await nextTick()
    await state.changeAdapter()
    expect(state.draft.name).toBe('P34 出厂版本历史（直写任务）')
    expect(api.getDppmsDirectFactoryHistoryTemplate).toHaveBeenCalledWith('20001')
    expect(state.draft.definition.adapter).toBe('DPPMS_FACTORY_HISTORY_DIRECT')
    expect(api.saveTask).not.toHaveBeenCalled()
  })
})

describe('P35 direct task selection', () => {
  it('loads the reviewed approved P35 template without saving on selection', async () => {
    api.getAdapters.mockResolvedValue([{ key: 'DPPMS_SOFTWARE_HISTORY_DIRECT', label: '项目直写', objects: [], missingPolicies: ['RETAIN'], loadingModes: ['UPSERT'] }])
    api.getDppmsDirectSoftwareHistoryTemplate.mockResolvedValue({ ...structuredClone(task.definition), adapter: 'DPPMS_SOFTWARE_HISTORY_DIRECT', sourceSystem: 'DPPMS', mode: 'ONCE', missingPolicy: 'RETAIN' })
    const state = await renderEditor()
    state.draft.definition.adapter = 'DPPMS_SOFTWARE_HISTORY_DIRECT'
    await nextTick()
    await state.changeAdapter()
    expect(state.draft.name).toBe('P35 软件批次与版本历史（直写任务）')
    expect(api.getDppmsDirectSoftwareHistoryTemplate).toHaveBeenCalledWith('20001')
    expect(state.draft.definition.adapter).toBe('DPPMS_SOFTWARE_HISTORY_DIRECT')
    expect(api.saveTask).not.toHaveBeenCalled()
  })
})

describe('P37 direct task selection', () => {
  it('loads the reviewed approved P37 template without saving on selection', async () => {
    api.getAdapters.mockResolvedValue([{ key: 'DPPMS_SECURE_LICENSE_DIRECT', label: '项目直写', objects: [], missingPolicies: ['RETAIN'], loadingModes: ['UPSERT'] }])
    api.getDppmsDirectLicenseHistoryTemplate.mockResolvedValue({ ...structuredClone(task.definition), adapter: 'DPPMS_SECURE_LICENSE_DIRECT', sourceSystem: 'DPPMS', mode: 'ONCE', missingPolicy: 'RETAIN' })
    const state = await renderEditor()
    state.draft.definition.adapter = 'DPPMS_SECURE_LICENSE_DIRECT'
    await nextTick()
    await state.changeAdapter()
    expect(state.draft.name).toBe('P37 受控许可历史（直写任务）')
    expect(api.getDppmsDirectLicenseHistoryTemplate).toHaveBeenCalledWith('20001')
    expect(state.draft.definition.adapter).toBe('DPPMS_SECURE_LICENSE_DIRECT')
    expect(api.saveTask).not.toHaveBeenCalled()
  })
})
