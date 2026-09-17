// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, shallowRef } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { inspectOperationCapabilities } from '@/api/pms/project/execution-operations'
import request from '@/config/axios'
import { editingTargetKey, useOperationHost } from './operationHost'
import { selectionClient } from './operationClient'
import type { BusinessViewTarget } from './registry'

vi.mock('@/api/pms/project/execution-operations', () => ({ inspectOperationCapabilities: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: { post: vi.fn() } }))
vi.mock('@/config/axios/service', () => ({ service: { defaults: { transformResponse: [] } } }))
const selection = (round = '90') => ({ task: { projectId: '1', taskId: '2', executionContractId: '3',
  contractVersion: 1, planVersionId: '4', executionId: round, executionVersion: 1 }, stage: null })
const target = () => ({ registration: { id: '7', componentKey: 'SOL_SITE_SURVEY', componentVersion: '1',
  ownerContext: 'SOL', entityType: 'SITE_SURVEY', viewSource: 'PAGE', status: 'PUBLISHED' },
  resolvedContext: { project: { id: '1' }, taskId: '2', taskExecution: selection().task },
  allowedActions: ['QUERY', 'CREATE', 'UPDATE', 'CONFIRM'] }) as unknown as BusinessViewTarget
const capability = (reason: string | null = null, round = '90') => ({
  node: { projectId: '1', id: '2', kind: 'TASK', code: 'T', name: '任务', status: 'IN_PROGRESS' },
  execution: selection(round), reason, presentation: { status: 'AVAILABLE' }, ownerFactVersion: 'v1',
  actions: [{ operationCode: 'SOL.SITE_SURVEY.CREATE', operationVersion: 1, label: '新增',
    ownerPermitted: true, executionPermitted: true, runtimeAvailable: true, allowed: true,
    pre: { outcome: 'NO_ADDITIONAL_RULE' }, post: { outcome: 'NOT_EVALUATED' } }]
})
function harness() {
  const active = shallowRef(target()), changed = vi.fn()
  let state!: ReturnType<typeof useOperationHost>
  const wrapper = mount(defineComponent({ setup() { state = useOperationHost(active, changed); return () => h('div') } }))
  return { active, changed, wrapper, get state() { return state } }
}
beforeEach(() => vi.clearAllMocks())
describe('retained Owner view and execution routing', () => {
  it('does not mount a usable client until inspection resolves; queries do not call commands', async () => {
    vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability() as never)
    const view = harness()
    expect(view.state.mode.value).toBe('CHECKING')
    await flushPromises()
    expect(view.state.mode.value).toBe('CONTROLLED')
    expect(selectionClient({ task: view.state.decorated.value.taskExecution })).toBe(view.state.client.value)
    expect(view.changed).not.toHaveBeenCalled()
    view.wrapper.unmount()
  })
  it('only a positively identified legacy binding selects legacy routing', async () => {
    vi.mocked(inspectOperationCapabilities).mockRejectedValue(new Error('network'))
    const view = harness(); await flushPromises()
    expect(view.state.mode.value).toBe('CHECKING')
    expect(view.state.allowedActions.value).toEqual(['QUERY'])
    vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability('LEGACY_BINDING') as never)
    await view.state.refresh()
    expect(view.state.mode.value).toBe('LEGACY')
    expect(view.state.client.value).toBeUndefined()
    view.wrapper.unmount()
  })
  it('requires explicit reopen on legacy to controlled transition, never silently changes an existing form', async () => {
    vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability('LEGACY_BINDING') as never)
    const view = harness(); await flushPromises()
    vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability() as never)
    await view.state.refresh()
    expect(view.state.requiresReopen.value).toBe(true)
    expect(view.state.allowedActions.value).toEqual(['QUERY'])
    await view.state.reopen()
    expect(view.state.requiresReopen.value).toBe(false)
    expect(view.state.mode.value).toBe('CONTROLLED')
    view.wrapper.unmount()
  })
  it('invalidates the old command client on rework without changing the editing identity', async () => {
    vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability() as never)
    const view = harness(); await flushPromises()
    const old = view.state.client.value!, key = editingTargetKey(view.active.value)
    vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability(null, '91') as never)
    await view.state.refresh()
    expect(old.matches(selection())).toBe(false)
    expect(view.state.client.value).toBe(old)
    expect(view.state.requiresReopen.value).toBe(true)
    expect(view.state.allowedActions.value).toEqual(['QUERY'])
    expect(view.state.decorated.value.taskExecution?.executionId).toBe('90')
    expect(editingTargetKey(view.active.value)).toBe(key)
    await view.state.reopen()
    expect(view.state.requiresReopen.value).toBe(false)
    expect(view.state.client.value).not.toBe(old)
    expect(view.state.decorated.value.taskExecution?.executionId).toBe('91')
    view.wrapper.unmount()
  })
  it('does not infer file write permission from business operations', async () => {
    vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability() as never)
    const view = harness(); await flushPromises()
    expect(view.state.allowedActions.value).not.toContain('FILE_WRITE')
    view.wrapper.unmount()
  })
  it('keeps a current client after a transient query failure but closes new UI actions', async () => {
    vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability() as never)
    const view = harness(); await flushPromises()
    const client = view.state.client.value
    vi.mocked(inspectOperationCapabilities).mockRejectedValue(new Error('network'))
    await view.state.refresh()
    expect(view.state.client.value).toBe(client)
    expect(view.state.mode.value).toBe('CONTROLLED')
    expect(view.state.allowedActions.value).toEqual(['QUERY'])
    view.wrapper.unmount()
  })

  it('does not rotate a client when only an observation version changes', async () => {
    vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability() as never)
    const view = harness(); await flushPromises()
    const old = view.state.client.value
    const next = capability(); next.execution.task.executionVersion = 2
    vi.mocked(inspectOperationCapabilities).mockResolvedValue(next as never)
    await view.state.refresh()
    expect(view.state.client.value).toBe(old)
    expect(view.state.requiresReopen.value).toBe(false)
    expect(view.state.decorated.value.taskExecution?.executionVersion).toBe(2)
    view.wrapper.unmount()
  })
  it('preserves the last routed identity when execution temporarily disappears', async () => {
    vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability() as never)
    const view = harness(); await flushPromises()
    const old = view.state.client.value
    vi.mocked(inspectOperationCapabilities).mockResolvedValue({ ...capability(), execution: null } as never)
    await view.state.refresh()
    expect(view.state.client.value).toBe(old)
    expect(view.state.requiresReopen.value).toBe(true)
    expect(view.state.decorated.value.taskExecution?.executionId).toBe('90')
    view.wrapper.unmount()
  })
  it('recovers an uncertain command after rework without adopting the new round', async () => {
    vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability() as never)
    vi.mocked(request.post).mockRejectedValueOnce(new Error('connection lost'))
    const view = harness(); await flushPromises()
    const old = view.state.client.value!
    await expect(old.execute({ operationCode: 'SOL.SITE_SURVEY.CREATE', input: { name: 'draft' } })).rejects.toThrow('connection lost')
    const sent = vi.mocked(request.post).mock.calls[0][0]
    expect(view.state.uncertain.value).toBe(true)
    vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability(null, '91') as never)
    await view.state.refresh()
    await view.state.reopen()
    expect(view.state.client.value).toBe(old)
    expect(view.state.requiresReopen.value).toBe(true)
    const result = { ownerContext: 'SOL', objectType: 'SITE_SURVEY', objectId: '88', objectVersion: 1,
      businessFactVersion: 'v1', resultCode: 'SURVEY_DRAFT_SAVED', response: { id: '88' }, replayed: true }
    vi.mocked(request.post).mockResolvedValueOnce(result as never)
    await view.state.recover()
    const replay = vi.mocked(request.post).mock.calls[1][0]
    expect(replay.data).toEqual(sent.data)
    expect(replay.headers).toEqual(sent.headers)
    expect(replay.data.execution.task.executionId).toBe('90')
    expect(view.state.uncertain.value).toBe(false)
    expect(view.state.receipt.value).toEqual(result)
    expect(view.state.requiresReopen.value).toBe(true)
    await view.state.reopen()
    expect(view.state.client.value).not.toBe(old)
    expect(view.state.decorated.value.taskExecution?.executionId).toBe('91')
    view.wrapper.unmount()
  })
})
