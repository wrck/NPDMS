import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import JointTest from './index.vue'
import * as JointTestApi from '@/api/pms/engineering/joint-test'
import { mount, passthrough, tableColumn, findByTestId, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const warning = vi.hoisted(() => vi.fn())
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/api/pms/project/project', () => ({ __v_isRef: false, getProjectPage: vi.fn() }))
vi.mock('@/api/pms/asset/equipment', () => ({ __v_isRef: false, getEquipmentPage: vi.fn() }))
vi.mock('@/components/EquipmentTag/index.vue', () => ({ default: { render: () => null } }))
vi.mock('@/api/pms/engineering/joint-test', () => ({ getJointTestPage: vi.fn(), createJointTest: vi.fn(), updateJointTest: vi.fn(), deleteJointTest: vi.fn(), failJointTest: vi.fn() }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: { PMS_JOINT_TEST_STATUS: 'joint' }, getIntDictOptions: () => [] }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ warning, success: vi.fn(), delConfirm: vi.fn() }) }))
const formStub = defineComponent({ setup(_, { slots, attrs, expose }) {
  expose({ validate: async () => true })
  return () => h('form', attrs, slots.default?.())
} })
const uploaderStub = defineComponent({ setup(_, { attrs }) { return () => h('uploader', attrs) } })
const uploaders = (node: TestNode): TestNode[] => [...(node.type === 'uploader' ? [node] : []), ...node.children.flatMap(uploaders)]
const render = () => {
  const mounted = mount(JointTest, {}, { ElTable: passthrough, ElTableColumn: tableColumn, ElForm: formStub, ElRow: passthrough, ElCol: passthrough, ElInput: passthrough, ElDatePicker: passthrough, ElSelect: passthrough, ElOption: passthrough, PmsEntitySelect: passthrough, Editor: passthrough, UploadFile: uploaderStub })
  return { ...mounted, state: (mounted.vm as any).$.setupState }
}
const flush = async () => { for (let i = 0; i < 5; i++) await nextTick() }
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(JointTestApi.getJointTestPage).mockResolvedValue({ list: [], total: 0 })
})

it('shows the retained failure reason without allowing terminal result edits or deletion', async () => {
  const mounted = render()
  try {
    const failed = { id: 8, projectId: 1, code: 'JT-FAILED', testCase: 'test case', status: 3, version: 2, exceptionRecord: 'retained failure' }
    mounted.state.openForm(failed); await flush()
    expect(mounted.state.readOnly).toBe(true)
    expect(findByTestId(mounted.root, 'joint-test-exception')?.props?.['model-value']).toBe('retained failure')
    await mounted.state.save(); await mounted.state.remove(failed)
    expect(JointTestApi.updateJointTest).not.toHaveBeenCalled()
    expect(JointTestApi.deleteJointTest).not.toHaveBeenCalled()
  } finally { mounted.app.unmount() }
})

it('starts a clean local record and emits the exact device ID and timestamp', async () => {
  const mounted = render()
  try {
    mounted.state.openForm({ id: 8, projectId: 1, code: 'OLD', testCase: 'old', status: 3, version: 2, exceptionRecord: 'old reason', evidenceUrl: 'old evidence' })
    mounted.state.openForm(); await flush()
    Object.assign(mounted.state.form, { projectId: 1, code: 'NEW', testCase: 'local check', equipmentId: '970000000000090104', testTime: '1788055200000' })
    await mounted.state.save()
    expect(JointTestApi.createJointTest).toHaveBeenCalledWith(expect.objectContaining({ status: 0, exceptionRecord: '', evidenceUrl: '', testTime: 1788055200000, equipmentId: '970000000000090104' }))
  } finally { mounted.app.unmount() }
})

it('requires a nonblank failure reason and submits its trimmed text', async () => {
  const mounted = render()
  try {
    mounted.state.handleFail({ id: 8 })
    mounted.state.failForm.exceptionRecord = '   '
    await mounted.state.confirmFail()
    expect(warning).toHaveBeenCalledWith('请输入异常记录')
    expect(JointTestApi.failJointTest).not.toHaveBeenCalled()
    mounted.state.failForm.exceptionRecord = ' local failure '
    await mounted.state.confirmFail()
    expect(JointTestApi.failJointTest).toHaveBeenCalledWith(8, 'local failure')
  } finally { mounted.app.unmount() }
})

it('keeps the string upload contract when reopening a record with no evidence', async () => {
  const mounted = render()
  try {
    mounted.state.openForm({ id: 9, projectId: 1, code: 'JT-NO-EVIDENCE', testCase: 'local check', status: 1, version: 2, evidenceUrl: null })
    await flush()
    const uploader = uploaders(mounted.root)[0]
    expect(uploader.props?.modelValue).toBe('')
    const emitValue = uploader.props?.['onUpdate:modelValue'] as (value: string) => void
    emitValue('local-evidence-url')
    await mounted.state.save()
    expect(JointTestApi.updateJointTest).toHaveBeenCalledWith(expect.objectContaining({ evidenceUrl: 'local-evidence-url', status: 1, version: 2 }))
  } finally { mounted.app.unmount() }
})
