import { defineComponent, h, nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { findByTestId, mount, passthrough, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Panel from './CutoverPlanPanel.vue'

const api = vi.hoisted(() => ({ getCutoverPlan: vi.fn(), saveCutoverPlanDraft: vi.fn() }))
vi.mock('@/api/pms/cutover', () => api)
vi.mock('@/api/pms/platform/file/version-reference', () => ({}))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn(), warning: vi.fn() }) }))
vi.mock('@/components/PmsFileArtifact', () => ({ PmsFileUploader: defineComponent({ render: () => null }) }))
vi.mock('@/components/PmsFileArtifact/PmsFileVersionReferenceList.vue', () => ({ default: defineComponent({
  props: ['artifactId', 'versionNo'],
  setup(props) { return () => h('span', { 'data-testid': 'generated-file-reference' }, `${props.artifactId} V${props.versionNo}`) }
}) }))
vi.mock('./CutoverPlanEditor.vue', () => ({ default: defineComponent({
  props: ['modelValue'], emits: ['update:modelValue'],
  setup(props, { emit }) { return () => h('section', [
    h('button', { 'data-testid': 'edit-partial-contact', onClick: () => emit('update:modelValue', {
      ...props.modelValue, supportArrangements: props.modelValue.supportArrangements.map((row: { roleCode: string }, index: number) =>
        index === 0 ? { ...row, dutyDescription: '先保存待补电话的客户安排' } : row)
    }) }, '填写客户职责'),
    h('span', props.modelValue.supportArrangements?.find((row: { roleCode: string }) => row.roleCode === 'CUSTOMER')?.dutyDescription)
  ]) }
}) }))

const flush = async () => { for (let i = 0; i < 12; i++) { await Promise.resolve(); await nextTick() } }
const controls = { ElDatePicker: passthrough, ElDescriptions: passthrough, ElDescriptionsItem: passthrough,
  ElRadio: passthrough, ElRadioGroup: passthrough, ElCheckbox: passthrough }

describe('标准方案草稿保存', () => {
  it('保存并重新打开未填完整的保障安排，不把整行静默丢弃', async () => {
    const plan = {
      taskId: '101', taskVersion: 7, planRevisionId: '501', revisionNo: 1, planVersion: 0,
      status: 'DRAFT', sourceSnapshot: null, approvalFact: null, allowedActions: ['SAVE_DRAFT'],
      content: { editMode: 'ONLINE_TEMPLATE_STANDARD', overview: { projectDescription: '平台样例', deviceSummary: [], scheduleTable: [] },
        riskMitigations: [], steps: [],
        supportArrangements: [{ arrangementId: null, roleCode: 'CUSTOMER', personName: '项目样例联系人',
          dutyDescription: '', phone: '', arrivalTime: 0, customerContactReference: { contactId: '601', contactVersion: 0 } },
          { arrangementId: '502', roleCode: 'CUSTOMER', personName: '第二位项目联系人', dutyDescription: '另一项保障',
            phone: '', arrivalTime: 0, customerContactReference: { contactId: '602', contactVersion: 1 } }] }
    }
    api.getCutoverPlan.mockImplementation(async () => structuredClone(plan))
    api.saveCutoverPlanDraft.mockImplementation(async (command) => {
      plan.content = structuredClone(command.data)
      plan.planVersion++
      return { taskId: '101', planVersion: plan.planVersion }
    })
    const props = { taskId: '101', taskVersion: 7, manualGrade: 'C' }
    const page = mount(Panel, props, controls)
    await flush()
    await (findByTestId(page.root, 'edit-partial-contact')!.props!.onClick as () => void)()
    await nextTick()
    await (findByTestId(page.root, 'save-plan')!.props!.onClick as () => Promise<void>)()
    await flush()
    expect(api.saveCutoverPlanDraft.mock.calls[0][0].data.supportArrangements).toEqual(expect.arrayContaining([
      expect.objectContaining({ roleCode: 'CUSTOMER', personName: '项目样例联系人',
        dutyDescription: '先保存待补电话的客户安排', phone: '', arrivalTime: 0 })
    ]))
    expect(api.saveCutoverPlanDraft.mock.calls[0][0].data.supportArrangements).toHaveLength(2)
    expect(api.saveCutoverPlanDraft.mock.calls[0][0].data.supportArrangements[1]).toMatchObject({
      arrangementId: '502', personName: '第二位项目联系人', dutyDescription: '另一项保障', customerContactReference: { contactId: '602', contactVersion: 1 }
    })
    page.app.unmount()
    const reopened = mount(Panel, props, controls)
    await flush()
    expect(textOf(reopened.root)).toContain('先保存待补电话的客户安排')
    reopened.app.unmount()
  })
})


describe('已生成方案重新打开', () => {
  it('从服务端恢复文件引用，保存新正文后不显示旧文件', async () => {
    const plan = {
      taskId: '101', taskVersion: 7, planRevisionId: '501', revisionNo: 1, planVersion: 1,
      status: 'DRAFT', sourceSnapshot: null, approvalFact: null, allowedActions: ['SAVE_DRAFT'],
      content: { editMode: 'ONLINE_TEMPLATE_STANDARD', overview: { projectDescription: '验收', deviceSummary: [], scheduleTable: [] },
        riskMitigations: [], steps: [], supportArrangements: [{ roleCode: 'CUSTOMER', personName: '验收', dutyDescription: '', phone: '' }] },
      generatedDraft: { planRevisionId: '501', planVersion: 1, downloadedAt: 1792066155900,
        fileArtifactFact: { artifactId: '2110704555528138754', versionNo: 1, referenceKey: 'generated-sample' } } as object | null
    }
    api.getCutoverPlan.mockImplementation(async () => structuredClone(plan))
    api.saveCutoverPlanDraft.mockImplementation(async (command) => {
      plan.content = structuredClone(command.data)
      plan.planVersion++
      plan.generatedDraft = null
      return { planVersion: plan.planVersion }
    })
    const props = { taskId: '101', taskVersion: 7, manualGrade: 'C' }
    const first = mount(Panel, props, controls)
    await flush()
    expect(textOf(first.root)).toContain('2110704555528138754 V1')
    first.app.unmount()
    const reopened = mount(Panel, props, controls)
    await flush()
    expect(textOf(reopened.root)).toContain('2110704555528138754 V1')
    await (findByTestId(reopened.root, 'edit-partial-contact')!.props!.onClick as () => void)()
    await (findByTestId(reopened.root, 'save-plan')!.props!.onClick as () => Promise<void>)()
    await flush()
    expect(findByTestId(reopened.root, 'generated-file-reference')).toBeUndefined()
    reopened.app.unmount()
  })
})


describe('各方案模式的单个计划割接时间', () => {
  it.each(['ONLINE_TEMPLATE_STANDARD', 'ONLINE_TEMPLATE_SIMPLE_D', 'FULL_FILE_UPLOAD'])('在 %s 保存并重开保留明确填写的时间', async (editMode) => {
    const content = editMode === 'ONLINE_TEMPLATE_STANDARD'
      ? { editMode, overview: { projectDescription: '', deviceSummary: [], scheduleTable: [] }, steps: [], riskMitigations: [], supportArrangements: [] }
      : editMode === 'ONLINE_TEMPLATE_SIMPLE_D' ? { editMode, steps: [] }
        : { editMode, fileArtifactFact: { artifactId: '2110704555528138754', versionNo: 1 }, ownershipConfirmed: true }
    const plan = { taskId: '101', taskVersion: 7, planRevisionId: '501', revisionNo: 1, planVersion: 0,
      status: 'DRAFT', sourceSnapshot: null, approvalFact: null, allowedActions: ['SAVE_DRAFT'], content }
    api.getCutoverPlan.mockImplementation(async () => structuredClone(plan))
    api.saveCutoverPlanDraft.mockClear().mockImplementation(async (command) => {
      plan.content = structuredClone(command.data); plan.planVersion++; return { planVersion: plan.planVersion }
    })
    const props = { taskId: '101', taskVersion: 7, manualGrade: editMode === 'ONLINE_TEMPLATE_SIMPLE_D' ? 'D' : 'A' }
    const page = mount(Panel, props, controls); await flush()
    const control = findByTestId(page.root, 'plan-scheduled-time')!
    expect(control.props!['model-value']).toBeUndefined()
    await (control.props!['onUpdate:modelValue'] as Function)('1793498400000')
    await (findByTestId(page.root, 'save-plan')!.props!.onClick as () => Promise<void>)(); await flush()
    expect(api.saveCutoverPlanDraft.mock.calls[0][0].data).toMatchObject({ editMode, scheduledTime: 1793498400000 })
    page.app.unmount()
    const reopened = mount(Panel, props, controls); await flush()
    expect(findByTestId(reopened.root, 'plan-scheduled-time')!.props!['model-value']).toBe(1793498400000)
    reopened.app.unmount()
  })
})
