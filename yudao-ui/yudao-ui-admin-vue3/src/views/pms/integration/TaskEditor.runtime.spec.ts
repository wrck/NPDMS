import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import type { Task } from '@/api/pms/integration'
import TaskEditor from './TaskEditor.vue'

const api = vi.hoisted(() => ({
  getConnections: vi.fn(),
  getAdapters: vi.fn(),
  getTask: vi.fn(),
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
      definition: { ...task.definition, loadingMode: 'UPSERT', retryCount: 2 }
    })
    expect(state.visible).toBe(false)
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
