import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import BusinessViewHost from './BusinessViewHost.vue'
import { resolveBusinessView } from './registry'
import { businessPageRoutes } from './presentationRoute'
import { inspectOperationCapabilities } from '@/api/pms/project/execution-operations'
import { mount, textOf, findByTestId, passthrough, tableColumn, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const owner = vi.hoisted(() => ({ mounts: 0, allowLeave: false }))
vi.mock('@/views/pms/delivery-business/site-survey/index.vue', async () => {
  const { defineComponent, h, ref } = await import('vue')
  return { default: defineComponent({
    props: ['projectId', 'objectId', 'readonly'], emits: ['dirty-change'],
    setup(props, { emit, expose }) {
      owner.mounts++
      const value = ref('saved')
      expose({ requestLeave: async () => owner.allowLeave, discardChanges: () => { value.value = 'saved'; return true }, isDirty: () => value.value !== 'saved' })
      return () => h('div', [h('span', `owner:${props.projectId}:${value.value}:readonly=${props.readonly}`),
        h('button', { 'data-testid': 'edit-page', onClick: () => { value.value = 'unsaved'; emit('dirty-change', true) } }, 'edit')])
    }
  }) }
})
vi.mock('@/views/pms/delivery-business/requirement-analysis/entity/EntityPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('@/views/pms/acceptance/acceptance-report/index.vue', () => ({ default: { render: () => null } }))
vi.mock('@/views/pms/platform/dynamic-form/instance/DynamicFormInstanceContent.vue', () => ({ default: { render: () => null } }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ warning: vi.fn() }) }))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn() }))
vi.mock('@/api/pms/project/execution-operations', () => ({ inspectOperationCapabilities: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: { post: vi.fn() } }))
vi.mock('@/config/axios/service', () => ({ service: { defaults: { transformResponse: [] } } }))
const route = businessPageRoutes.SOL_SITE_SURVEY
const target = () => ({ registration: { ...route, id: '7', status: 'PUBLISHED' },
  resolvedContext: { project: { id: '1' }, taskId: '2' }, allowedActions: ['QUERY', 'CREATE'] })
const capability = () => ({ node: { projectId: '1', kind: 'TASK', id: '2', code: 'T', name: '工勘', status: 'ACTIVE' },
  execution: null, actions: [], reason: 'LEGACY_BINDING',
  presentation: { registration: target().registration, status: 'AVAILABLE', pageUrl: route.pageUrl, query: { projectId: '1' } } })
const options = { ElTable: passthrough, ElTableColumn: tableColumn }
const tick = async () => { for (let i = 0; i < 10; i++) { await Promise.resolve(); await nextTick() } }
const button = (root: TestNode, label: string): TestNode | undefined => root.type === 'button' && textOf(root).includes(label)
  ? root : root.children.map(child => button(child, label)).find(Boolean)
beforeEach(() => { vi.clearAllMocks(); owner.mounts = 0; owner.allowLeave = false })

describe('BusinessViewHost consumes frozen page routing', () => {
  it('keeps the real host and unsaved Owner buffer through refresh, disable and explicit reopen', async () => {
    vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability() as never)
    const view = mount(BusinessViewHost, target(), options)
    try {
      await tick()
      expect(owner.mounts).toBe(1)
      expect(view.root.children[0].props?.['data-page-url']).toBe(`${route.pageUrl}?projectId=1`)
      await (findByTestId(view.root, 'edit-page')!.props!.onClick as () => void)(); await tick()
      const refresh = async () => { await (button(view.root, '刷新执行状态')!.props!.onClick as () => Promise<void>)(); await tick() }
      await refresh()
      expect(owner.mounts).toBe(1); expect(textOf(view.root)).toContain('unsaved')
      vi.mocked(inspectOperationCapabilities).mockResolvedValue({ ...capability(), presentation: { ...capability().presentation, status: 'READ_ONLY' } } as never)
      await refresh()
      expect(textOf(view.root)).toContain('unsaved:readonly=true'); expect(owner.mounts).toBe(1)
      vi.mocked(inspectOperationCapabilities).mockResolvedValue({ ...capability(), presentation: { ...capability().presentation, query: {} } } as never)
      await refresh()
      const reopen = async () => { await (button(view.root, '处理未保存内容并重新进入')!.props!.onClick as () => Promise<void>)(); await tick() }
      await reopen()
      expect(textOf(view.root)).toContain('unsaved'); expect(owner.mounts).toBe(1)
      owner.allowLeave = true
      await reopen()
      expect(owner.mounts).toBe(2); expect(textOf(view.root)).toContain('saved:readonly=false')
      expect(view.root.children[0].props?.['data-page-url']).toBe(route.pageUrl)
    } finally { view.app.unmount() }
  })
  it('never mounts the default Owner page when first inspection supplies an unregistered route', async () => {
    vi.mocked(inspectOperationCapabilities).mockResolvedValue({ ...capability(), presentation: { ...capability().presentation, pageUrl: '/pms/unknown' } } as never)
    const view = mount(BusinessViewHost, target(), options)
    try { await tick(); expect(owner.mounts).toBe(0) } finally { view.app.unmount() }
  })
  it('the actual component registry rejects mismatched routes and never spreads URL parameters into props', () => {
    const source = target()
    const resolved = resolveBusinessView({ ...source, presentation: { pageUrl: route.pageUrl, query: { projectId: '1' } } } as never)
    expect(resolved.props).toMatchObject({ projectId: '1', taskId: '2' })
    expect(resolved.props).not.toHaveProperty('pageUrl')
    expect(resolveBusinessView({ ...source, presentation: { pageUrl: businessPageRoutes.ACC_ACCEPTANCE_REPORT.pageUrl, query: {} } } as never).error).toBeTruthy()
    expect(resolveBusinessView({ ...source, presentation: { pageUrl: route.pageUrl, query: { projectId: '2' } } } as never).error).toBeTruthy()
  })
})
