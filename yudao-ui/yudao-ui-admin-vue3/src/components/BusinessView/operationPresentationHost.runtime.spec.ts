// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, shallowRef } from 'vue'
import { mount } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import { inspectOperationCapabilities } from '@/api/pms/project/execution-operations'
import { editingTargetKey, useOperationHost } from './operationHost'
import type { BusinessViewTarget } from './registry'

vi.mock('@/api/pms/project/execution-operations', () => ({ inspectOperationCapabilities: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: { post: vi.fn() } }))
vi.mock('@/config/axios/service', () => ({ service: { defaults: { transformResponse: [] } } }))
const execution = { task: { projectId: '1', taskId: '2', executionContractId: '3', contractVersion: 1,
  planVersionId: '4', executionId: '5', executionVersion: 1 }, stage: null }
const capability = (status = 'AVAILABLE') => ({
  node: { projectId: '1', id: '2', kind: 'TASK', code: 'T', name: 'Task', status: 'IN_PROGRESS' },
  execution, reason: null, presentation: { status }, ownerFactVersion: 'v1',
  actions: [{ operationCode: 'SOL.SITE_SURVEY.CREATE', operationVersion: 1, label: 'Create',
    ownerPermitted: true, executionPermitted: true, runtimeAvailable: true, allowed: true,
    pre: { outcome: 'NO_ADDITIONAL_RULE' }, post: { outcome: 'NOT_EVALUATED' } }]
})
function harness() {
  const active = shallowRef({
    registration: { id: '7', componentKey: 'SOL_SITE_SURVEY', componentVersion: '1',
      ownerContext: 'SOL', entityType: 'SITE_SURVEY', viewSource: 'PAGE', status: 'PUBLISHED' },
    resolvedContext: { project: { id: '1' }, taskId: '2', taskExecution: execution.task },
    allowedActions: ['QUERY', 'CREATE', 'FILE_WRITE']
  } as unknown as BusinessViewTarget)
  let state!: ReturnType<typeof useOperationHost>
  const wrapper = mount(defineComponent({ setup() {
    state = useOperationHost(active, vi.fn()); return () => h('div')
  } }))
  return { active, wrapper, get state() { return state } }
}
const flushPromises = async () => {
  for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() }
}
beforeEach(() => vi.clearAllMocks())
describe('capability presentation is independent from business authorization', () => {
  for (const status of ['READ_ONLY', 'UNAVAILABLE']) {
    it(`honors fresh ${status} without changing Owner permissions or the editing client`, async () => {
      vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability() as never)
      const view = harness()
      try {
        await flushPromises()
        const client = view.state.client.value, key = editingTargetKey(view.active.value)
        expect(view.state.allowedActions.value).toContain('CREATE')
        vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability(status) as never)
        await view.state.refresh()
        expect(view.state.allowedActions.value).toEqual(['QUERY'])
        expect(view.state.presentationReadonly.value).toBe(true)
        expect(view.state.client.value).toBe(client)
        expect(view.state.requiresReopen.value).toBe(false)
        expect(editingTargetKey(view.active.value)).toBe(key)
        expect(view.state.observation.value?.actions[0].ownerPermitted).toBe(true)
        expect(view.state.observation.value?.actions[0].allowed).toBe(true)
        expect(view.active.value.registration.status).toBe('PUBLISHED')
        vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability() as never)
        await view.state.refresh()
        expect(view.state.presentationReadonly.value).toBe(false)
        expect(view.state.allowedActions.value).toContain('CREATE')
        expect(view.state.client.value).toBe(client)
      } finally { view.wrapper.app.unmount() }
    })
  }
  it('retains only readable actions when presentation is missing', async () => {
    vi.mocked(inspectOperationCapabilities).mockResolvedValue({ ...capability(), presentation: null } as never)
    const view = harness()
    try {
      await flushPromises()
      expect(view.state.presentationReadonly.value).toBe(true)
      expect(view.state.allowedActions.value).toEqual(['QUERY'])
      expect(view.state.client.value).toBeDefined()
    } finally { view.wrapper.app.unmount() }
  })
})
