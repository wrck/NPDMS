import { defineComponent, h, nextTick, onErrorCaptured, reactive, ref } from 'vue'
import { expect, it, vi } from 'vitest'
import Host from './NativeBusinessHost.vue'
import { NativeBusinessOperationFailure } from './nativeBusinessBridge'
import { mount, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const messages = vi.hoisted(() => ({ error: vi.fn() }))
const saved = vi.hoisted(() => ({ outcome: 'SAVED', entityRef: { entityId: '42' } }))
vi.mock('element-plus', () => ({ ElMessage: messages }))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn(), onBeforeRouteUpdate: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: {} }))
vi.mock('@/config/axios/service', () => ({ service: {} }))
vi.mock('@/utils', () => ({ generateUUID: () => 'test-key' }))
vi.mock('../ProjectBusinessFieldConfiguration.vue', () => ({ default: defineComponent({ setup: () => () => null }) }))
vi.mock('@/utils/auth', () => ({ getCurrentUserId: () => 42, getTenantId: () => 1, getVisitTenantId: () => undefined }))
vi.mock('@/api/pms/platform/business', () => ({ createProjectBusinessApi: () => ({ base: '/api/v1/pms/arrival-business' }) }))
vi.mock('./nativeBusinessBridge', async importOriginal => {
  const original = await importOriginal<typeof import('./nativeBusinessBridge')>()
  return { ...original, createNativeBusinessBridge: () => ({ api: { base: '/api/v1/pms/arrival-business' }, state: {
    error: ref(''), pending: ref(undefined), executing: ref(false), receipt: ref(saved) } }) }
})

it('only adopts the first association to its own successful command receipt', () => {
  const child = ref<any>()
  const view = mount(defineComponent({ setup: () => () => h(Host,
    { ref: child, apiBase: '/api/v1/pms/arrival-business', stableCode: 'IMP_ARRIVAL', scopeProjectId: 9 }, { default: () => h('span') }) }))
  try {
    expect(child.value.canAdoptAssociatedObject('42')).toBe(true)
    expect(child.value.canAdoptAssociatedObject('43')).toBe(false)
    saved.outcome = 'FAILED'
    expect(child.value.canAdoptAssociatedObject('42')).toBe(false)
  } finally { saved.outcome = 'SAVED'; view.app.unmount() }
})

it('preserves explicit native file permission without treating generic delivery as its source', async () => {
  const child = ref<any>()
  const props = reactive({ initialEntityId: undefined as string | undefined, allowedActions: ['QUERY', 'report-save', 'FILE_WRITE'] })
  const view = mount(defineComponent({ setup: () => () => h(Host,
    { ...props, ref: child, apiBase: '/api/v1/pms/acceptance-business', stableCode: 'ACC_ACCEPTANCE_ACTIVITY', scopeProjectId: 9 },
    { default: () => h('span') }) }))
  try {
    const state = child.value.$.setupState
    expect(state.pageProps.allowedActions).toContain('FILE_WRITE')
    props.initialEntityId = '42'; await flush()
    expect(state.pageProps.objectId).toBeUndefined()
    props.allowedActions = ['QUERY', 'report-save']; await flush()
    expect(state.pageProps.allowedActions).not.toContain('FILE_WRITE')
  } finally { view.app.unmount() }
})
const all = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(all)]
const flush = async () => { for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() } }
it.each([true, false])('captures only expected native operation failures and preserves the editing buffer (%s)', async expected => {
  messages.error.mockClear()
  const outer = vi.fn(), failed = ref(false)
  const failure = expected ? new NativeBusinessOperationFailure('FIELD_VALUE_INVALID', false) : new Error('unexpected render error')
  const Editor = defineComponent({ emits: ['save'], setup(_, { emit }) { const name = ref('原始输入'); return () => h('section', [
    h('input', { value: name.value, onChange: (value: string) => { name.value = value } }),
    h('button', { 'data-save': true, onClick: () => emit('save') }, '保存')
  ]) } })
  const Wrapper = defineComponent({ setup() {
    onErrorCaptured(error => { outer(error); failed.value = true; return false })
    return () => failed.value ? h('p', '业务组件不可用') : h(Host, { apiBase: '/api/v1/pms/arrival-business', stableCode: 'IMP_ARRIVAL', scopeProjectId: 9 }, { default: () => h(Editor, { onSave: async () => { throw failure } }) })
  } })
  const view = mount(Wrapper)
  try {
    const input = all(view.root).find(node => node.type === 'input')!
    ;(input.props!.onChange as Function)('尚未保存的姓名'); await flush()
    expect(outer.mock.calls.map(call => String(call[0]))).toEqual([])
    const button = all(view.root).find(node => node.props?.['data-save'])!
    // The click emits a Vue component event; Vue captures its async listener rejection.
    const result = (button.props!.onClick as Function)()
    await Promise.resolve(result).catch(() => {}); await flush()
    if (expected) {
      expect(outer).not.toHaveBeenCalled(); expect(messages.error).toHaveBeenCalledWith('FIELD_VALUE_INVALID')
      expect(all(view.root).find(node => node.type === 'input')?.props?.value).toBe('尚未保存的姓名')
    } else expect(outer).toHaveBeenCalledWith(failure)
  } finally { view.app.unmount() }
})

