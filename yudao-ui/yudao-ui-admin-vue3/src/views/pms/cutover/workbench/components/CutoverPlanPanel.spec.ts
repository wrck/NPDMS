import { defineComponent, h, nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { findByTestId, mount, passthrough, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Panel from './CutoverPlanPanel.vue'

const api = vi.hoisted(() => ({ getCutoverPlan: vi.fn(), saveCutoverPlanDraft: vi.fn() }))
vi.mock('@/api/pms/cutover/workbench', () => api)
vi.mock('@/api/pms/platform/file/version-reference', () => ({}))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn(), warning: vi.fn() }) }))
vi.mock('@/components/PmsFileArtifact', () => ({ PmsFileUploader: defineComponent({ render: () => null }) }))
vi.mock('@/components/PmsFileArtifact/PmsFileVersionReferenceList.vue', () => ({ default: defineComponent({ render: () => null }) }))
vi.mock('./CutoverPlanEditor.vue', () => ({ default: defineComponent({
  props: ['modelValue'], emits: ['update:modelValue'],
  setup(props, { emit }) { return () => h('section', [
    h('button', { 'data-testid': 'edit-partial-contact', onClick: () => emit('update:modelValue', {
      ...props.modelValue, supportArrangements: props.modelValue.supportArrangements.map((row: { roleCode: string }, index: number) =>
        index === 0 ? { ...row, dutyDescription: '先保存待补电话的客户安排' } : row)
    }) }, '填写客户职责'),
    h('span', props.modelValue.supportArrangements.find((row: { roleCode: string }) => row.roleCode === 'CUSTOMER')?.dutyDescription)
  ]) }
}) }))

const flush = async () => { for (let i = 0; i < 12; i++) { await Promise.resolve(); await nextTick() } }
const controls = { ElDescriptions: passthrough, ElDescriptionsItem: passthrough,
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
    api.saveCutoverPlanDraft.mockImplementation(async (_id, _taskVersion, _planVersion, content) => {
      plan.content = structuredClone(content)
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
    expect(api.saveCutoverPlanDraft.mock.calls[0][3].supportArrangements).toEqual(expect.arrayContaining([
      expect.objectContaining({ roleCode: 'CUSTOMER', personName: '项目样例联系人',
        dutyDescription: '先保存待补电话的客户安排', phone: '', arrivalTime: 0 })
    ]))
    expect(api.saveCutoverPlanDraft.mock.calls[0][3].supportArrangements).toHaveLength(2)
    expect(api.saveCutoverPlanDraft.mock.calls[0][3].supportArrangements[1]).toMatchObject({
      arrangementId: '502', personName: '第二位项目联系人', dutyDescription: '另一项保障', customerContactReference: { contactId: '602', contactVersion: 1 }
    })
    page.app.unmount()
    const reopened = mount(Panel, props, controls)
    await flush()
    expect(textOf(reopened.root)).toContain('先保存待补电话的客户安排')
    reopened.app.unmount()
  })
})
