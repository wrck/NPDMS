vi.mock('@/components/BusinessEntity/DeliveryPanel.vue', () => ({ default: { render: () => null } }))
import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive } from 'vue'
import Attachments from './NativeAttachments.vue'
import { testEventHandler, mount, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const mocks = vi.hoisted(() => ({ get: vi.fn(), update: vi.fn(), list: vi.fn(), withdraw: vi.fn() }))
vi.mock('@/utils', () => ({ generateUUID: () => 'independent-native-slot' }))
vi.mock('@/api/pms/engineering/configuration', () => ({ getConfiguration: mocks.get, updateConfiguration: mocks.update }))
vi.mock('@/api/pms/engineering/joint-test', () => ({ getJointTest: mocks.get, updateJointTest: mocks.update }))
vi.mock('@/api/pms/engineering/ext-proc', () => ({ getExternalProcurement: mocks.get, updateExternalProcurement: mocks.update }))
vi.mock('@/api/pms/engineering/outsource', () => ({ getOutsourceRequest: mocks.get, updateOutsourceRequest: mocks.update }))
vi.mock('@/api/pms/engineering/material-req', () => ({ getMaterialRequisition: mocks.get, updateMaterialRequisition: mocks.update }))
vi.mock('@/api/pms/engineering/material-exch', () => ({ getMaterialExchange: mocks.get, updateMaterialExchange: mocks.update }))
vi.mock('@/api/pms/platform/delivery', () => ({ listMaterials: mocks.list, withdrawMaterial: mocks.withdraw }))
vi.mock('@/components/PmsFileArtifact', () => ({ PmsFileUploader: defineComponent({ setup(_, { attrs }) { return () => h('native-uploader', attrs) } }), PmsFileReferenceList: defineComponent({ setup(_, { attrs }) { return () => h('native-file', attrs) } }) }))
const nodes = (n: TestNode, type: string): TestNode[] => [...(n.type === type ? [n] : []), ...n.children.flatMap(c => nodes(c, type))]
const flush = async () => { for (let i = 0; i < 12; i++) await nextTick() }
beforeEach(() => { vi.resetAllMocks(); mocks.list.mockResolvedValue([]); mocks.get.mockResolvedValue({ id: '9007199254740993', projectId: 20, status: 0, version: 3, remark: 'saved Owner', serials: [{ sn: 'immutable-snapshot' }] }) })
const kinds = ['configuration', 'jointTest', 'externalProcurement', 'outsourceRequest', 'materialRequisition', 'materialExchange'] as const
it.each(kinds)('%s retries actual native collection without a new file upload', async kind => {
  const mounted = mount(Attachments, { kind, entityId: '9007199254740993', readonly: false }); const state = (mounted.vm as any).$.setupState
  try {
    await flush(); const uploader = nodes(mounted.root, 'native-uploader')[0]
    expect(uploader.props?.['object-id']).toBe('9007199254740993')
    mocks.update.mockRejectedValueOnce(new Error('native registration unavailable'))
    await testEventHandler(uploader, 'onCompleted')({ artifactId: '9007199254740995', versionNo: 1 }); await flush()
    expect(state.pending).toBe(true); expect(state.error).toContain('registration unavailable'); expect(nodes(mounted.root, 'native-uploader')).toEqual([])
    await state.collect(); await flush()
    expect(mocks.update).toHaveBeenCalledTimes(2); expect(mocks.update).toHaveBeenLastCalledWith(expect.objectContaining({ id: '9007199254740993', version: 3, remark: 'saved Owner' }))
    if (kind === 'materialExchange') expect(mocks.update.mock.calls[1][0].serials).toBeUndefined()
    expect(state.pending).toBe(false); expect(mocks.list).toHaveBeenLastCalledWith(kind === 'outsourceRequest' ? 'RES' : 'IMP', kind, '9007199254740993', expect.any(String))
  } finally { mounted.app.unmount() }
})
it.each(kinds)('%s rejects collecting into a frozen native Owner', async kind => {
  mocks.get.mockResolvedValue({ id: 19, status: kind === 'configuration' || kind === 'jointTest' ? 2 : 1, version: 4 })
  const mounted = mount(Attachments, { kind, entityId: 19, readonly: false }); const state = (mounted.vm as any).$.setupState
  try { await flush(); await state.collect(); expect(mocks.update).not.toHaveBeenCalled(); expect(state.error).toContain('不可编辑') }
  finally { mounted.app.unmount() }
})
it('readonly history cannot upload, save or withdraw', async () => {
  const mounted = mount(Attachments, { kind: 'outsourceRequest', entityId: 19, readonly: true }); const state = (mounted.vm as any).$.setupState
  try { await flush(); expect(nodes(mounted.root, 'native-uploader')).toEqual([]); await state.collect(); await state.withdraw(1); expect(mocks.update).not.toHaveBeenCalled(); expect(mocks.withdraw).not.toHaveBeenCalled() }
  finally { mounted.app.unmount() }
})
it('a draft needs its saved native identity before file operations', async () => {
  const mounted = mount(Attachments, { kind: 'configuration', readonly: false }); const state = (mounted.vm as any).$.setupState
  try { await flush(); expect(nodes(mounted.root, 'native-uploader')).toEqual([]); await state.collect(); expect(mocks.get).not.toHaveBeenCalled(); expect(mocks.list).not.toHaveBeenCalled() }
  finally { mounted.app.unmount() }
})
it('an upload completion from the previous Owner cannot save the newly opened Owner', async () => {
  const props = reactive({ kind: 'configuration' as typeof kinds[number], entityId: 30, readonly: false })
  const mounted = mount(defineComponent({ setup: () => () => h(Attachments, props) }))
  try {
    await flush()
    const oldCompleted = nodes(mounted.root, 'native-uploader')[0].props!.onCompleted as () => Promise<void>
    props.entityId = 31; props.kind = 'jointTest'
    await flush()
    await oldCompleted(); await flush()
    expect(mocks.get).not.toHaveBeenCalled()
    expect(mocks.update).not.toHaveBeenCalled()
    expect(nodes(mounted.root, 'native-uploader')[0].props?.['object-id']).toBe('31')
  } finally { mounted.app.unmount() }
})
it('a late Owner lookup cannot collect files after the record changes', async () => {
  let resolveOwner!: (owner: any) => void
  mocks.get.mockImplementationOnce(() => new Promise(resolve => { resolveOwner = resolve }))
  const props = reactive({ kind: 'configuration' as const, entityId: 30, readonly: false })
  const mounted = mount(defineComponent({ setup: () => () => h(Attachments, props) }))
  try {
    await flush()
    const completed = nodes(mounted.root, 'native-uploader')[0].props!.onCompleted as () => Promise<void>
    const collecting = completed()
    await flush(); props.entityId = 31; await flush()
    resolveOwner({ id: 30, projectId: 20, status: 0, version: 0 })
    await collecting; await flush()
    expect(mocks.update).not.toHaveBeenCalled()
    expect(nodes(mounted.root, 'native-uploader')[0].props?.['object-id']).toBe('31')
  } finally { mounted.app.unmount() }
})
