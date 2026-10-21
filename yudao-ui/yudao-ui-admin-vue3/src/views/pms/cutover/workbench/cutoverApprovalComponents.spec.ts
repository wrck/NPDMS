import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { checkPermi } from '@/utils/permission'
import type {
  CutoverApprovalDetail,
  CutoverApprovalReassignmentView,
  ManualGrade
} from '@/api/pms/cutover'
import CutoverApprovalPanel from '../components/CutoverApprovalPanel.vue'
import CutoverApprovalDecisionForm from '../components/CutoverApprovalDecisionForm.vue'
import CutoverApprovalReassignmentPanel from '../components/CutoverApprovalReassignmentPanel.vue'
import CutoverTaskWorkbench from './index.vue'
import { createCutoverWriteCoordinator } from '../shared/cutoverTaskInteraction'
import {
  button,
  findByTestId,
  mount,
  passthrough,
  tableColumn,
  textOf
} from '../cutoverTestHarness'

const api = vi.hoisted(() => ({
  getCutoverApproval: vi.fn(),
  getCutoverApprovalInstance: vi.fn(),
  approveCutoverApproval: vi.fn(),
  rejectCutoverApproval: vi.fn(),
  reassignCutoverApproval: vi.fn(),
  getCutoverApprovalReassignmentCandidates: vi.fn(),
  getCutoverServiceManagerReassignmentCandidate: vi.fn(),
  getCutoverApprovalTodos: vi.fn(),
  getCutoverDashboardKpis: vi.fn(),
  getCutoverTaskPage: vi.fn(),
  getCutoverTaskDetail: vi.fn(),
  getCutoverChecklistMatchingOptions: vi.fn(),
  getCutoverChecklist: vi.fn()
}))
const people = vi.hoisted(() => ({ getSimpleUserList: vi.fn() }))
vi.mock('@/api/system/user', () => people)
vi.mock('@/api/pms/cutover', () => api)
const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => router, useRoute: () => ({ query: {} }) }))
vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({ success: vi.fn(), warning: vi.fn() })
}))
vi.mock('../components/CutoverCreateWizard.vue', () => ({ default: { render: () => null } }))
vi.mock('../components/CutoverAssessmentPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('../components/CutoverChecklistPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('../components/CutoverClosurePanel.vue', () => ({ default: { render: () => null } }))
vi.mock('../components/CutoverPlanPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('../components/CutoverWorkbenchSteps.vue', () => ({ default: { render: () => null } }))
vi.mock('../components/CutoverPlanEditor.vue', () => ({
  default: defineComponent({
    setup: () => () => h('section', { 'data-testid': 'frozen-plan' }, '冻结方案正文')
  })
}))

const controls = {
  ElDialog: passthrough,
  ElDescriptions: passthrough,
  ElDescriptionsItem: passthrough,
  ElResult: passthrough,
  ElTable: passthrough,
  ElTableColumn: tableColumn,
  ElForm: passthrough,
  ElFormItem: passthrough,
  ElRadioGroup: passthrough,
  ElRadioButton: button,
  ElInput: passthrough,
  ElSelect: passthrough,
  ElOption: passthrough,
  ElInputNumber: passthrough
}
const actionableTableColumn = defineComponent({
  inheritAttrs: false,
  setup(_, { attrs, slots }) {
    return () =>
      h(
        'span',
        attrs.label === '操作'
          ? slots.default?.({ row: { id: '9007199254740993', taskId: '9007199254740993' } })
          : undefined
      )
  }
})

describe('F-CUT-005 mounted approval workbench', () => {
  beforeEach(() => {
    people.getSimpleUserList.mockReset().mockResolvedValue([
      { id: '8', nickname: '原审批人' }, { id: '44', nickname: '新审批人' },
      { id: '9007199254740995', nickname: '长编号审批人' }
    ])
    vi.mocked(checkPermi).mockReturnValue(true)
    router.push.mockReset()
    Object.values(api).forEach((mock) => mock.mockReset())
    api.getCutoverServiceManagerReassignmentCandidate.mockResolvedValue(null)
    api.getCutoverChecklistMatchingOptions.mockResolvedValue({ cutoverTypes: [], networkModes: [], deviceTypes: [], matchingInput: { deviceTypeCodes: [] } })
    api.getCutoverChecklist.mockResolvedValue({ taskId: '9007199254740993', checklistId: '301', items: [] })
    api.getCutoverDashboardKpis.mockResolvedValue({
      todoCount: 0,
      archivedCount: 0,
      approvingCount: 0,
      rejectedPendingModificationCount: 0,
      generatedAt: 1788314400000
    })
  })

  it.each(['SECOND_LINE', 'RND'] as const)('opens %s shared approval directly without a separate claim step', async (nodeCode) => {
    const detail = full('B')
    Object.assign(detail.nodes[0], { nodeCode, originalApproverUserId: null, currentApproverUserId: null,
      candidateRoleCode: nodeCode === 'SECOND_LINE' ? 'probSupporter' : 'probRD' })
    api.getCutoverApprovalInstance.mockResolvedValue(detail)
    api.approveCutoverApproval.mockResolvedValue(detail)
    const page = mount(CutoverApprovalPanel, { approvalId: '701' }, controls)
    await flush()
    expect(findByTestId(page.root, 'approve-approval')).toBeDefined()
    expect(findByTestId(page.root, 'reject-approval')).toBeDefined()
    expect(findByTestId(page.root, 'claim-approval')).toBeUndefined()
    await update(page.root, 'approval-feedback', 'direct role decision')
    await click(page.root, 'approve-approval')
    await flush()
    expect(api.approveCutoverApproval).toHaveBeenCalledTimes(1)
    expect(api.approveCutoverApproval).toHaveBeenCalledWith(expect.objectContaining({
      approvalId: '701', approvalVersion: 4, taskVersion: 9,
      data: expect.objectContaining({ reviewItems: expect.arrayContaining([
        expect.objectContaining({ itemCode: 'PREPARATION', decision: 'YES' }),
        expect.objectContaining({ itemCode: 'BUSINESS_TEST', decision: 'YES' }),
        expect.objectContaining({ itemCode: 'EXECUTION', decision: 'YES' }),
        expect.objectContaining({ itemCode: 'ROLLBACK', decision: 'YES' }),
        expect.objectContaining({ itemCode: 'OTHER', decision: 'YES' })
      ]) })
    }))
    page.app.unmount()
  })

  it('opens existing reassignment form from full approval and submits through CUT', async () => {
    const detail = full('B')
    detail.nodes.push({ ...detail.nodes[0], nodeId: '712', nodeNo: 2, nodeCode: 'SECOND_LINE',
      status: 'WAITING', candidateRoleCode: 'probSupporter', originalApproverUserId: null, currentApproverUserId: null })
    const changed = structuredClone(detail)
    changed.approvalVersion = 5
    changed.nodes[0].currentApproverUserId = '44'
    api.getCutoverApprovalInstance.mockResolvedValueOnce(detail).mockResolvedValue(changed)
    api.reassignCutoverApproval.mockResolvedValue(reassignment())
    const page = mount(CutoverApprovalPanel, { approvalId: '701' }, controls)
    await flush()
    await click(page.root, 'open-cutover-reassignment')
    expect(textOf(page.root)).toContain('审批改派')
    await update(page.root, 'new-approver-id', '44')
    await update(page.root, 'reassignment-reason', '人员变更')
    await click(page.root, 'submit-reassignment')
    await flush()
    expect(api.reassignCutoverApproval).toHaveBeenCalledWith({ approvalId: '701', approvalVersion: 4,
      data: { nodeNo: 1, newApproverUserId: '44', reason: '人员变更' }, idempotencyKey: expect.any(String) })
    expect(api.approveCutoverApproval).not.toHaveBeenCalled()
    expect(api.rejectCutoverApproval).not.toHaveBeenCalled()
    await vi.waitFor(() => expect(findByTestId(page.root, 'submit-reassignment')).toBeUndefined())
    page.app.unmount()
  })

  it('does not expose reassignment without permission or for an unassigned shared role', async () => {
    const detail = full('B')
    api.getCutoverApprovalInstance.mockResolvedValue(detail)
    vi.mocked(checkPermi).mockReturnValue(false)
    const denied = mount(CutoverApprovalPanel, { approvalId: '701' }, controls)
    await flush()
    expect(findByTestId(denied.root, 'open-cutover-reassignment')).toBeUndefined()
    denied.app.unmount()
    vi.mocked(checkPermi).mockReturnValue(true)
    Object.assign(detail.nodes[0], { nodeCode: 'SECOND_LINE', candidateRoleCode: 'probSupporter',
      originalApproverUserId: null, currentApproverUserId: null })
    const shared = mount(CutoverApprovalPanel, { approvalId: '701' }, controls)
    await flush()
    expect(findByTestId(shared.root, 'open-cutover-reassignment')).toBeUndefined()
    expect(findByTestId(shared.root, 'approve-approval')).toBeDefined()
    expect(api.reassignCutoverApproval).not.toHaveBeenCalled()
    shared.app.unmount()
  })

  it.each(['A', 'B', 'C', 'D'] as ManualGrade[])(
    'renders the %s route from the frozen FULL projection',
    async (grade) => {
      api.getCutoverApprovalInstance.mockResolvedValue(full(grade))
      const mounted = mount(CutoverApprovalPanel, { approvalId: '701' }, controls)
      await flush()

      expect(textOf(mounted.root)).toContain('割接评审')
      expect(textOf(mounted.root)).toContain(grade)
      expect(textOf(mounted.root)).toContain('核心割接项目')
      expect(textOf(mounted.root)).toContain('未配置')
      expect(textOf(mounted.root)).toContain('高')
      expect(textOf(mounted.root)).toContain('GOLD')
      if (grade !== 'D') {
        expect(textOf(mounted.root)).toContain('核心链路风险')
        expect(textOf(mounted.root)).toContain('已核验')
        expect(textOf(mounted.root)).toContain('业务回归结果')
      }
      expect(findByTestId(mounted.root, 'frozen-plan')).toBeDefined()
      mounted.app.unmount()
    }
  )

  it('opens the complete task in readonly mode from the authorized approval', async () => {
    api.getCutoverApprovalInstance.mockResolvedValue(full('B'))
    const page = mount(CutoverApprovalPanel, { approvalId: '701' }, controls)
    await flush()
    await click(page.root, 'view-complete-cutover-task')
    expect(router.push).toHaveBeenCalledWith({ path: '/pms/cutover/tasks/9007199254740993', query: { readonly: '1' } })
    expect(api.approveCutoverApproval).not.toHaveBeenCalled()
    page.app.unmount()
  })

  it.each([false, true])('shows authorized participant names when the user picker is restricted or unavailable (%s)', async (unavailable) => {
    const detail = full('D')
    detail.nodes[0].currentApproverUserId = '2108539871029019609'
    detail.nodes[0].currentApproverName = '杨凯添'
    if (unavailable) people.getSimpleUserList.mockRejectedValueOnce(new Error('unavailable'))
    else people.getSimpleUserList.mockResolvedValueOnce([{ id: '2108539871029019646', nickname: '周文成' }])
    api.getCutoverApprovalInstance.mockResolvedValue(detail)
    const page = mount(CutoverApprovalPanel, { approvalId: '701' }, controls)
    await flush()
    expect(textOf(page.root)).toContain('杨凯添')
    expect(textOf(page.root)).not.toContain('用户 2108539871029019609')
    page.app.unmount()
  })

  it('shows the authorized current approver name in the reassignment summary', async () => {
    const view = reassignment()
    view.nodes[0].currentApproverUserId = '2108539871029019609'
    view.nodes[0].currentApproverName = '杨凯添'
    people.getSimpleUserList.mockResolvedValueOnce([])
    api.getCutoverApprovalInstance.mockResolvedValue(view)
    const page = mount(CutoverApprovalPanel, { approvalId: '701' }, controls)
    await flush()
    expect(textOf(page.root)).toContain('杨凯添')
    expect(textOf(page.root)).not.toContain('用户 2108539871029019609')
    expect(findByTestId(page.root, 'approve-approval')).toBeUndefined()
    page.app.unmount()
  })

  it('renders original item descriptions with frozen answers instead of the latest checklist results', async () => {
    const detail = full('B')
    const item = detail.sourceSnapshot.riskItems[0]
    item.answerSnapshot = JSON.stringify({ value: { majorCutover: false, spareReference: '11' } })
    api.getCutoverApprovalInstance.mockResolvedValue(detail)
    detail.sourcePresentation = { matchingOptions: null, incomplete: false, answerSchemas: {
      [String(item.checklistItemId)]: { fields: [
        { code: 'majorCutover', label: '本次是否为重大割接项目' },
        { code: 'spareReference', label: '备件外部申请引用或人工核验说明' }
      ] }
    } }
    const page = mount(CutoverApprovalPanel, { approvalId: '701' }, controls)
    await flush()
    await flush()
    expect(api.getCutoverChecklist).not.toHaveBeenCalled()
    expect(textOf(page.root)).toContain('本次是否为重大割接项目')
    expect(textOf(page.root)).toContain('备件外部申请引用或人工核验说明')
    expect(textOf(page.root)).toContain('11')
    expect(textOf(page.root)).not.toContain('majorCutover')
    expect(textOf(page.root)).not.toContain('禁止使用当前结果')
    page.app.unmount()
  })

  it.each(['missing', 'incomplete'])('retains frozen facts when source presentation is %s', async (failure) => {
    const detail = full('B')
    detail.sourceSnapshot.riskItems[0].answerSnapshot = '{"value":{"unmapped":"审批原始事实"}}'
    if (failure === 'incomplete') detail.sourcePresentation = { matchingOptions: null, answerSchemas: {}, incomplete: true }
    api.getCutoverApprovalInstance.mockResolvedValue(detail)
    const page = mount(CutoverApprovalPanel, { approvalId: '701' }, controls)
    await flush()
    expect(textOf(page.root)).toContain('审批原始事实')
    expect(textOf(page.root)).toContain('字段描述暂时无法读取')
    expect(api.getCutoverChecklist).not.toHaveBeenCalled()
    expect(api.getCutoverChecklistMatchingOptions).not.toHaveBeenCalled()
    page.app.unmount()
  })

  it('renders labels and selected device types from the exact checklist referenced by the approval', async () => {
    const detail = full('B')
    detail.sourceSnapshot.collectionAnalysis.cutoverType = 'VERSION_UPGRADE'
    detail.sourceSnapshot.collectionAnalysis.networkMode = 'SILENT_DUAL'
    api.getCutoverApprovalInstance.mockResolvedValue(detail)
    detail.sourcePresentation = { incomplete: false, answerSchemas: {}, matchingOptions: {
      configurationRevisionId: '401', configurationRevisionNo: 1,
      cutoverTypes: [{ value: 'VERSION_UPGRADE', label: '版本升级' }],
      networkModes: [{ value: 'SILENT_DUAL', label: '静默双机' }],
      deviceTypes: [{ value: 'DPX_FRAME', label: 'DPX框式设备' }],
      matchingInput: { cutoverType: 'VERSION_UPGRADE', networkMode: 'SILENT_DUAL', deviceTypeCodes: ['DPX_FRAME'] }
    } }
    const original = structuredClone(detail)
    const mounted = mount(CutoverApprovalPanel, { approvalId: '701' }, controls)
    await flush()
    expect(textOf(mounted.root)).toContain('版本升级')
    expect(textOf(findByTestId(mounted.root, 'approval-cutover-type')!)).toBe('版本升级')
    expect(textOf(findByTestId(mounted.root, 'approval-network-mode')!)).toBe('静默双机')
    expect(textOf(findByTestId(mounted.root, 'approval-device-types')!)).toBe('DPX框式设备')
    expect(api.getCutoverChecklistMatchingOptions).not.toHaveBeenCalled()
    expect(detail).toEqual(original)
    expect(api.approveCutoverApproval).not.toHaveBeenCalled()
    mounted.app.unmount()
  })

  it('uses authorized approval presentation without ordinary task query permission', async () => {
    vi.mocked(checkPermi).mockReturnValue(false)
    const detail = full('B')
    detail.sourcePresentation = { matchingOptions: null, answerSchemas: {}, incomplete: false }
    api.getCutoverApprovalInstance.mockResolvedValue(detail)
    const page = mount(CutoverApprovalPanel, { approvalId: '701' }, controls)
    await flush()
    expect(api.getCutoverChecklist).not.toHaveBeenCalled()
    expect(api.getCutoverChecklistMatchingOptions).not.toHaveBeenCalled()
    expect(textOf(page.root)).not.toContain('字段描述暂时无法读取')
    expect(textOf(page.root)).toContain('已核验')
    page.app.unmount()
  })

  it('ignores old description responses after switching approval instances', async () => {
    let finishOld!: (value: unknown) => void
    const approval = (id: string, label: string) => {
      const detail = full('B')
      detail.approvalInstanceId = id
      detail.sourceSnapshot.riskItems[0].answerSnapshot = '{"value":{"sameCode":"冻结事实"}}'
      detail.sourcePresentation = { matchingOptions: null, incomplete: false, answerSchemas: {
        '3011': { fields: [{ code: 'sameCode', label }] }
      } }
      return detail
    }
    api.getCutoverApprovalInstance.mockImplementation(id => id === '701'
      ? new Promise(resolve => { finishOld = resolve }) : Promise.resolve(approval('702', '新审批字段描述')))
    const selected = ref('701')
    const page = mount(defineComponent({ setup: () => () => h(CutoverApprovalPanel, { approvalId: selected.value }) }), {}, controls)
    await flush()
    selected.value = '702'
    await flush()
    await flush()
    expect(textOf(page.root)).toContain('新审批字段描述')
    finishOld(approval('701', '旧审批字段描述'))
    await flush()
    expect(textOf(page.root)).toContain('新审批字段描述')
    expect(textOf(page.root)).not.toContain('旧审批字段描述')
    page.app.unmount()
  })

  it('keeps the frozen approval readable when checklist metadata cannot be loaded', async () => {
    const detail = full('B')
    detail.sourceSnapshot.collectionAnalysis.cutoverType = 'CUSTOM_TYPE'
    api.getCutoverApprovalInstance.mockResolvedValue(detail)
    api.getCutoverChecklistMatchingOptions.mockRejectedValue(new Error('metadata unavailable'))
    const mounted = mount(CutoverApprovalPanel, { approvalId: '701' }, controls)
    await flush()
    expect(textOf(findByTestId(mounted.root, 'approval-cutover-type')!)).toBe('CUSTOM_TYPE')
    expect(textOf(mounted.root)).toContain('本次审批引用的清单匹配信息暂时无法读取')
    expect(findByTestId(mounted.root, 'frozen-plan')).toBeDefined()
    expect(api.approveCutoverApproval).not.toHaveBeenCalled()
    mounted.app.unmount()
  })

  it('does not request a current checklist for the D-grade flow with no frozen checklist', async () => {
    api.getCutoverApprovalInstance.mockResolvedValue(full('D'))
    const mounted = mount(CutoverApprovalPanel, { approvalId: '701' }, controls)
    await flush()
    expect(api.getCutoverChecklistMatchingOptions).not.toHaveBeenCalled()
    expect(api.getCutoverChecklist).not.toHaveBeenCalled()
    expect(findByTestId(mounted.root, 'frozen-plan')).toBeDefined()
    mounted.app.unmount()
  })

  it('submits five review items and service-manager reassessment from allowedActions', async () => {
    const detail = full('A', 'SERVICE_MANAGER')
    api.getCutoverApprovalInstance.mockResolvedValue(detail)
    api.approveCutoverApproval.mockResolvedValue(detail)
    const mounted = mount(CutoverApprovalPanel, { approvalId: '701' }, controls)
    await flush()
    await update(mounted.root, 'approval-feedback', '复核通过')
    await click(mounted.root, 'approve-approval')
    await flush()

    expect(api.approveCutoverApproval).toHaveBeenCalledWith({
      approvalId: '701', approvalVersion: 4, taskVersion: 9,
      data: expect.objectContaining({
        action: 'APPROVE',
        feedback: '复核通过',
        reviewItems: expect.arrayContaining([
          expect.objectContaining({ itemCode: 'PREPARATION', decision: 'YES' })
        ]),
        assessmentReview: { decision: 'CONFIRMED', reason: null }
      }),
      idempotencyKey: expect.any(String)
    })
    expect(api.approveCutoverApproval.mock.calls[0]?.[0].data).not.toHaveProperty('leadTimeCompliance')
    mounted.app.unmount()
  })

  it.each([
    ['A', '迟交：是', '0 个自然日'],
    ['B', '迟交：否', '2 个自然日']
  ] as const)(
    'renders frozen lead-time compliance for grade %s',
    async (grade, status, actualDays) => {
      api.getCutoverApprovalInstance.mockResolvedValue(full(grade))
      const mounted = mount(CutoverApprovalPanel, { approvalId: '701' }, controls)
      await flush()

      expect(findByTestId(mounted.root, 'lead-time-card')).toBeDefined()
      expect(textOf(findByTestId(mounted.root, 'lead-time-status')!)).toContain(status)
      expect(textOf(mounted.root)).toContain('1 个自然日')
      expect(textOf(mounted.root)).toContain(actualDays)
      expect(textOf(findByTestId(mounted.root, 'lead-time-scheduled')!)).not.toBe('—')
      expect(textOf(findByTestId(mounted.root, 'lead-time-submitted')!)).not.toBe('—')
      mounted.app.unmount()
    }
  )

  it('does not reserve a lead-time placeholder when the frozen fact is not applicable', async () => {
    api.getCutoverApprovalInstance.mockResolvedValue(full('C'))
    const mounted = mount(CutoverApprovalPanel, { approvalId: '701' }, controls)
    await flush()

    expect(findByTestId(mounted.root, 'lead-time-card')).toBeUndefined()
    mounted.app.unmount()
  })

  it('submits a legal rejection only after a NO item has its reason', async () => {
    const emitted: unknown[] = []
    const mounted = mount(
      CutoverApprovalDecisionForm,
      {
        nodeCode: 'INITIATOR',
        allowedActions: ['APPROVE', 'REJECT'],
        busy: false,
        onDecide: (value: unknown) => emitted.push(value)
      },
      controls
    )
    await update(mounted.root, 'approval-feedback', '准备工作不完整')
    const preparation = findByTestId(mounted.root, 'review-reason-PREPARATION')
    expect(preparation).toBeUndefined()
    const group = findByTestId(mounted.root, 'review-decision-PREPARATION')
    expect(group).toBeDefined()
    await (group!.props?.['onUpdate:modelValue'] as (value: string) => void)('NO')
    await nextTick()
    await update(mounted.root, 'review-reason-PREPARATION', '缺少回退演练记录')
    await click(mounted.root, 'reject-approval')
    expect(emitted[0]).toMatchObject({ action: 'REJECT', feedback: '准备工作不完整' })
    expect((emitted[0] as any).reviewItems[0]).toEqual({
      itemCode: 'PREPARATION',
      decision: 'NO',
      unreasonableReason: '缺少回退演练记录'
    })
    mounted.app.unmount()
  })

  it('does not infer decision actions for final-result or reassignment projections', async () => {
    api.getCutoverApprovalInstance.mockResolvedValueOnce({
      viewMode: 'FINAL_RESULT_ONLY',
      approvalInstanceId: '701',
      taskId: '9007199254740993',
      planRevisionId: '501',
      grade: 'B',
      status: 'APPROVED',
      decisionAt: 1788220800000,
      rejectionReason: null,
      allowedActions: []
    })
    const terminal = mount(CutoverApprovalPanel, { approvalId: '701' }, controls)
    await flush()
    expect(textOf(terminal.root)).toContain('审批已通过')
    expect(findByTestId(terminal.root, 'approve-approval')).toBeUndefined()
    terminal.app.unmount()

    api.getCutoverApprovalInstance.mockResolvedValueOnce(reassignment())
    api.reassignCutoverApproval.mockResolvedValue(reassignment())
    const admin = mount(CutoverApprovalPanel, { approvalId: '701' }, controls)
    await flush()
    expect(textOf(admin.root)).toContain('审批状态')
    expect(textOf(admin.root)).toContain('核心割接')
    expect(findByTestId(admin.root, 'reassignment-reason')).toBeUndefined()
    expect(findByTestId(admin.root, 'approve-approval')).toBeUndefined()
    await click(admin.root, 'open-cutover-reassignment')
    await update(admin.root, 'new-approver-id', '9007199254740995')
    await update(admin.root, 'reassignment-reason', '当前审批人请假')
    await click(admin.root, 'submit-reassignment')
    await flush()
    expect(api.reassignCutoverApproval).toHaveBeenCalledWith({
      approvalId: '701', approvalVersion: 4,
      data: {
        nodeNo: 1,
        newApproverUserId: '9007199254740995',
        reason: '当前审批人请假'
      },
      idempotencyKey: expect.any(String)
    })
    expect(findByTestId(admin.root, 'approve-approval')).toBeUndefined()
    admin.app.unmount()
  })

  it('retries only refresh after a successful decision whose first refresh fails', async () => {
    const detail = full('A')
    api.getCutoverApprovalInstance
      .mockReset()
      .mockResolvedValueOnce(detail)
      .mockRejectedValueOnce(new Error('refresh failed'))
      .mockResolvedValue(detail)
    api.approveCutoverApproval.mockResolvedValue(detail)
    const mounted = mount(CutoverApprovalPanel, { approvalId: '701' }, controls)
    await flush()
    await update(mounted.root, 'approval-feedback', '同意')

    await click(mounted.root, 'approve-approval')
    await flush()
    expect(api.approveCutoverApproval).toHaveBeenCalledTimes(1)
    await click(mounted.root, 'retry-approval-refresh')
    await flush()
    expect(api.approveCutoverApproval).toHaveBeenCalledTimes(1)
    await update(mounted.root, 'approval-feedback', '再次核对')
    await click(mounted.root, 'approve-approval')
    await flush()
    await vi.waitFor(() => expect(api.approveCutoverApproval).toHaveBeenCalledTimes(2))
    mounted.app.unmount()
  })

  it('uses approval recovery actions and refreshes before a later write', async () => {
    const keys = ['intent-1', 'intent-2']
    const coordinator = createCutoverWriteCoordinator(() => keys.shift()!)
    const write = vi.fn().mockRejectedValueOnce({
      response: { data: { data: { recoveryAction: 'REFRESH_APPROVAL' } } }
    })
    const refresh = vi
      .fn()
      .mockRejectedValueOnce(new Error('refresh failed'))
      .mockResolvedValue(undefined)
    await expect(coordinator.run('DECIDE:701:APPROVE', write, refresh)).rejects.toBeDefined()
    const refreshOnly = await coordinator.run('DECIDE:701:APPROVE', write, refresh)
    expect(refreshOnly.writeCalled).toBe(false)
    expect(write).toHaveBeenCalledTimes(1)
    expect(refresh).toHaveBeenCalledTimes(2)
  })

  it('opens the current-user todo and administrator reassignment queues', async () => {
    api.getCutoverTaskPage.mockResolvedValue({ list: [], total: 0, pageNo: 1, pageSize: 20 })
    api.getCutoverApprovalInstance.mockResolvedValue(full('A'))
    api.getCutoverApprovalTodos.mockResolvedValue({
      list: [
        {
          approvalInstanceId: '701',
          approvalVersion: 4,
          taskId: '9007199254740993',
          projectId: '401',
          taskCode: 'CUT-001',
          taskName: '核心割接',
          grade: 'A',
          nodeNo: 1,
          nodeCode: 'INITIATOR',
          createdAt: 1788220800000
        }
      ],
      total: 1,
      pageNo: 1,
      pageSize: 10
    })
    api.getCutoverApprovalReassignmentCandidates.mockResolvedValue({
      list: [
        {
          ...reassignment().nodes[0],
          approvalInstanceId: '701',
          approvalVersion: 4,
          taskId: '9007199254740993',
          projectId: '401',
          taskCode: 'CUT-001',
          taskName: '核心割接',
          grade: 'A',
          status: 'PENDING',
          holdReason: 'APPROVER_UNAVAILABLE',
          createdAt: 1788220800000
        }
      ],
      total: 1,
      pageNo: 1,
      pageSize: 10
    })
    const mounted = mount(
      CutoverTaskWorkbench,
      {},
      {
        ...controls,
        ElTableColumn: actionableTableColumn,
        ElDialog: passthrough,
        ElSelect: passthrough,
        ElOption: passthrough,
        CutoverCreateWizard: passthrough,
        CutoverAssessmentPanel: passthrough,
        CutoverChecklistPanel: passthrough,
        CutoverPlanPanel: passthrough,
        CutoverWorkbenchSteps: passthrough
      }
    )
    await flush()
    await click(mounted.root, 'open-approval-todos')
    await flush()
    expect(api.getCutoverApprovalTodos).toHaveBeenCalledWith({ pageNo: 1, pageSize: 10 })
    await click(mounted.root, 'open-approval-from-todo')
    await flush()
    expect(router.push).toHaveBeenCalledWith('/pms/cutover/approvals/701')
    expect(api.getCutoverTaskDetail).not.toHaveBeenCalled()
    await click(mounted.root, 'open-reassignment-queue')
    await flush()
    expect(api.getCutoverApprovalReassignmentCandidates).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 10
    })
    mounted.app.unmount()
  })

  it.each(['FINAL_RESULT_ONLY', 'REASSIGNMENT_ONLY'])('loads %s through the approval instance boundary', async (viewMode) => {
    api.getCutoverApprovalInstance.mockResolvedValue(viewMode === 'FINAL_RESULT_ONLY' ? finalResult() : reassignment())
    const mounted = mount(CutoverApprovalPanel, { approvalId: '701' }, controls)
    await flush()
    expect(api.getCutoverApprovalInstance).toHaveBeenCalledWith('701')
    expect(api.getCutoverApproval).not.toHaveBeenCalled()
    expect(api.getCutoverTaskDetail).not.toHaveBeenCalled()
    expect(findByTestId(mounted.root, 'approve-approval')).toBeUndefined()
    expect(textOf(mounted.root)).toContain(viewMode === 'FINAL_RESULT_ONLY' ? '审批已通过' : '审批状态')
    mounted.app.unmount()
  })

  it('ignores a late response from the previous approval instance', async () => {
    let resolveOld!: (value: unknown) => void
    api.getCutoverApprovalInstance.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
      .mockResolvedValue({ ...finalResult(), approvalInstanceId: '702' })
    const id = ref('701')
    const host = defineComponent({ setup: () => () => h(CutoverApprovalPanel, { approvalId: id.value }) })
    const mounted = mount(host, {}, controls)
    await flush()
    id.value = '702'
    await flush()
    resolveOld(full('A'))
    await flush()
    expect(api.getCutoverApprovalInstance).toHaveBeenLastCalledWith('702')
    expect(textOf(mounted.root)).toContain('审批已通过')
    expect(findByTestId(mounted.root, 'approve-approval')).toBeUndefined()
    mounted.app.unmount()
  })

  it('clears decision state when the same component switches tasks', async () => {
    api.getCutoverApprovalInstance.mockResolvedValueOnce(full('A')).mockResolvedValueOnce({
      ...full('B'),
      approvalInstanceId: '702',
      taskId: '9007199254740994'
    })
    const host = defineComponent({
      setup(_, { expose }) {
        const approvalId = ref('701')
        expose({
          switchTask: () => {
            approvalId.value = '702'
          }
        })
        return () => h(CutoverApprovalPanel, { approvalId: approvalId.value })
      }
    })
    const mounted = mount(host, {}, controls)
    await flush()
    await update(mounted.root, 'approval-feedback', '上一任务意见')
    ;(mounted.vm as any).switchTask()
    await flush()
    expect(api.getCutoverApprovalInstance).toHaveBeenLastCalledWith('702')
    await vi.waitFor(() =>
      expect(findLatestByTestId(mounted.root, 'approval-feedback')?.props?.modelValue).toBe('')
    )
    mounted.app.unmount()
  })

  it('clears the decision form when approval advances to the next node', async () => {
    const first = full('A')
    const second: CutoverApprovalDetail = {
      ...full('A'),
      approvalVersion: 5,
      currentNodeNo: 2,
      nodes: [
        { ...first.nodes[0], status: 'APPROVED', decisionAt: 1788220800000 },
        { ...first.nodes[0], nodeId: '712', nodeNo: 2, nodeCode: 'SERVICE_MANAGER' }
      ]
    }
    api.getCutoverApprovalInstance.mockResolvedValueOnce(first).mockResolvedValueOnce(second)
    api.approveCutoverApproval.mockResolvedValue(second)
    const mounted = mount(CutoverApprovalPanel, { approvalId: first.approvalInstanceId }, controls)
    await flush()
    await update(mounted.root, 'approval-feedback', '第一节点意见')
    await click(mounted.root, 'approve-approval')
    await flush()
    expect(findByTestId(mounted.root, 'approval-feedback')?.props?.modelValue).toBe('')
    mounted.app.unmount()
  })

  it('clears reassignment input after the approval version changes', async () => {
    const first = reassignment()
    const second = { ...reassignment(), approvalVersion: 5 }
    api.getCutoverApprovalInstance.mockResolvedValueOnce(first).mockResolvedValueOnce(second)
    api.reassignCutoverApproval.mockResolvedValue(second)
    const mounted = mount(CutoverApprovalPanel, { approvalId: first.approvalInstanceId }, controls)
    await flush()
    await click(mounted.root, 'open-cutover-reassignment')
    await update(mounted.root, 'new-approver-id', '9007199254740995')
    await update(mounted.root, 'reassignment-reason', '当前审批人请假')
    await click(mounted.root, 'submit-reassignment')
    await flush()
    expect(findByTestId(mounted.root, 'reassignment-reason')).toBeUndefined()
    await click(mounted.root, 'open-cutover-reassignment')
    expect(findByTestId(mounted.root, 'new-approver-id')?.props?.modelValue).toBe('')
    expect(findByTestId(mounted.root, 'reassignment-reason')?.props?.modelValue).toBe('')
    mounted.app.unmount()
  })

  it('selects reassignment nodes and people by name and rejects invalid form values', async () => {
    const view = reassignment()
    const original = view.nodes[0].currentApproverUserId!
    const submitted = vi.fn()
    const mounted = mount(CutoverApprovalReassignmentPanel, {
      view, busy: false, userNames: { [String(original)]: '原审批人', '9007199254740995': '新审批人' }, onReassign: submitted
    }, controls)
    expect(findByTestId(mounted.root, 'reassignment-node')).toBeDefined()
    expect(textOf(mounted.root)).toContain('原审批人')
    expect(textOf(mounted.root)).not.toContain('当前审批人ID')
    expect(findByTestId(mounted.root, 'new-approver-id')?.props?.filterable).toBeDefined()
    expect(findByTestId(mounted.root, 'reassignment-reason')?.props?.type).toBe('textarea')
    await update(mounted.root, 'reassignment-reason', '人员变更')
    await update(mounted.root, 'new-approver-id', String(original))
    await click(mounted.root, 'submit-reassignment')
    expect(submitted).not.toHaveBeenCalled()
    await update(mounted.root, 'new-approver-id', '不存在的用户')
    await click(mounted.root, 'submit-reassignment')
    expect(submitted).not.toHaveBeenCalled()
    await update(mounted.root, 'new-approver-id', '9007199254740995')
    await update(mounted.root, 'reassignment-node', 99)
    await click(mounted.root, 'submit-reassignment')
    expect(submitted).not.toHaveBeenCalled()
    await update(mounted.root, 'reassignment-node', 1)
    await update(mounted.root, 'new-approver-id', '9007199254740995')
    await update(mounted.root, 'reassignment-reason', '  人员变更  ')
    await click(mounted.root, 'submit-reassignment')
    expect(submitted).toHaveBeenCalledWith({ nodeNo: 1, newApproverUserId: '9007199254740995', reason: '人员变更' })
    mounted.app.unmount()
  })

  it('keeps reassignment disabled when people could not load and retries the read', async () => {
    people.getSimpleUserList.mockRejectedValueOnce(new Error('load failed'))
    api.getCutoverApprovalInstance.mockResolvedValue(reassignment())
    const mounted = mount(CutoverApprovalPanel, { approvalId: '701' }, controls)
    await flush()
    await click(mounted.root, 'open-cutover-reassignment')
    expect(textOf(mounted.root)).toContain('人员信息加载失败')
    expect(findByTestId(mounted.root, 'submit-reassignment')?.props?.disabled).toBe(true)
    await click(mounted.root, 'retry-reassignment-users')
    await flush()
    expect(people.getSimpleUserList).toHaveBeenCalledTimes(2)
    expect(textOf(mounted.root)).not.toContain('人员信息加载失败')
    expect(api.reassignCutoverApproval).not.toHaveBeenCalled()
    mounted.app.unmount()
  })

  it('restricts service-manager reassignment to the current qualified person and rejects injected admin selection', async () => {
    api.getCutoverServiceManagerReassignmentCandidate.mockResolvedValue('44')
    const view = reassignment()
    view.nodes[0].nodeCode = 'SERVICE_MANAGER'
    const submitted = vi.fn()
    const mounted = mount(CutoverApprovalReassignmentPanel, {
      view, busy: false, userNames: { '1': '管理员', '8': '原审批人', '44': '当前服务经理' }, onReassign: submitted
    }, controls)
    await flush()
    expect(api.getCutoverServiceManagerReassignmentCandidate).toHaveBeenCalledWith(view.approvalInstanceId)
    expect(findByTestId(mounted.root, 'reassignment-assignee-1')).toBeUndefined()
    expect(findByTestId(mounted.root, 'reassignment-assignee-44')?.props?.label).toBe('当前服务经理')
    await update(mounted.root, 'new-approver-id', '1')
    await update(mounted.root, 'reassignment-reason', '人员调整')
    await click(mounted.root, 'submit-reassignment')
    expect(submitted).not.toHaveBeenCalled()
    await update(mounted.root, 'new-approver-id', '44')
    await click(mounted.root, 'submit-reassignment')
    expect(submitted).toHaveBeenCalledWith({ nodeNo: view.nodes[0].nodeNo, newApproverUserId: '44', reason: '人员调整' })
    mounted.app.unmount()
  })

  it('fails closed on service-manager lookup failure, retries it and never falls back to all users', async () => {
    api.getCutoverServiceManagerReassignmentCandidate.mockRejectedValueOnce(new Error('unavailable')).mockResolvedValue(null)
    const view = reassignment()
    view.nodes[0].nodeCode = 'SERVICE_MANAGER'
    const mounted = mount(CutoverApprovalReassignmentPanel, { view, busy: false, userNames: { '1': '管理员' } }, controls)
    await flush()
    expect(textOf(mounted.root)).toContain('服务经理候选读取失败')
    expect(textOf(mounted.root)).not.toContain('管理员')
    expect(findByTestId(mounted.root, 'submit-reassignment')?.props?.disabled).toBe(true)
    await click(mounted.root, 'retry-service-manager-candidate')
    await flush()
    expect(api.getCutoverServiceManagerReassignmentCandidate).toHaveBeenCalledTimes(2)
    expect(textOf(mounted.root)).not.toContain('服务经理候选读取失败')
    expect(findByTestId(mounted.root, 'new-approver-id')?.props?.placeholder).toBe('当前无可改派的服务经理')
    expect(findByTestId(mounted.root, 'submit-reassignment')?.props?.disabled).toBe(true)
    mounted.app.unmount()
  })

  it.each([320, 768, 1024, 1440])('mounts decision and reassignment controls at %ipx', (width) => {
    vi.stubGlobal('innerWidth', width)
    const decision = mount(
      CutoverApprovalDecisionForm,
      {
        nodeCode: 'INITIATOR',
        allowedActions: ['APPROVE', 'REJECT'],
        busy: false
      },
      controls
    )
    const reassign = mount(
      CutoverApprovalReassignmentPanel,
      { view: reassignment(), busy: false },
      controls
    )
    expect(findByTestId(decision.root, 'approve-approval')).toBeDefined()
    expect(findByTestId(reassign.root, 'submit-reassignment')).toBeDefined()
    decision.app.unmount()
    reassign.app.unmount()
  })
})

const full = (
  grade: ManualGrade,
  nodeCode: 'INITIATOR' | 'SERVICE_MANAGER' = 'INITIATOR'
): CutoverApprovalDetail => ({
  viewMode: 'FULL',
  approvalInstanceId: '701',
  approvalVersion: 4,
  taskId: '9007199254740993',
  taskVersion: 9,
  planRevisionId: '501',
  planRevisionNo: 2,
  grade,
  status: 'PENDING',
  holdReason: null,
  currentNodeNo: 1,
  nodes: [
    {
      nodeId: '711',
      nodeNo: 1,
      nodeCode,
      status: 'PENDING',
      originalApproverUserId: '8',
      currentApproverUserId: '8',
      decisionAt: null,
      feedback: null,
      reviewItems: [],
      assessmentReview: null
    }
  ],
  sourceSnapshot: {
    snapshotVersion: 1,
    taskId: '9007199254740993',
    taskVersion: 8,
    checklistId: grade === 'D' ? null : '301',
    checklistVersion: grade === 'D' ? null : 2,
    project: {
      projectId: '401',
      projectVersion: 3,
      projectCode: 'P-001',
      projectName: '核心割接项目',
      customerId: '501',
      customerCode: 'C-001',
      customerName: '客户',
      departmentId: '601',
      departmentCode: 'O1',
      departmentName: '办事处',
      projectScopeVersion: '7'
    },
    collectionAnalysis: { cutoverType: 'CORE', networkMode: null, scheduledTime: 1788220800000 },
    riskItems:
      grade === 'D'
        ? []
        : [frozenChecklistItem('RISK', 'RISK-001', '核心链路风险', '{"value":"已核验"}')],
    businessSurveyItems:
      grade === 'D'
        ? []
        : [
            frozenChecklistItem(
              'BUSINESS_SURVEY',
              'BUSINESS-001',
              '业务回归结果',
              '{"value":"通过"}'
            )
          ],
    assessment: {
      assessmentId: '201',
      assessmentVersion: 2,
      questionnaireTemplateCode: 'CUT_P2_MANUAL_ASSESSMENT',
      questionnaireTemplateVersion: '1',
      businessImportanceLevel: 'HIGH',
      operationComplexityLevel: 'HIGH',
      hiddenRiskLevel: 'LOW',
      sparePartApplied: false,
      customerServiceLevelCode: 'GOLD',
      manualGrade: grade,
      submittedBy: '8',
      submittedAt: 1788210000000
    },
    plan: {
      planRevisionId: '501',
      planRevisionNo: 2,
      planVersion: 1,
      originCode: 'NEW_PLATFORM',
      sourceSnapshot: {
        snapshotVersion: 1,
        taskId: '9007199254740993',
        taskVersion: 8,
        assessmentId: '201',
        assessmentVersion: 2,
        grade,
        checklistId: grade === 'D' ? null : '301',
        checklistVersion: grade === 'D' ? null : 2,
        projectId: '401',
        projectVersion: 3,
        projectScopeVersion: '7',
        devices: [],
        configurationRevisionId: '801',
        configurationCode: 'CORE',
        configurationRevisionNo: 1,
        templateSections: [],
        failedRiskFacts: []
      },
      content: {
        editMode: 'ONLINE_TEMPLATE_SIMPLE_D',
        steps: [
          { sectionCode: 'OPERATION', stepNo: 1, content: '执行割接' },
          { sectionCode: 'ROLLBACK', stepNo: 1, content: '执行回退' }
        ]
      }
    }
  },
  leadTimeCompliance:
    grade === 'A' || grade === 'B'
      ? {
          ruleVersion: 'CUT_LEAD_TIME_R034_V1',
          timezoneId: 'Asia/Shanghai',
          cutoverType: 'SIGNATURE_UPGRADE',
          scheduledTime: 1788220800000,
          planSubmittedAt: grade === 'A' ? 1788210000000 : 1788048000000,
          requiredDays: 1,
          actualNaturalDays: grade === 'A' ? 0 : 2,
          lateSubmission: grade === 'A'
        }
      : null,
  decisionAt: null,
  rejectionReason: null,
  allowedActions: ['APPROVE', 'REJECT']
})

const reassignment = (): CutoverApprovalReassignmentView => ({
  viewMode: 'REASSIGNMENT_ONLY',
  approvalInstanceId: '701',
  approvalVersion: 4,
  taskId: '9007199254740993',
  projectId: '401',
  taskCode: 'CUT-001',
  taskName: '核心割接',
  grade: 'A',
  status: 'PENDING',
  holdReason: 'APPROVER_UNAVAILABLE',
  nodes: [
    {
      nodeId: '711',
      nodeNo: 1,
      nodeCode: 'INITIATOR',
      nodeStatus: 'PENDING',
      currentApproverUserId: '8',
      nodeVersion: 2
    }
  ],
  allowedActions: ['REASSIGN']
})
const finalResult = () => ({
  viewMode: 'FINAL_RESULT_ONLY' as const,
  approvalInstanceId: '701',
  taskId: '9007199254740993',
  planRevisionId: '501',
  grade: 'A' as const,
  status: 'APPROVED' as const,
  decisionAt: 1788220800000,
  rejectionReason: null,
  allowedActions: [] as []
})
const frozenChecklistItem = (
  itemTypeCode: 'RISK' | 'BUSINESS_SURVEY',
  stableItemKey: string,
  itemName: string,
  answerSnapshot: string
) => ({
  checklistItemId: stableItemKey === 'RISK-001' ? '3011' : '3012',
  stableItemKey,
  itemDefinitionId: '4011',
  itemDefinitionVersion: 1,
  itemTypeCode,
  itemName,
  required: true,
  itemResultVersion: 1,
  resultSourceCode: 'DIRECT' as const,
  answerSnapshot,
  factDescription: `${itemName}冻结事实`,
  collectionTaskId: null,
  collectionResultReferenceId: null,
  collectionResultVersion: null,
  externalSourceCode: null,
  manualEvidenceFileReference: null
})
const click = async (root: any, id: string) => {
  const node = findByTestId(root, id)
  expect(node, id).toBeDefined()
  await (node!.props?.onClick as () => Promise<void>)()
}
const update = async (root: any, id: string, value: unknown) => {
  const node = findByTestId(root, id)
  expect(node, id).toBeDefined()
  await (node!.props?.['onUpdate:modelValue'] as (value: unknown) => void)(value)
  await nextTick()
}
const flush = async () => {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}
const findLatestByTestId = (root: any, id: string) => {
  const matches: any[] = []
  const visit = (node: any) => {
    if (node.props?.['data-testid'] === id) matches.push(node)
    node.children?.forEach(visit)
  }
  visit(root)
  return matches.at(-1)
}
