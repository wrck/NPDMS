import { defineComponent, h, nextTick, reactive } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import Host from './BusinessViewHost.vue'
import { inspectOperationCapabilities } from '@/api/pms/project/execution-operations'
import { mount, passthrough, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const editor = vi.hoisted(() => ({ receiptId: '42', mounted: 0, leave: vi.fn() }))
vi.mock('./registry', async () => {
  const { defineComponent, h, ref } = await import('vue')
  const Page = defineComponent({ props: { readonly: Boolean }, setup(props, { expose }) {
    editor.mounted++
    const value = ref('')
    expose({ requestLeave: editor.leave, canAdoptAssociatedObject: (id: unknown) => String(id) === editor.receiptId })
    return () => h('input', { value: value.value, disabled: props.readonly, onInput: (next: string) => { value.value = next } })
  } })
  return { resolveBusinessView: (target: any) => ({ component: Page, props: { readonly: target.readonly } }) }
})
vi.mock('@/api/pms/project/execution-operations', () => ({ inspectOperationCapabilities: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: {} }))
vi.mock('@/config/axios/service', () => ({ service: { defaults: {} } }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ warning: vi.fn() }) }))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn() }))
const all = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(all)]
const flush = async () => { for (let i = 0; i < 16; i++) { await Promise.resolve(); await nextTick() } }
const target = () => ({ registration: { id: 7, componentKey: 'DIRECT_BUSINESS_ACC_ACCEPTANCE_ACTIVITY', componentVersion: '1',
  ownerContext: 'ACC', entityType: 'acceptanceActivity', viewSource: 'PAGE', status: 'PUBLISHED' },
  resolvedContext: { project: { id: 9 }, taskId: 10, businessObjectId: undefined as string | undefined,
    bindingParameters: { acceptanceType: 'PRELIMINARY' } }, allowedActions: ['QUERY', 'create', 'report-publish'] })
beforeEach(() => {
  vi.clearAllMocks(); editor.mounted = 0; editor.receiptId = '42'; editor.leave.mockResolvedValue(false)
  vi.mocked(inspectOperationCapabilities).mockImplementation(async query => ({
    node: { projectId: query.projectId, id: query.nodeId, kind: query.nodeKind, status: 'IN_PROGRESS' },
    reason: 'LEGACY_BINDING', presentation: { status: 'AVAILABLE' }, actions: []
  }) as any)
})

it('retains the upload buffer when its saved record is first associated and still refreshes capabilities', async () => {
  editor.leave.mockResolvedValue(true)
  const state = reactive(target()), blocked = vi.fn()
  const view = mount(defineComponent({ setup: () => () => h(Host, { ...state, onSwitchBlocked: blocked } as any) }), {}, { ElTable: passthrough, ElTableColumn: passthrough })
  try {
    await flush()
    const input = all(view.root).find(node => node.type === 'input')!
    ;(input.props!.onInput as Function)('selected-file.txt'); await flush()
    state.resolvedContext.businessObjectId = '42'; await flush()
    expect(editor.mounted).toBe(1)
    expect(all(view.root).find(node => node.type === 'input')?.props?.value).toBe('selected-file.txt')
    expect(editor.leave).not.toHaveBeenCalled(); expect(blocked).not.toHaveBeenCalled()
    expect(inspectOperationCapabilities).toHaveBeenLastCalledWith(expect.objectContaining({ objectId: '42' }))

    editor.leave.mockResolvedValue(false)
    state.resolvedContext.businessObjectId = '43'; await flush()
    expect(blocked).toHaveBeenCalledOnce(); expect(editor.mounted).toBe(1)
    editor.leave.mockResolvedValue(true)
    state.resolvedContext = { ...state.resolvedContext }; await flush()
    expect(editor.mounted).toBe(2)
  } finally { view.app.unmount() }
})

it.each(['different-record', 'different-task', 'different-configuration', 'legacy-page'])('keeps the original leave guard for %s', async change => {
  const state = reactive(target()), blocked = vi.fn()
  if (change === 'legacy-page') state.registration.componentKey = 'ACC_ACCEPTANCE_REPORT'
  const view = mount(defineComponent({ setup: () => () => h(Host, { ...state, onSwitchBlocked: blocked } as any) }), {}, { ElTable: passthrough, ElTableColumn: passthrough })
  try {
    await flush()
    state.resolvedContext.businessObjectId = change === 'different-record' ? '43' : '42'
    if (change === 'different-task') state.resolvedContext.taskId = 11
    if (change === 'different-configuration') state.resolvedContext.bindingParameters = { acceptanceType: 'FINAL' }
    await flush()
    expect(editor.leave).toHaveBeenCalledOnce(); expect(blocked).toHaveBeenCalledOnce()
    expect(editor.mounted).toBe(1)
  } finally { view.app.unmount() }
})
