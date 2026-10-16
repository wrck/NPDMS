import { defineComponent, h, nextTick, ref } from 'vue'
import { afterEach, expect, it, vi } from 'vitest'
import Support from './NativeBusinessFormSupport.vue'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const context = vi.hoisted(() => ({ current: undefined as any }))
const calls = vi.hoisted(() => ({ deliveries: vi.fn(), approvals: vi.fn() }))
vi.mock('./nativeBusinessContext', () => ({ useNativeBusiness: () => context.current }))
vi.mock('element-plus', () => ({ ElMessage: { warning: vi.fn(), info: vi.fn(), error: vi.fn() }, ElMessageBox: { confirm: vi.fn() } }))
vi.mock('../ProjectBusinessForm.vue', () => ({ default: defineComponent({ setup: () => () => h('div', '业务字段') }) }))
vi.mock('../ProjectBusinessCompletion.vue', () => ({ default: defineComponent({ setup: () => () => h('div', '完成条件') }) }))
// API probes isolate the parent's mount boundary: a mounted child performs its real initial read.
vi.mock('../ProjectBusinessDeliveries.vue', () => ({ default: defineComponent({ props: ['readonly'], setup: props => { calls.deliveries(); return () => h('div', `附件历史:${props.readonly}`) } }) }))
vi.mock('../ProjectBusinessApprovals.vue', () => ({ default: defineComponent({ props: ['readonly'], setup: props => { calls.approvals(); return () => h('div', `审批历史:${props.readonly}`) } }) }))
const apps: { unmount: () => void }[] = []
afterEach(() => apps.splice(0).forEach(app => app.unmount()))
const flush = async () => { for (let i = 0; i < 12; i++) { await Promise.resolve(); await nextTick() } }
it.each([
  { features: undefined, attachments: false, approvals: false },
  { features: { attachments: true, approvals: false }, attachments: true, approvals: false },
  { features: { attachments: false, approvals: true }, attachments: false, approvals: true }
])('loads only explicitly enabled native support features (%j)', async setting => {
  calls.deliveries.mockClear(); calls.approvals.mockClear()
  const bridge = { state: { model: ref({ fields: [], features: setting.features, operations: [] }) }, ready: vi.fn(), reloadModel: vi.fn(),
    readPresentation: vi.fn(async () => ({ row: { ref: { entityId: '11' }, concurrencyBasis: 2 }, form: {} })) }
  context.current = { getBridge: () => bridge, can: () => false, projectId: () => 9, registerForm: () => () => {} }
  const view = mount(Support, { record: { id: '11', version: 2 }, readonly: true, nativeForm: true })
  apps.push(view.app); await flush()
  expect(calls.deliveries).toHaveBeenCalledTimes(setting.attachments ? 1 : 0)
  expect(calls.approvals).toHaveBeenCalledTimes(setting.approvals ? 1 : 0)
  expect(bridge.readPresentation).toHaveBeenCalledTimes(setting.attachments || setting.approvals ? 1 : 0)
  if (setting.attachments) expect(textOf(view.root)).toContain('附件历史:true')
  if (setting.approvals) expect(textOf(view.root)).toContain('审批历史:true')
})

it('loads newly enabled support after a native form initially needs no generic detail', async () => {
  calls.deliveries.mockClear()
  const model = ref({ fields: [], operations: [], features: { attachments: false, approvals: false } })
  const bridge = { state: { model }, ready: vi.fn(),
    readPresentation: vi.fn(async () => ({ row: { ref: { entityId: '11' }, concurrencyBasis: 2 }, form: {} })) }
  context.current = { getBridge: () => bridge, can: () => false, projectId: () => 9, registerForm: () => () => {} }
  const view = mount(Support, { record: { id: '11', version: 2 }, readonly: true, nativeForm: true })
  apps.push(view.app); await flush()
  expect(bridge.readPresentation).not.toHaveBeenCalled()
  model.value.features.attachments = true; await flush()
  expect(bridge.readPresentation).toHaveBeenCalledOnce()
  expect(textOf(view.root)).toContain('附件历史:true')
  model.value.features.attachments = false; await flush()
  expect(textOf(view.root)).not.toContain('附件历史')
})

it('waits for the saved record identity without repeatedly loading a new native form', async () => {
  calls.deliveries.mockClear()
  const bridge = { state: { model: ref({ fields: [], operations: [], features: { attachments: true } }) }, ready: vi.fn(),
    readPresentation: vi.fn(async () => ({ row: { ref: { entityId: '11' }, concurrencyBasis: 2 }, form: {} })) }
  context.current = { getBridge: () => bridge, can: () => false, projectId: () => 9, registerForm: () => () => {} }
  const record = ref<{ id?: string; version?: number }>({})
  const view = mount(defineComponent({ setup: () => () => h(Support, { record: record.value, nativeForm: true }) }))
  apps.push(view.app); await flush()
  expect(bridge.ready).toHaveBeenCalledOnce()
  expect(bridge.readPresentation).not.toHaveBeenCalled()
  expect(calls.deliveries).not.toHaveBeenCalled()
  record.value = { id: '11', version: 2 }; await flush()
  expect(bridge.readPresentation).toHaveBeenCalledOnce()
  expect(textOf(view.root)).toContain('附件历史:true')
})

it('still reads the generic form when its attachment and approval features are disabled', async () => {
  const bridge = { state: { model: ref({ fields: [], operations: [], features: { attachments: false, approvals: false } }) }, ready: vi.fn(),
    readPresentation: vi.fn(async () => ({ row: { ref: { entityId: '11' }, concurrencyBasis: 2 }, form: {} })) }
  context.current = { getBridge: () => bridge, can: () => false, projectId: () => 9, registerForm: () => () => {} }
  const view = mount(Support, { record: { id: '11', version: 2 }, readonly: true })
  apps.push(view.app); await flush()
  expect(bridge.readPresentation).toHaveBeenCalledOnce()
  expect(textOf(view.root)).toContain('业务字段')
})

it('does not bypass a failed model permission check when no support feature is enabled', async () => {
  const bridge = { state: { model: ref(undefined) }, ready: vi.fn().mockRejectedValue(new Error('业务读取权限不足')),
    readPresentation: vi.fn() }
  context.current = { getBridge: () => bridge, can: () => false, projectId: () => 9, registerForm: () => () => {} }
  const view = mount(Support, { record: { id: '11', version: 2 }, readonly: true, nativeForm: true })
  apps.push(view.app); await flush()
  expect(bridge.ready).toHaveBeenCalledOnce()
  expect(bridge.readPresentation).not.toHaveBeenCalled()
  expect(textOf(view.root)).toContain('业务读取权限不足')
})
