import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import Arrival from './index.vue'
import * as ArrivalApi from '@/api/pms/engineering/arrival'
import { mount, passthrough, tableColumn, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/api/pms/project/project', () => ({ __v_isRef: false, getProjectPage: vi.fn() }))
vi.mock('@/api/pms/asset/equipment', () => ({ __v_isRef: false, getEquipmentPage: vi.fn() }))
vi.mock('@/api/pms/engineering/arrival', () => ({ getArrivalPage: vi.fn(), createArrival: vi.fn(), updateArrival: vi.fn(), deleteArrival: vi.fn() }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: { PMS_ARRIVAL_STATUS: 'arrival' }, getIntDictOptions: () => [] }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn(), delConfirm: vi.fn() }) }))
const formStub = defineComponent({ setup(_, { slots, attrs, expose }) {
  expose({ validate: async () => true })
  return () => h('form', attrs, slots.default?.())
} })
const editorStub = defineComponent({ setup(_, { attrs }) { return () => h('editor', attrs) } })
const uploaderStub = defineComponent({ setup(_, { attrs }) { return () => h('uploader', attrs) } })
const nodes = (node: TestNode, type: string): TestNode[] => [...(node.type === type ? [node] : []), ...node.children.flatMap(child => nodes(child, type))]
const flush = async () => { for (let i = 0; i < 5; i++) await nextTick() }
const render = () => {
  const mounted = mount(Arrival, {}, { ElTable: passthrough, ElTableColumn: tableColumn, ElForm: formStub, ElRow: passthrough, ElCol: passthrough, ElInput: passthrough, ElInputNumber: passthrough, ElDatePicker: passthrough, ElSelect: passthrough, ElOption: passthrough, PmsEntitySelect: passthrough, Editor: editorStub, UploadFile: uploaderStub })
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
    expect(nodes(mounted.root, 'editor').every(node => node.props?.readonly === true)).toBe(true)
    expect(nodes(mounted.root, 'uploader')[0].props?.disabled).toBe(true)
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

it('normalizes null legacy evidence before upload so reopening does not switch the API payload to an array', async () => {
  const mounted = render()
  try {
    mounted.state.openForm({ id: 9, projectId: 1, code: 'ARR-NO-FILE', status: 0, version: 1, attachmentUrl: null })
    await flush()
    const uploader = nodes(mounted.root, 'uploader')[0]
    expect(uploader.props?.modelValue).toBe('')
    const emitValue = uploader.props?.['onUpdate:modelValue'] as (value: string) => void
    emitValue('receipt-url')
    await mounted.state.save()
    expect(ArrivalApi.updateArrival).toHaveBeenCalledWith(expect.objectContaining({ attachmentUrl: 'receipt-url', version: 1 }))
  } finally { mounted.app.unmount() }
})
