import { computed, nextTick, ref, shallowRef } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import Host from './BusinessEntityHost.vue'
import { mount, passthrough, button, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const mocks = vi.hoisted(() => ({ state: undefined as any, confirm: vi.fn() }))
vi.mock('./useBusinessEntity', () => ({ useBusinessEntity: () => mocks.state, isConcurrencyConflict: () => false,
  serverErrorMessage: (error: any, fallback: string) => error?.message || fallback }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ confirm: mocks.confirm }) }))
vi.mock('@/components/BusinessView/registry', () => ({ resolveStandaloneBusinessEntityView: () => undefined }))
vi.mock('@/api/pms/project/projects', () => ({ __v_isRef: false }))
vi.mock('@/components/PmsEntitySelect/index.vue', () => ({ default: { render: () => null } }))
vi.mock('./BusinessEntityForm.vue', async () => {
  const { defineComponent, h } = await import('vue')
  return { default: defineComponent({ setup: (_, { slots }) => () => h('div', slots.extra?.()) }) }
})
vi.mock('./BusinessEntityList.vue', () => ({ default: { render: () => null } }))
vi.mock('./DefaultBusinessDelivery.vue', () => ({ default: { render: () => null } }))
vi.mock('./DeliveryPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('./ApprovalPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('./ContentHistoryPanel.vue', () => ({ default: { render: () => null } }))
const flush = async () => { for (let i=0;i<12;i++) { await Promise.resolve(); await nextTick() } }
const findDelete = (node: TestNode): TestNode | undefined => node.type === 'button' && textOf(node) === '删除'
  ? node : node.children.map(findDelete).find(Boolean)
const record = () => ({ ref: { tenantId: 7, ownerModule: 'IT', entityType: 'note', entityId: 11 }, available: true, concurrencyBasis: 3, fieldValues: {} })
const operation = { code: 'delete', name: '删除', kind: 'DELETE', version: 1, executable: true }
const deleted = { outcome: 'DELETED', entityRef: record().ref, newConcurrencyBasis: 4 }
beforeEach(() => {
  vi.clearAllMocks(); mocks.confirm.mockResolvedValue(undefined)
  const current = shallowRef<any>(), detail = shallowRef<any>({ title: '记录', fields: [], capabilities: [], operations: [operation] })
  mocks.state = { detail, current, loadError: ref(''), loadDetail: vi.fn(async () => undefined),
    writableFields: computed(() => []), readableFields: computed(() => []), createOperation: ref(), updateOperation: ref({ executable: true, kind: 'UPDATE' }),
    rows: ref([]), listLoading: ref(false), listError: ref(''), sliceComplete: ref(true), loadPage: vi.fn(async () => undefined),
    formPresentation: ref(), readEntity: vi.fn(async () => { current.value = record(); return current.value }),
    executing: ref(false), pendingIntent: ref(), recover: vi.fn(), execute: vi.fn(async () => deleted) }
})
const open = () => mount(Host, { ownerModule: 'IT', entityType: 'note', initialEntityId: 11 },
  { ContentWrap: passthrough, ElButton: button, ElFormItem: passthrough, ElAlert: passthrough })
it('deletes through the inherited operation and returns to list without reading the removed object', async () => {
  const mounted=open(); await flush(); mocks.state.readEntity.mockClear()
  const remove=findDelete(mounted.root); expect(remove).toBeDefined()
  await (remove!.props!.onClick as Function)(); await flush()
  expect(mocks.confirm).toHaveBeenCalledTimes(1)
  expect(mocks.state.execute).toHaveBeenCalledWith(operation, 11, {}, 3)
  expect(mocks.state.readEntity).not.toHaveBeenCalled()
  expect(mocks.state.current.value).toBeUndefined()
  expect(findDelete(mounted.root)).toBeUndefined(); mounted.app.unmount()
})
it('cancellation and repeated clicks do not issue a deletion', async () => {
  let cancel!: (reason: unknown) => void
  mocks.confirm.mockImplementation(() => new Promise((_, reject) => { cancel=reject }))
  const mounted=open(); await flush(); const remove=findDelete(mounted.root); expect(remove).toBeDefined()
  const first=(remove!.props!.onClick as Function)(); const second=(remove!.props!.onClick as Function)()
  await flush(); expect(mocks.confirm).toHaveBeenCalledTimes(1)
  cancel('cancel'); await Promise.all([first, second]); await flush()
  expect(mocks.state.execute).not.toHaveBeenCalled(); expect(mocks.state.current.value.ref.entityId).toBe(11)
  mounted.app.unmount()
})
it('recovering a committed deletion does not reopen the initial deleted entity', async () => {
  mocks.state.pendingIntent.value = { key: 'pending' }; mocks.state.recover.mockResolvedValue(deleted)
  const mounted=open(); await flush()
  expect(mocks.state.recover).toHaveBeenCalledTimes(1)
  expect(mocks.state.readEntity).not.toHaveBeenCalled()
  expect(mocks.state.current.value).toBeUndefined(); mounted.app.unmount()
})
it('does not delete a different entity selected while confirmation was open', async () => {
  let confirm!: () => void
  mocks.confirm.mockImplementation(() => new Promise<void>(resolve => { confirm=resolve }))
  const mounted=open(); await flush(); const remove=findDelete(mounted.root); expect(remove).toBeDefined()
  const pending=(remove!.props!.onClick as Function)(); await flush()
  mocks.state.current.value = { ...record(), ref: { ...record().ref, entityId: 12 } }
  confirm(); await pending; await flush()
  expect(mocks.state.execute).not.toHaveBeenCalled(); expect(mocks.state.current.value.ref.entityId).toBe(12)
  mounted.app.unmount()
})
it('read-only entry refuses deletion even if its click handler is invoked', async () => {
  const mounted=mount(Host, { ownerModule:'IT',entityType:'note',initialEntityId:11,readonly:true },
    { ContentWrap:passthrough,ElButton:button,ElFormItem:passthrough,ElAlert:passthrough })
  await flush(); const remove=findDelete(mounted.root); expect(remove).toBeDefined()
  expect(remove!.props!.disabled).toBe(true)
  await (remove!.props!.onClick as Function)(); await flush()
  expect(mocks.confirm).not.toHaveBeenCalled(); expect(mocks.state.execute).not.toHaveBeenCalled()
  mounted.app.unmount()
})
