import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import Arrival from './ProjectArrivalReceiptPanel.vue'
import * as ArrivalApi from '@/api/pms/engineering/arrival'
import { mount, passthrough, tableColumn, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/api/system/user', () => ({ __v_isRef: false, getUserPage: vi.fn(), getSimpleUser: vi.fn() }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ getUser: { id: 8, nickname: '测试用户' } }) }))
vi.mock('@/api/pms/engineering/arrival', () => ({ getArrivalPage: vi.fn(), createArrival: vi.fn(), updateArrival: vi.fn(), deleteArrival: vi.fn() }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: { PMS_ARRIVAL_STATUS: 'arrival' }, getIntDictOptions: () => [] }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn(), confirm: vi.fn(), delConfirm: vi.fn() }) }))
const formStub = defineComponent({ setup(_, { slots, attrs, expose }) {
  expose({ validate: async () => true })
  return () => h('form', attrs, slots.default?.())
} })
const uploaderStub = defineComponent({ setup(_, { attrs }) { return () => h('uploader', attrs) } })
const nodes = (node: TestNode, type: string): TestNode[] => [...(node.type === type ? [node] : []), ...node.children.flatMap(child => nodes(child, type))]
const flush = async () => { for (let i = 0; i < 5; i++) await nextTick() }
const uploadValue = (mounted: ReturnType<typeof render>): (value: string) => void => {
  const listener = nodes(mounted.root, 'uploader')[0].props?.['onUpdate:modelValue'] as any
  return (value: string) => (Array.isArray(listener) ? listener : [listener]).forEach(fn => fn(value))
}
const render = () => {
  const mounted = mount(Arrival, { projectId: 1 }, { ElTable: passthrough, ElTableColumn: tableColumn, ElForm: formStub, ElRow: passthrough, ElCol: passthrough, UploadFile: uploaderStub })
  return { ...mounted, state: (mounted.vm as any).$.setupState }
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(ArrivalApi.getArrivalPage).mockResolvedValue({ list: [], total: 0 })
})

it('makes a signed record and its attachment read-only in the existing page', async () => {
  const mounted = render()
  try {
    const signed = { id: 8, projectId: 1, code: 'ARR-SIGNED', status: 1, version: 2, attachmentUrl: 'original-file' }
    mounted.state.openForm(signed); await flush()
    expect(mounted.state.readOnly).toBe(true)
    expect(nodes(mounted.root, 'uploader')[0].props?.disabled).toBe(true)
    await mounted.state.save(); await mounted.state.remove(signed)
    expect(ArrivalApi.updateArrival).not.toHaveBeenCalled()
    expect(ArrivalApi.deleteArrival).not.toHaveBeenCalled()
  } finally { mounted.app.unmount() }
})

it('creates a pending record with receiver defaulting to the current user and completion time auto-filled from upload', async () => {
  const mounted = render()
  try {
    mounted.state.openForm({ id: 8, projectId: 1, code: 'ARR-SIGNED', status: 1, version: 2, attachmentUrl: 'original-file' })
    mounted.state.openForm(); await flush()
    expect(mounted.state.receiverName).toBe('测试用户')
    expect(mounted.state.form.receiverUserId).toBe(8)
    expect(mounted.state.completionTimeText).toBe('上传交付件后自动填入')
    uploadValue(mounted)('receipt-url')
    const autoTime = mounted.state.form.arrivalTime
    expect(autoTime).toBeTypeOf('number')
    expect(mounted.state.completionTimeText).not.toBe('上传交付件后自动填入')
    await mounted.state.save()
    expect(ArrivalApi.createArrival).toHaveBeenCalledWith(expect.objectContaining({ status: 0, attachmentUrl: 'receipt-url', version: undefined, arrivalTime: autoTime, receiverUserId: 8 }))
  } finally { mounted.app.unmount() }
})

it('keeps local pending and abnormal records editable with their existing version', async () => {
  const mounted = render()
  try {
    for (const status of [0, 2]) {
      mounted.state.openForm({ id: 9, projectId: 1, code: 'ARR-LOCAL', status, version: 3 }); await flush()
      expect(mounted.state.readOnly).toBe(false)
      uploadValue(mounted)('receipt-url')
      await mounted.state.save()
      expect(ArrivalApi.updateArrival).toHaveBeenLastCalledWith(expect.objectContaining({ id: 9, status, version: 3, attachmentUrl: 'receipt-url', arrivalTime: expect.any(Number) }))
    }
  } finally { mounted.app.unmount() }
})

it('normalizes null legacy evidence before upload so reopening does not switch the API payload to an array', async () => {
  const mounted = render()
  try {
    mounted.state.openForm({ id: 9, projectId: 1, code: 'ARR-NO-FILE', status: 0, version: 1, attachmentUrl: null })
    await flush()
    const uploader = nodes(mounted.root, 'uploader')[0]
    expect(uploader.props?.modelValue).toBe('')
    uploadValue(mounted)('receipt-url')
    await mounted.state.save()
    expect(ArrivalApi.updateArrival).toHaveBeenCalledWith(expect.objectContaining({ attachmentUrl: 'receipt-url', version: 1 }))
  } finally { mounted.app.unmount() }
})

it('keeps both list and create in the enclosing project', async () => {
  const mounted = render()
  try {
    mounted.state.query.projectId = 99
    await mounted.state.load()
    expect(ArrivalApi.getArrivalPage).toHaveBeenLastCalledWith(expect.objectContaining({ projectId: 1 }))
    mounted.state.openForm()
    uploadValue(mounted)('receipt-url')
    await mounted.state.save()
    expect(ArrivalApi.createArrival).toHaveBeenLastCalledWith(expect.objectContaining({ projectId: 1 }))
  } finally { mounted.app.unmount() }
})
