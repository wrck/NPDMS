import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive } from 'vue'
import { mount } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Attachments from './NativeAttachments.vue'
import AdditionalAttachments from './AdditionalNativeAttachments.vue'
import ChecklistAttachments from '@/views/pms/acceptance/deliverable-checklist/ChecklistAttachments.vue'
const api = vi.hoisted(() => ({
  get: vi.fn(),
  update: vi.fn(),
  list: vi.fn(),
  init: vi.fn(),
  complete: vi.fn()
}))
vi.mock('@/components/BusinessEntity/DeliveryPanel.vue', () => ({
  default: { render: () => null }
}))
vi.mock('@/components/PmsFileArtifact', () => ({
  PmsFileUploader: { render: () => null },
  PmsFileReferenceList: { render: () => null }
}))
vi.mock('@/api/pms/engineering/configuration', () => ({
  getConfiguration: api.get,
  updateConfiguration: api.update
}))
vi.mock('@/api/pms/engineering/joint-test', () => ({
  getJointTest: api.get,
  updateJointTest: api.update
}))
vi.mock('@/api/pms/engineering/briefing', () => ({ getBriefing: api.get, updateBriefing: api.update }))
vi.mock('@/api/pms/acceptance/acceptance', () => ({ getAcceptance: vi.fn(), updateAcceptance: vi.fn() }))
vi.mock('@/api/pms/acceptance/deliverable-checklist', () => ({ getDeliverableChecklist: api.get, updateDeliverableChecklist: api.update }))
vi.mock('@/api/pms/engineering/ext-proc', () => ({
  getExternalProcurement: api.get,
  updateExternalProcurement: api.update
}))
vi.mock('@/api/pms/engineering/outsource', () => ({
  getOutsourceRequest: api.get,
  updateOutsourceRequest: api.update
}))
vi.mock('@/api/pms/engineering/material-req', () => ({
  getMaterialRequisition: api.get,
  updateMaterialRequisition: api.update
}))
vi.mock('@/api/pms/engineering/material-exch', () => ({
  getMaterialExchange: vi.fn(),
  updateMaterialExchange: vi.fn()
}))
vi.mock('@/api/pms/platform/delivery', () => ({
  listMaterials: api.list,
  withdrawMaterial: vi.fn()
}))
vi.mock('@/api/pms/platform/file', () => ({
  initializeUpload: api.init,
  completeUpload: api.complete
}))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/hooks/web/useDraftUploads', () => ({ registerDraftUpload: vi.fn() }))
const flush = async () => {
  for (let i = 0; i < 8; i++) await nextTick()
}
type DraftKind = 'configuration' | 'jointTest' | 'briefing' | 'checklist' | 'externalProcurement' | 'outsourceRequest' | 'materialRequisition'
const render = (kind: DraftKind = 'configuration') => {
  const props = reactive({
    kind,
    entityId: undefined as number | undefined,
    readonly: false
  })
  let child: any
  const view = mount(
    defineComponent({
      setup: () => () =>
        h((kind === 'briefing' ? AdditionalAttachments : kind === 'checklist' ? ChecklistAttachments : Attachments) as any, {
          ...props,
          ref: (value: any) => {
            child = value
          }
        })
    })
  )
  return { ...view, props, state: () => child.$.setupState }
}
const select = (state: any) =>
  state.selectPending({
    uid: 17,
    name: 'test.txt',
    status: 'ready',
    raw: Object.assign(new File(['test'], 'test.txt'), { uid: 17 })
  })
beforeEach(() => {
  vi.clearAllMocks()
  api.list.mockResolvedValue([])
  api.get.mockResolvedValue({ id: 91, projectId: 5, status: 0, version: 4, remark: 'saved draft' })
  api.update.mockResolvedValue(undefined)
  api.init.mockResolvedValue({ artifactId: '9007199254740993', sessionId: '9007199254740995' })
  api.complete.mockResolvedValue({
    artifactId: '9007199254740993',
    referenceId: '9007199254740997',
    versionNo: 1
  })
})
it.each(['configuration', 'jointTest', 'briefing', 'checklist', 'externalProcurement', 'outsourceRequest', 'materialRequisition'] as const)('%s allows selecting before the first save without writing a file or an Owner', async kind => {
  const view = render(kind)
  try {
    await flush()
    select(view.state())
    await flush()
    expect(view.state().pendingFiles).toHaveLength(1)
    expect(api.init).not.toHaveBeenCalled()
    expect(api.get).not.toHaveBeenCalled()
    expect(api.update).not.toHaveBeenCalled()
    view.props.entityId = 91
    await flush()
    expect(await view.state().savePending(91)).toBe(true)
    expect(api.init).toHaveBeenCalledWith(
      expect.objectContaining({ objectId: '91', objectType: kind === 'checklist' ? 'DELIVERABLE_CHECKLIST' : kind,
        ownerContext: kind === 'briefing' ? 'SOL' : kind === 'checklist' ? 'ACC' : kind === 'outsourceRequest' ? 'RES' : 'IMP' }),
      expect.any(String)
    )
    expect(api.update).toHaveBeenCalledWith(
      expect.objectContaining({ id: 91, version: 4, remark: 'saved draft' })
    )
    expect(view.state().pendingFiles).toHaveLength(0)
  } finally {
    view.app.unmount()
  }
})
it('retries an interrupted upload with the same session and keys, without collecting a missing file', async () => {
  const view = render()
  try {
    await flush()
    select(view.state())
    view.props.entityId = 91
    await flush()
    api.complete.mockRejectedValueOnce(new Error('connection lost'))
    expect(await view.state().savePending(91)).toBe(false)
    expect(api.update).not.toHaveBeenCalled()
    expect(view.state().pendingFiles).toHaveLength(1)
    expect(await view.state().savePending(91)).toBe(true)
    expect(api.init).toHaveBeenCalledTimes(1)
    expect(api.complete.mock.calls[0].slice(0, 4)).toEqual(api.complete.mock.calls[1].slice(0, 4))
  } finally {
    view.app.unmount()
  }
})
it('retries collection without uploading an already bound file again', async () => {
  const view = render()
  try {
    await flush()
    select(view.state())
    view.props.entityId = 91
    await flush()
    api.update.mockRejectedValueOnce(new Error('collection unavailable'))
    expect(await view.state().savePending(91)).toBe(false)
    expect(await view.state().savePending(91)).toBe(true)
    expect(api.complete).toHaveBeenCalledTimes(1)
    expect(api.update).toHaveBeenCalledTimes(2)
  } finally {
    view.app.unmount()
  }
})
it('does not upload when the record becomes readonly after selection', async () => {
  const view = render()
  try {
    await flush()
    select(view.state())
    view.props.entityId = 91
    view.props.readonly = true
    await flush()
    expect(await view.state().savePending(91)).toBe(false)
    expect(api.init).not.toHaveBeenCalled()
    expect(api.update).not.toHaveBeenCalled()
  } finally {
    view.app.unmount()
  }
})

it('does not present an already initialized attachment as removed after collection failure', async () => {
  const view = render()
  try {
    await flush(); select(view.state()); view.props.entityId = 91; await flush()
    api.update.mockRejectedValueOnce(new Error('collection unavailable'))
    expect(await view.state().savePending(91)).toBe(false)
    const selected = view.state().pendingFiles[0]
    expect(view.state().beforeRemovePending(selected)).toBe(false)
    view.state().removePending(selected)
    expect(view.state().pendingFiles).toHaveLength(1)
    expect(await view.state().savePending(91)).toBe(true)
    expect(api.complete).toHaveBeenCalledTimes(1)
  } finally { view.app.unmount() }
})

it('exposes collection retry when the post-save Owner refresh fails, without reuploading the file', async () => {
  const view = render()
  try {
    await flush(); select(view.state()); view.props.entityId = 91; await flush()
    api.get.mockResolvedValueOnce({ id: 91, projectId: 5, status: 0, version: 4 })
      .mockRejectedValueOnce(new Error('refresh unavailable'))
      .mockResolvedValue({ id: 91, projectId: 5, status: 0, version: 5 })
    expect(await view.state().savePending(91)).toBe(false)
    expect(view.state().pending).toBe(true)
    expect(await view.state().collect()).toBe(true)
    expect(api.update).toHaveBeenLastCalledWith(expect.objectContaining({ id: 91, version: 5 }))
    expect(await view.state().savePending(91)).toBe(true)
    expect(api.complete).toHaveBeenCalledTimes(1)
  } finally { view.app.unmount() }
})
