import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive } from 'vue'
import TaskDetail from './index.vue'
import { findByTestId, mount, textOf } from '../cutoverTestHarness'

const messages = vi.hoisted(() => ({ warning: vi.fn() }))
const api = vi.hoisted(() => ({ getCutoverTaskDetail: vi.fn(), saveCutoverAssessment: vi.fn(), submitCutoverAssessment: vi.fn() }))
vi.mock('@/api/pms/cutover', () => api)
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn(), warning: messages.warning }) }))
vi.mock('../components/CutoverAssessmentPanel.vue', () => ({ default: defineComponent({
  props: { model: { type: Object, required: true }, editable: Boolean, onSave: Function, onSubmit: Function }, setup(props) { return () => h('section', [
    h('span', props.model.manualGrade),
    h('button', { 'data-testid': 'save-assessment', disabled: !props.editable, onClick: props.onSave }, '保存'),
    h('button', { 'data-testid': 'submit-assessment', disabled: !props.editable, onClick: props.onSubmit }, '下一步')
  ]) }
}) }))
vi.mock('../components/CutoverProjectContextCard.vue', () => ({ default: { render: () => null } }))
vi.mock('../components/CutoverApprovalPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('../components/CutoverChecklistPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('../components/CutoverClosurePanel.vue', () => ({ default: { render: () => null } }))
vi.mock('../components/CutoverPlanPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('../components/CutoverWorkbenchSteps.vue', () => ({ default: { render: () => null } }))
const flush = async () => { for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() } }
const detail = (id = '101', grade = 'A') => ({
  task: { id, taskNo: `CUT-${id}`, taskName: `割接${id}`, projectId: '70', currentStage: 'P2', taskStatus: 'GRADE_CONFIRMING', version: 7 },
  assessment: { rowVersion: 3, manualGrade: grade, answers: { businessImportanceLevel: 'HIGH', operationComplexityLevel: 'LOW', hiddenRiskLevel: 'LOW', sparePartApplied: false } },
  allowedActions: ['SAVE_ASSESSMENT', 'SUBMIT_ASSESSMENT'], workbenchSteps: [], devices: []
})

describe('P2 detail write recovery', () => {
  beforeEach(() => vi.resetAllMocks())
  it('retains unsaved assessment on failure and offers an explicit discard without another write', async () => {
    api.getCutoverTaskDetail.mockImplementation(async () => structuredClone(detail()))
    const failure = { response: { status: 409, data: { data: { recoveryAction: 'REFRESH_OWNER_FACTS' } } } }
    api.saveCutoverAssessment.mockRejectedValue(failure)
    const page = mount(TaskDetail, { taskId: '101' }); await flush()
    const state = (page.vm as any).$.setupState
    state.assessmentModel.manualGrade = 'D'
    await expect((findByTestId(page.root, 'save-assessment')!.props!.onClick as Function)()).rejects.toEqual(failure)
    expect(state.assessmentModel.manualGrade).toBe('D')
    expect(api.getCutoverTaskDetail).toHaveBeenCalledTimes(2)
    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)
    const leaving = (findByTestId(page.root, 'close-cutover-detail')!.props!.onClick as Function)()
    await flush()
    expect(state.leaveDialogVisible).toBe(true)
    state.resolveLeaveDecision('stay')
    await leaving
    expect(state.assessmentModel.manualGrade).toBe('D')
    expect(api.submitCutoverAssessment).not.toHaveBeenCalled()
    const discard = (page.vm as any).saveBeforeLeave()
    await flush()
    const writes = api.saveCutoverAssessment.mock.calls.length
    state.resolveLeaveDecision('discard')
    expect(await discard).toBe(true)
    expect(state.assessmentModel.manualGrade).toBe('A')
    expect(await (page.vm as any).saveBeforeLeave()).toBe(true)
    expect(api.saveCutoverAssessment).toHaveBeenCalledTimes(writes)
    page.app.unmount()
  })
  it('retries an uncertain assessment save with exactly the original key, versions and content', async () => {
    api.getCutoverTaskDetail.mockResolvedValue(detail())
    api.saveCutoverAssessment.mockRejectedValueOnce(new Error('response lost')).mockResolvedValue({})
    const page = mount(TaskDetail, { taskId: '101' })
    await flush()
    const save = findByTestId(page.root, 'save-assessment')!.props!.onClick as () => Promise<void>
    await expect(save()).rejects.toThrow('response lost')
    await save()
    expect(api.saveCutoverAssessment).toHaveBeenCalledTimes(2)
    expect(api.saveCutoverAssessment.mock.calls[1][0]).toEqual(api.saveCutoverAssessment.mock.calls[0][0])
    expect(api.saveCutoverAssessment.mock.calls[0][0]).toMatchObject({ taskId: '101', taskVersion: 7, assessmentVersion: 3, idempotencyKey: expect.any(String) })
    expect(api.submitCutoverAssessment).not.toHaveBeenCalled()
    page.app.unmount()
  })
  it('blocks a second write while the accepted first write cannot be refreshed', async () => {
    api.getCutoverTaskDetail.mockResolvedValueOnce(detail()).mockRejectedValue(new Error('refresh failed'))
    api.saveCutoverAssessment.mockResolvedValue({})
    const page = mount(TaskDetail, { taskId: '101' })
    await flush()
    const save = findByTestId(page.root, 'save-assessment')!.props!.onClick as () => Promise<void>
    await save(); await flush()
    expect(findByTestId(page.root, 'save-assessment')!.props!.disabled).toBe(true)
    await save()
    expect(api.saveCutoverAssessment).toHaveBeenCalledOnce()
    expect(textOf(page.root)).toContain('详情加载失败')
    page.app.unmount()
  })
  it('does not blame completed questions or submit when server eligibility is missing', async () => {
    api.getCutoverTaskDetail.mockResolvedValue({ ...detail(), allowedActions: ['SAVE_ASSESSMENT'],
      customerServiceLevel: { referenceStatus: 'NOT_CONFIGURED', serviceLevelCode: null } })
    const page = mount(TaskDetail, { taskId: '101' })
    await flush()
    await (findByTestId(page.root, 'submit-assessment')!.props!.onClick as () => Promise<void>)()
    expect(messages.warning).toHaveBeenCalledWith('评估信息已保存，但当前来源信息、实施条件或操作权限不满足提交要求，请刷新任务并核查。')
    expect(api.submitCutoverAssessment).not.toHaveBeenCalled()
    expect(api.saveCutoverAssessment).not.toHaveBeenCalled()
    page.app.unmount()
  })
  it('ignores a late detail response after the task identity changes', async () => {
    let resolve!: (value: unknown) => void
    api.getCutoverTaskDetail.mockImplementationOnce(() => new Promise(done => { resolve = done })).mockResolvedValue(detail('102', 'D'))
    const input = reactive({ taskId: '101' })
    const Host = defineComponent({ setup() { return () => h(TaskDetail, input) } })
    const page = mount(Host)
    input.taskId = '102'
    await flush()
    resolve(detail('101'))
    await flush()
    expect(textOf(page.root)).toContain('割接102')
    expect(textOf(page.root)).not.toContain('割接101')
    page.app.unmount()
  })
  it('shows the server reason without issuing a submission when original devices moved', async () => {
    const reason = '原选设备已缺失、替换或不属于当前项目，请核查设备归属后重试'
    api.getCutoverTaskDetail.mockResolvedValue({ ...detail(), allowedActions: ['SAVE_ASSESSMENT'], assessmentSubmissionBlockReason: reason })
    const page = mount(TaskDetail, { taskId: '101' })
    await flush()
    await (findByTestId(page.root, 'submit-assessment')!.props!.onClick as () => Promise<void>)()
    expect(messages.warning).toHaveBeenCalledWith(reason)
    expect(api.submitCutoverAssessment).not.toHaveBeenCalled()
    page.app.unmount()
  })
  it('saves an explicit manual customer level in the existing assessment command before submission', async () => {
    const level = { status: 'NOT_CONFIGURED', serviceLevelCode: null, sourceRecordId: null, factVersion: 1 }
    const loaded = { ...detail(), customerServiceLevel: level, assessment: { ...detail().assessment, customerServiceLevel: level, manualCustomerServiceLevelCode: null } }
    api.getCutoverTaskDetail.mockResolvedValue(loaded)
    api.saveCutoverAssessment.mockResolvedValue({})
    const page = mount(TaskDetail, { taskId: '101' }); await flush()
    ;(page.vm as any).$.setupState.assessmentModel.manualCustomerServiceLevelCode = 'GOLD'
    await (findByTestId(page.root, 'save-assessment')!.props!.onClick as Function)()
    expect(api.saveCutoverAssessment).toHaveBeenCalledWith(expect.objectContaining({ data: expect.objectContaining({ manualCustomerServiceLevelCode: 'GOLD' }) }))
    expect(api.submitCutoverAssessment).not.toHaveBeenCalled()
    page.app.unmount()
  })
  it('requires a manual choice when the current customer level is unconfigured', async () => {
    api.getCutoverTaskDetail.mockResolvedValue({ ...detail(), customerServiceLevel: { status: 'NOT_CONFIGURED', serviceLevelCode: null } })
    const page = mount(TaskDetail, { taskId: '101' }); await flush()
    await (findByTestId(page.root, 'submit-assessment')!.props!.onClick as Function)()
    expect(messages.warning).toHaveBeenCalledWith('请选择客户服务等级并保存评估。')
    expect(api.submitCutoverAssessment).not.toHaveBeenCalled()
    page.app.unmount()
  })
  it('allows a legacy unavailable draft to save a manual level using the now-unconfigured source', async () => {
    api.getCutoverTaskDetail.mockResolvedValue({ ...detail(), customerServiceLevel: { status: 'NOT_CONFIGURED', serviceLevelCode: null },
      assessment: { ...detail().assessment, status: 'DRAFT', customerServiceLevel: { status: 'UNAVAILABLE', serviceLevelCode: null } } })
    api.saveCutoverAssessment.mockResolvedValue({})
    const page = mount(TaskDetail, { taskId: '101' }); await flush()
    const state = (page.vm as any).$.setupState
    expect(state.assessmentCustomerLevel.status).toBe('NOT_CONFIGURED')
    state.assessmentModel.manualCustomerServiceLevelCode = 'GOLD'
    await (findByTestId(page.root, 'save-assessment')!.props!.onClick as Function)()
    expect(api.saveCutoverAssessment).toHaveBeenCalledWith(expect.objectContaining({ data: expect.objectContaining({ manualCustomerServiceLevelCode: 'GOLD' }) }))
    expect(messages.warning).not.toHaveBeenCalled()
    page.app.unmount()
  })

})
