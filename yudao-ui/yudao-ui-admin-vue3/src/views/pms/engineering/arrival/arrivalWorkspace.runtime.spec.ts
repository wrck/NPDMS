import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import Arrival from './index.vue'
import * as ArrivalApi from '@/api/pms/engineering/arrival'
import { listMaterials, withdrawMaterial } from '@/api/pms/platform/delivery'
import { getArtifact } from '@/api/pms/platform/file'
import { testEventHandler, mount, passthrough, tableColumn, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/api/pms/project/projects', () => ({ __v_isRef: false, getProjectPage: vi.fn() }))
vi.mock('@/api/pms/asset/device/archive', () => ({ __v_isRef: false, getDeviceArchivePage: vi.fn() }))
vi.mock('@/api/pms/engineering/arrival', () => ({ getArrivalPage: vi.fn(), getArrival: vi.fn(), createArrival: vi.fn(), updateArrival: vi.fn(), deleteArrival: vi.fn() }))
vi.mock('@/api/pms/platform/delivery', () => ({ listMaterials: vi.fn(), withdrawMaterial: vi.fn() }))
vi.mock('@/api/pms/platform/file', () => ({ getArtifact: vi.fn() }))
vi.mock('@/components/BusinessEntity/DeliveryPanel.vue', () => ({ default: defineComponent({ setup(_, { attrs }) { return () => h('delivery-panel', attrs) } }) }))
vi.mock('@/components/PmsFileArtifact', () => ({
  PmsFileReferenceList: defineComponent({ setup(_, { attrs }) { return () => h('pms-file-reference-list', attrs) } }),
  PmsFileUploader: defineComponent({ setup(_, { attrs }) { return () => h('pms-file-uploader', attrs) } })
}))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: { PMS_ARRIVAL_STATUS: 'arrival' }, getIntDictOptions: () => [] }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn(), delConfirm: vi.fn() }) }))
const formStub = defineComponent({ setup(_, { slots, attrs, expose }) {
  expose({ validate: async () => true })
  return () => h('form', attrs, slots.default?.())
} })
const editorStub = defineComponent({ setup(_, { attrs }) { return () => h('editor', attrs) } })
const nodes = (node: TestNode, type: string): TestNode[] => [...(node.type === type ? [node] : []), ...node.children.flatMap(child => nodes(child, type))]
const flush = async () => { for (let i = 0; i < 5; i++) await nextTick() }
const render = () => {
  const mounted = mount(Arrival, {}, { ElTable: passthrough, ElTableColumn: tableColumn, ElForm: formStub, ElRow: passthrough, ElCol: passthrough, ElInput: passthrough, ElInputNumber: passthrough, ElDatePicker: passthrough, ElSelect: passthrough, ElOption: passthrough, PmsEntitySelect: passthrough, Editor: editorStub })
  return { ...mounted, state: (mounted.vm as any).$.setupState }
}
const SIGN_SLOT = {
  'owner-context': 'IMP',
  'object-type': 'ARRIVAL',
  'purpose-code': 'ARRIVAL_SIGN_DOCUMENT',
  'reference-key': 'arrival-sign-document'
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(ArrivalApi.getArrivalPage).mockResolvedValue({ list: [], total: 0 })
  vi.mocked(listMaterials).mockResolvedValue([])
})

it('makes a signed record read-only and freezes its sign document slot', async () => {
  const mounted = render()
  try {
    const signed = { id: 8, projectId: 1, code: 'ARR-SIGNED', status: 1, version: 2, attachmentUrl: 'original-file' }
    mounted.state.openForm(signed); await flush()
    expect(mounted.state.readOnly).toBe(true)
    expect(nodes(mounted.root, 'editor').every(node => node.props?.readonly === true)).toBe(true)
    expect(nodes(mounted.root, 'pms-file-uploader')).toEqual([])
    expect(nodes(mounted.root, 'pms-file-reference-list')[0].props?.editable).toBe(false)
    await mounted.state.save(); await mounted.state.remove(signed)
    expect(ArrivalApi.updateArrival).not.toHaveBeenCalled()
    expect(ArrivalApi.deleteArrival).not.toHaveBeenCalled()
  } finally { mounted.app.unmount() }
})

it('creates a pending record without inheriting a viewed record status or evidence', async () => {
  const mounted = render()
  try {
    mounted.state.openForm({ id: 8, projectId: 1, code: 'ARR-SIGNED', status: 1, version: 2, attachmentUrl: 'original-file' })
    mounted.state.openForm(); await flush()
    Object.assign(mounted.state.form, { projectId: 1, code: 'ARR-NEW', arrivalTime: '1788055200000' })
    await mounted.state.save()
    expect(ArrivalApi.createArrival).toHaveBeenCalledWith(expect.objectContaining({ status: 0, attachmentUrl: '', version: undefined, arrivalTime: 1788055200000 }))
  } finally { mounted.app.unmount() }
})

it('keeps the dialog on the saved record after create so the sign document can be attached', async () => {
  const mounted = render()
  try {
    vi.mocked(ArrivalApi.createArrival).mockResolvedValue(77)
    mounted.state.openForm(); await flush()
    Object.assign(mounted.state.form, { projectId: 1, code: 'ARR-NEW', arrivalTime: '1788055200000' })
    await mounted.state.save(); await flush()
    expect(mounted.state.form.id).toBe(77)
    expect(mounted.state.form.version).toBe(0)
    const uploader = nodes(mounted.root, 'pms-file-uploader')[0]
    expect(uploader.props).toMatchObject({ ...SIGN_SLOT, 'object-id': '77', 'category-code': 'ARRIVAL_SIGN_DOCUMENT' })
    expect(nodes(mounted.root, 'pms-file-reference-list')[0].props).toMatchObject({ ...SIGN_SLOT, 'object-id': '77' })
    vi.mocked(ArrivalApi.getArrival).mockResolvedValue({ id: 77, projectId: 1, status: 0, version: 0 })
    await testEventHandler(uploader, 'onCompleted')({ artifactId: 9001, versionNo: 1, referenceId: 501, referenceKey: 'arrival-sign-document' })
    await flush()
    expect(nodes(mounted.root, 'pms-file-reference-list')[0].props).toMatchObject({ ...SIGN_SLOT, 'object-id': '77', 'artifact-id': 9001 })
    expect(nodes(mounted.root, 'pms-file-uploader')[0].props).toMatchObject({ 'artifact-id': 9001 })
    mounted.state.onSignDocumentLoaded({ artifactId: 9001, reference: { referenceKey: 'arrival-sign-document', referenceVersion: 3 } })
    await flush()
    expect(nodes(mounted.root, 'pms-file-uploader')[0].props).toMatchObject({ 'artifact-id': 9001, 'expected-reference-version': 3 })
  } finally { mounted.app.unmount() }
})

it('collects an uploaded file through the saved Owner without saving unrelated local edits', async () => {
  const mounted = render()
  try {
    mounted.state.openForm({ id: 77, projectId: 1, status: 0, version: 3, remark: 'saved' }); await flush()
    mounted.state.form.remark = 'unsaved local edit'
    vi.mocked(ArrivalApi.getArrival).mockResolvedValueOnce({ id: 77, projectId: 1, status: 0, version: 3, remark: 'saved' })
      .mockResolvedValueOnce({ id: 77, projectId: 1, status: 0, version: 4, remark: 'saved' })
    const before = mounted.state.deliveryEpoch
    await mounted.state.onSignDocumentUploaded(77, { artifactId: 9001, versionNo: 1, referenceId: 501 }); await flush()
    expect(ArrivalApi.updateArrival).toHaveBeenCalledWith(expect.objectContaining({ id: 77, version: 3, remark: 'saved' }))
    expect(mounted.state.form.remark).toBe('unsaved local edit')
    expect(mounted.state.form.version).toBe(4)
    expect(mounted.state.deliveryEpoch).toBeGreaterThan(before)
    expect(mounted.state.deliveryError).toBe('')
  } finally { mounted.app.unmount() }
})

it('reopens the existing native source slot from material identity and blocks upload on a failed read', async () => {
  const mounted = render()
  try {
    const key = { ownerContext: 'IMP', objectType: 'ARRIVAL', objectId: '77', purposeCode: 'ARRIVAL_SIGN_DOCUMENT', referenceKey: 'arrival-sign-document' }
    vi.mocked(listMaterials).mockResolvedValueOnce([{ materialKind: 'FILE', fileArtifactId: 9001, fileBusinessKey: key }] as any)
    vi.mocked(getArtifact).mockResolvedValueOnce({ artifactId: 9001, reference: { status: 'ACTIVE', referenceVersion: 3 } } as any)
    mounted.state.openForm({ id: 77, projectId: 1, status: 0, version: 3 }); await flush()
    expect(getArtifact).toHaveBeenCalledWith(9001, key)
    expect(nodes(mounted.root, 'pms-file-uploader')[0].props).toMatchObject({ 'artifact-id': 9001, 'expected-reference-version': 3 })
    vi.mocked(listMaterials).mockRejectedValueOnce(new Error('source read unavailable'))
    mounted.state.openForm({ id: 77, projectId: 1, status: 0, version: 3 }); await flush()
    expect(mounted.state.slotError).toContain('source read unavailable')
    expect(nodes(mounted.root, 'pms-file-uploader')).toEqual([])
  } finally { mounted.app.unmount() }
})

it('retains failed collection for explicit Owner retry and ignores a late upload from another record', async () => {
  const mounted = render()
  try {
    mounted.state.openForm({ id: 77, projectId: 1, status: 0, version: 3 }); await flush()
    const originalCompleted = testEventHandler(nodes(mounted.root, 'pms-file-uploader')[0], 'onCompleted')
    vi.mocked(ArrivalApi.getArrival).mockResolvedValue({ id: 77, projectId: 1, status: 0, version: 3 })
    vi.mocked(ArrivalApi.updateArrival).mockRejectedValueOnce(new Error('registration failed'))
    await mounted.state.onSignDocumentUploaded(77, { artifactId: 9001, versionNo: 1, referenceId: 501 }); await flush()
    expect(mounted.state.deliveryError).toContain('registration failed')
    expect(mounted.state.signSlot.artifactId).toBe(9001)
    await mounted.state.collectSignDocument(77)
    expect(ArrivalApi.updateArrival).toHaveBeenCalledTimes(2)
    expect(mounted.state.deliveryError).toBe('')
    mounted.state.openForm({ id: 88, projectId: 1, status: 0, version: 1 }); await flush()
    await originalCompleted({ artifactId: 9002, versionNo: 1, referenceId: 502 })
    expect(ArrivalApi.updateArrival).toHaveBeenCalledTimes(2)
    expect(mounted.state.signSlot).toBeUndefined()
  } finally { mounted.app.unmount() }
})

it('withdraws only actually superseded or detached materials from the exact native slot', async () => {
  const mounted = render()
  try {
    mounted.state.openForm({ id: 77, projectId: 1, status: 0, version: 3 }); await flush()
    const key = { ownerContext: 'IMP', objectType: 'ARRIVAL', objectId: '77', purposeCode: 'ARRIVAL_SIGN_DOCUMENT', referenceKey: 'arrival-sign-document' }
    const material = { status: 'ACTIVE', materialKind: 'FILE', fileArtifactId: 9001, fileBusinessKey: key }
    vi.mocked(listMaterials).mockResolvedValue([
      { ...material, id: 1, fileVersionNo: 1 }, { ...material, id: 2, fileVersionNo: 2 },
      { ...material, id: 3, fileVersionNo: 1, fileBusinessKey: { ...key, objectId: '88' } },
      { ...material, id: 4, fileVersionNo: 1, status: 'WITHDRAWN' }
    ] as any)
    vi.mocked(getArtifact).mockResolvedValue({ artifactId: 9001, reference: { status: 'ACTIVE', versionNo: 2 } } as any)
    await mounted.state.withdrawObsoleteSignMaterials(77)
    expect(withdrawMaterial).toHaveBeenCalledExactlyOnceWith(1)
    vi.mocked(getArtifact).mockResolvedValue({ artifactId: 9001, reference: { status: 'DETACHED', versionNo: 2 } } as any)
    await mounted.state.withdrawObsoleteSignMaterials(77)
    expect(withdrawMaterial).toHaveBeenCalledWith(2)
    vi.mocked(getArtifact).mockResolvedValue(null as any)
    const count = vi.mocked(withdrawMaterial).mock.calls.length
    await mounted.state.withdrawObsoleteSignMaterials(77)
    expect(withdrawMaterial).toHaveBeenCalledTimes(count)
    expect(mounted.state.deliveryError).toContain('未撤回材料')
    mounted.state.openForm({ id: 77, projectId: 1, status: 1, version: 3 }); await flush()
    await mounted.state.withdrawObsoleteSignMaterials(77)
    expect(withdrawMaterial).toHaveBeenCalledTimes(count)
  } finally { mounted.app.unmount() }
})

it('keeps local pending and abnormal records editable with their existing version', async () => {
  const mounted = render()
  try {
    for (const status of [0, 2]) {
      mounted.state.openForm({ id: 9, projectId: 1, code: 'ARR-LOCAL', status, version: 3 }); await flush()
      expect(mounted.state.readOnly).toBe(false)
      mounted.state.form.remark = '本地补充'
      await mounted.state.save()
      expect(ArrivalApi.updateArrival).toHaveBeenLastCalledWith(expect.objectContaining({ id: 9, status, version: 3, remark: '本地补充' }))
    }
  } finally { mounted.app.unmount() }
})

it('preserves the legacy attachment payload and renders it as read-only links', async () => {
  const mounted = render()
  try {
    mounted.state.openForm({ id: 9, projectId: 1, code: 'ARR-LEGACY', status: 0, version: 1, attachmentUrl: null })
    await flush()
    expect(mounted.state.legacyAttachments).toEqual([])
    await mounted.state.save()
    expect(ArrivalApi.updateArrival).toHaveBeenCalledWith(expect.objectContaining({ attachmentUrl: '', version: 1 }))

    mounted.state.openForm({ id: 10, projectId: 1, code: 'ARR-LINKS', status: 0, version: 1, attachmentUrl: 'http://f/a.png,http://f/b.pdf' })
    await flush()
    const links = nodes(mounted.root, 'a')
    expect(links.map(node => node.props?.href)).toEqual(['http://f/a.png', 'http://f/b.pdf'])
    expect(links.every(node => node.props?.target === '_blank')).toBe(true)
  } finally { mounted.app.unmount() }
})
