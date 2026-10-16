import { defineComponent, h, inject, nextTick, reactive, ref } from 'vue'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import Host from './NativeBusinessHost.vue'
import { getProject } from '@/api/pms/project/projects'
import { createProjectBusinessApi } from '@/api/pms/platform/business'
import { createNativeBusinessBridge } from './nativeBusinessBridge'
import { nativeBusinessKey } from './nativeBusinessContext'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn(), onBeforeRouteUpdate: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: {} }))
vi.mock('@/config/axios/service', () => ({ service: {} }))
vi.mock('@/utils', () => ({ generateUUID: () => 'test-key' }))
vi.mock('../ProjectBusinessFieldConfiguration.vue', () => ({ default: defineComponent({ setup: () => () => null }) }))
vi.mock('@/utils/auth', () => ({ getCurrentUserId: () => 42, getTenantId: () => 1, getVisitTenantId: () => undefined }))
vi.mock('@/api/pms/project/projects', () => ({ getProject: vi.fn() }))
vi.mock('@/api/pms/platform/business', () => ({ createProjectBusinessApi: vi.fn(base => ({ base,
  model: vi.fn(async () => ({ fields: [], operations: [] })), page: vi.fn(async () => ({ list: [], total: 0 })),
  form: vi.fn(async () => ({ entity: { available: true, fieldValues: { projectId: 9 }, ref: { entityId: 33 } } })) })) }))
vi.mock('./nativeBusinessBridge', async importOriginal => {
  const original = await importOriginal<typeof import('./nativeBusinessBridge')>()
  return { ...original, createNativeBusinessBridge: vi.fn(api => ({ api, preparing: ref(false), state: {
    error: ref(''), pending: ref(undefined), executing: ref(false), receipt: ref(undefined) } })) }
})
vi.mock('@/views/pms/delivery-business/requirement-analysis/entity/EntityPanel.vue', () => ({ __esModule: true, default: defineComponent({
  props: ['project'], setup: props => () => h('article', `${props.project.id}:${props.project.projectName}`)
}) }))
vi.mock('@/views/pms/project/project-master-detail/components/ProjectSchedulePanel.vue', () => ({ __esModule: true, default: defineComponent({
  props: ['project', 'readonly'], emits: ['changed', 'open-task'],
  setup(props, { emit, expose }) {
    expose({ requestLeave: async () => false })
    return () => [h('article', `${props.project.id}:${props.readonly}`), h('button', { onClick: () => emit('changed') }, '刷新计划')]
  }
}) }))

const apps: { unmount: () => void }[] = []
afterEach(() => apps.splice(0).forEach(app => app.unmount()))
beforeEach(() => { vi.clearAllMocks(); vi.mocked(getProject).mockReset() })
const flush = async () => {
  for (let i = 0; i < 10; i++) { await Promise.resolve(); await nextTick() }
  await vi.dynamicImportSettled()
  for (let i = 0; i < 4; i++) { await Promise.resolve(); await nextTick() }
}
const base = { apiBase: '/api/v1/pms/requirement-analysis-business', stableCode: 'SOL_REQUIREMENT_ANALYSIS_REVISION', allowedActions: ['QUERY'] }

it('reuses the matching authorized project while preserving its exact identity', async () => {
  const initialProject = { id: '9007199254740993', projectName: '当前项目' }
  const view = mount(Host, { ...base, scopeProjectId: initialProject.id, initialProject })
  apps.push(view.app); await flush()
  expect(getProject).not.toHaveBeenCalled()
  expect(textOf(view.root)).toContain('9007199254740993:当前项目')
})

it('does not render a foreign project when the actual project lookup is forbidden', async () => {
  vi.mocked(getProject).mockRejectedValue(new Error('403'))
  const view = mount(Host, { ...base, scopeProjectId: 9, initialProject: { id: 10, projectName: '其他项目' } })
  apps.push(view.app); await flush()
  expect(getProject).toHaveBeenCalledWith(9)
  expect(textOf(view.root)).toContain('项目上下文读取失败')
  expect(textOf(view.root)).not.toContain('其他项目')
})

it('ignores an older lookup after switching to another authorized project', async () => {
  let resolve!: (value: any) => void
  vi.mocked(getProject).mockReturnValue(new Promise(yes => { resolve = yes }))
  const state = reactive({ scopeProjectId: 9, initialProject: undefined as any })
  const view = mount(defineComponent({ setup: () => () => h(Host, { ...base, ...state }) }))
  apps.push(view.app); await flush()
  state.initialProject = { id: 10, projectName: '新项目' }; state.scopeProjectId = 10
  await flush()
  resolve({ id: 9, projectName: '旧项目' }); await flush()
  expect(getProject).toHaveBeenCalledTimes(1)
  expect(textOf(view.root)).toContain('10:新项目')
  expect(textOf(view.root)).not.toContain('旧项目')
})

it('passes only the schedule panel contract without serializing extraneous-attribute warnings', async () => {
  const warning = vi.spyOn(console, 'warn').mockImplementation(() => {})
  try {
    const view = mount(Host, { apiBase: '/api/v1/pms/stage-plan-business', stableCode: 'SOL_STAGE_PLAN_BATCH',
      scopeProjectId: 9, initialProject: { id: 9 }, readonly: true, allowedActions: ['QUERY'] })
    apps.push(view.app); await flush()
    expect(textOf(view.root)).toContain('9:true')
    expect(getProject).not.toHaveBeenCalled()
    expect(await (view.vm as any).requestLeave()).toBe(false)
    expect(warning.mock.calls.filter(([message]) => String(message).includes('Extraneous'))).toEqual([])
  } finally { warning.mockRestore() }
})

it('hands resolved initial state to both native adapters without starting duplicate requests', async () => {
  const page = (stableCode: string, id: number) => ({ request: { projectId: 9, initialEntityId: id },
    model: { stableCode, fields: [], operations: [] }, configuration: { fields: [], version: 1 }, pageSize: 100,
    records: { list: [{ ref: { entityId: id }, available: true, fieldValues: { projectId: 9 } }], total: 1 },
    forms: [{ entity: { ref: { entityId: id }, available: true, fieldValues: { projectId: 9 } }, context: {}, definitions: [] }],
    relatedPages: [], context: {} })
  const initialization = { ...page('SOL_STAGE_PLAN_BATCH', 33), relatedPages: [page('SOL_CONSTRUCTION_PLAN', 28)] }
  const probe = defineComponent({ setup() { inject(nativeBusinessKey)!.getBridge('duration'); return () => null } })
  const wrapper = defineComponent({ setup: () => () => h(Host, {
    apiBase: '/api/v1/pms/stage-plan-business', stableCode: 'SOL_STAGE_PLAN_BATCH', initialProject: { id: 9 },
    scopeProjectId: 9, initialEntityId: 33, readonly: true, allowedActions: ['QUERY'], initialization
  } as any, { default: () => h(probe) }) })
  const view = mount(wrapper, {}); apps.push(view.app); await flush()
  expect(createNativeBusinessBridge).toHaveBeenCalledTimes(2)
  const raw = vi.mocked(createProjectBusinessApi).mock.results.map(result => result.value)
  const clients = vi.mocked(createNativeBusinessBridge).mock.calls.map(([api]) => api)
  await clients[0].model(); await clients[1].model()
  await clients[0].page(1, 100, [], undefined, 9); await clients[1].page(1, 1, [], undefined, 9)
  expect((await clients[0].form(33)).entity.ref.entityId).toBe(33)
  expect((await clients[1].form(28)).entity.ref.entityId).toBe(28)
  for (const api of raw) { expect(api.model).not.toHaveBeenCalled(); expect(api.page).not.toHaveBeenCalled(); expect(api.form).not.toHaveBeenCalled() }
  await clients[0].page(1, 100, [], undefined, 9)
  expect(raw[0].page).toHaveBeenCalledTimes(1)
})
