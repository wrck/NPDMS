import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, shallowRef } from 'vue'
import { mount } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import { inspectOperationCapabilities } from '@/api/pms/project/execution-operations'
import { useOperationHost } from './operationHost'
import { businessPageRoutes, pagePresentationKey, validateBusinessPage } from './presentationRoute'
import type { BusinessViewTarget } from './registry'

vi.mock('@/api/pms/project/execution-operations', () => ({ inspectOperationCapabilities: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: { post: vi.fn() } }))
vi.mock('@/config/axios/service', () => ({ service: { defaults: { transformResponse: [] } } }))
const route = businessPageRoutes.SOL_SITE_SURVEY
const execution = { task: { projectId: '1', taskId: '2', executionContractId: '3', contractVersion: 1,
  planVersionId: '4', executionId: '5', executionVersion: 1 }, stage: null }
const registration = { ...route, id: '7', status: 'PUBLISHED' }
const target = () => ({ registration, resolvedContext: { project: { id: '1' }, taskId: '2', taskExecution: execution.task },
  allowedActions: ['QUERY', 'CREATE'] }) as unknown as BusinessViewTarget
const capability = (legacy = false) => ({
  node: { projectId: '1', kind: 'TASK', id: '2', code: 'T', name: '工勘', status: 'IN_PROGRESS' }, execution,
  reason: legacy ? 'LEGACY_BINDING' : null,
  actions: [{ operationCode: 'SOL.SITE_SURVEY.CREATE', operationVersion: 1, label: '创建',
    ownerPermitted: true, executionPermitted: true, runtimeAvailable: true, allowed: true,
    pre: { outcome: 'NO_ADDITIONAL_RULE' }, post: { outcome: 'NOT_EVALUATED' } }],
  presentation: { registration, status: 'AVAILABLE', pageUrl: route.pageUrl, query: { projectId: '1' } }
})
const tick = async () => { for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() } }
function harness() {
  const active = shallowRef(target())
  let state!: ReturnType<typeof useOperationHost>
  const wrapper = mount(defineComponent({ setup() { state = useOperationHost(active, vi.fn()); return () => h('div') } }))
  return { active, wrapper, get state() { return state } }
}
beforeEach(() => vi.clearAllMocks())

describe('registered embedded pages', () => {
  for (const route of Object.values(businessPageRoutes)) {
    it(`matches the exact ${route.componentKey} identity without converting string IDs`, () => {
      const context = { registration: route, resolvedContext: { project: { id: '9007199254740993' }, businessObjectId: '9007199254740994' } }
      expect(() => validateBusinessPage(context, { pageUrl: route.pageUrl, query: { projectId: '9007199254740993', objectId: '9007199254740994' } })).not.toThrow()
      for (const patch of [{ ownerContext: 'OTHER' }, { entityType: 'OTHER' }, { componentVersion: '2' }, { viewSource: 'DYNAMIC_FORM' }])
        expect(() => validateBusinessPage({ ...context, registration: { ...route, ...patch } }, { pageUrl: route.pageUrl, query: {} })).toThrow()
    })
  }
  it.each(['https://remote', '/pms/unknown', '/api/v1/command', '/pms/delivery-business/site-survey?projectId=2'])('rejects nonregistered URL %s', pageUrl => {
    expect(() => validateBusinessPage(target(), { pageUrl, query: {} })).toThrow()
  })
  it.each([{ projectId: '2' }, { actorId: '1' }, { projectId: 1 }, { objectId: '3' }, { projectId: '$project.id' }])('rejects parameter context replacement %j', query => {
    expect(() => validateBusinessPage(target(), { pageUrl: route.pageUrl, query: query as never })).toThrow()
  })
  it('query order is not a new editing identity', () => {
    expect(pagePresentationKey({ pageUrl: route.pageUrl, query: { projectId: '1', objectId: '3' } }))
      .toBe(pagePresentationKey({ pageUrl: route.pageUrl, query: { objectId: '3', projectId: '1' } }))
  })
  it('keeps the routed component and command client on unavailable inspection and recovers without rotation', async () => {
    vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability() as never)
    const view = harness(); await tick()
    try {
      const client = view.state.client.value, page = view.state.routingPresentation.value
      vi.mocked(inspectOperationCapabilities).mockResolvedValue({ ...capability(), presentation: { pageUrl: route.pageUrl, status: 'UNAVAILABLE', query: null } } as never)
      await view.state.refresh()
      expect(view.state.routingPresentation.value).toBe(page)
      expect(view.state.client.value).toBe(client)
      expect(view.state.presentationReadonly.value).toBe(true)
      expect(view.state.allowedActions.value).toEqual(['QUERY'])
      vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability() as never)
      await view.state.refresh()
      expect(view.state.client.value).toBe(client)
      expect(view.state.requiresReopen.value).toBe(false)
      expect(view.state.allowedActions.value).toContain('CREATE')
    } finally { view.wrapper.app.unmount() }
  })
  it('changed published presentation requires explicit reopen before any routing replacement', async () => {
    vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability() as never)
    const view = harness(); await tick()
    try {
      const client = view.state.client.value, page = view.state.routingPresentation.value
      vi.mocked(inspectOperationCapabilities).mockResolvedValue({ ...capability(), presentation: { ...capability().presentation, query: {} } } as never)
      await view.state.refresh()
      expect(view.state.requiresReopen.value).toBe(true)
      expect(view.state.routingPresentation.value).toBe(page)
      expect(view.state.client.value).toBe(client)
      expect(view.state.allowedActions.value).toEqual(['QUERY'])
      await view.state.reopen()
      expect(view.state.requiresReopen.value).toBe(false)
      expect(view.state.routingPresentation.value?.query).toEqual({})
      expect(view.state.client.value).not.toBe(client)
    } finally { view.wrapper.app.unmount() }
  })
  it('removing a page never silently downgrades the retained view to its default component', async () => {
    vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability() as never)
    const view = harness(); await tick()
    try {
      const page = view.state.routingPresentation.value
      vi.mocked(inspectOperationCapabilities).mockResolvedValue({ ...capability(), presentation: { status: 'AVAILABLE' } } as never)
      await view.state.refresh()
      expect(view.state.requiresReopen.value).toBe(true)
      expect(view.state.routingPresentation.value).toBe(page)
    } finally { view.wrapper.app.unmount() }
  })
  it('page-only legacy command paths still honor fresh presentation readonly restrictions', async () => {
    vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability(true) as never)
    const view = harness(); await tick()
    try {
      expect(view.state.mode.value).toBe('LEGACY')
      expect(view.state.client.value).toBeUndefined()
      expect(view.state.allowedActions.value).toContain('CREATE')
      vi.mocked(inspectOperationCapabilities).mockResolvedValue({ ...capability(true), presentation: { ...capability().presentation, status: 'READ_ONLY' } } as never)
      await view.state.refresh()
      expect(view.state.presentationReadonly.value).toBe(true)
      expect(view.state.allowedActions.value).toEqual(['QUERY'])
      expect(view.state.requiresReopen.value).toBe(false)
    } finally { view.wrapper.app.unmount() }
  })
  it('an invalid new page cannot reopen ordinary writes on a retained legacy form', async () => {
    vi.mocked(inspectOperationCapabilities).mockResolvedValue({ ...capability(true), presentation: { status: 'AVAILABLE' } } as never)
    const view = harness(); await tick()
    try {
      vi.mocked(inspectOperationCapabilities).mockResolvedValue({ ...capability(true), presentation: { ...capability().presentation, query: { projectId: '2' } } } as never)
      await view.state.refresh()
      expect(view.state.routingPresentation.value).toBeUndefined()
      expect(view.state.presentationReadonly.value).toBe(true)
      expect(view.state.allowedActions.value).toEqual(['QUERY'])
    } finally { view.wrapper.app.unmount() }
  })
})
