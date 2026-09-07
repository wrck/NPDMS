import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  CutoverApprovalDetail,
  CutoverApprovalReassignmentView,
  ManualGrade
} from '@/api/pms/cutover/cutover-task'
import CutoverApprovalPanel from './components/CutoverApprovalPanel.vue'
import CutoverApprovalDecisionForm from './components/CutoverApprovalDecisionForm.vue'
import CutoverApprovalReassignmentPanel from './components/CutoverApprovalReassignmentPanel.vue'
import CutoverTaskWorkbench from './index.vue'
import { createCutoverApprovalWriteCoordinator } from './cutoverTaskInteraction'
import {
  button,
  findByTestId,
  mount,
  passthrough,
  tableColumn,
  textOf
} from '../../platform/dynamic-form/components/runtimeTestHarness'

const api = vi.hoisted(() => ({
  getCutoverApproval: vi.fn(),
  approveCutoverApproval: vi.fn(),
  rejectCutoverApproval: vi.fn(),
  reassignCutoverApproval: vi.fn(),
  getCutoverApprovalReassignmentCandidates: vi.fn(),
  getCutoverApprovalTodos: vi.fn(),
  getCutoverDashboardKpis: vi.fn(),
  getCutoverTaskPage: vi.fn(),
  getCutoverTaskDetail: vi.fn()
}))
vi.mock('@/api/pms/cutover/cutover-task', () => api)
vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({ success: vi.fn(), warning: vi.fn() })
}))
vi.mock('./components/CutoverCreateWizard.vue', () => ({ default: { render: () => null } }))
vi.mock('./components/CutoverAssessmentPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('./components/CutoverChecklistPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('./components/CutoverClosurePanel.vue', () => ({ default: { render: () => null } }))
vi.mock('./components/CutoverPlanPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('./components/CutoverWorkbenchSteps.vue', () => ({ default: { render: () => null } }))
vi.mock('./components/CutoverPlanEditor.vue', () => ({
  default: defineComponent({
    setup: () => () => h('section', { 'data-testid': 'frozen-plan' }, '冻结方案正文')
  })
}))

const controls = {
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
    Object.values(api).forEach((mock) => mock.mockReset())
    api.getCutoverDashboardKpis.mockResolvedValue({
      todoCount: 0,
      archivedCount: 0,
      approvingCount: 0,
      rejectedPendingModificationCount: 0,
      generatedAt: 1788314400000
    })
  })

  it.each(['A', 'B', 'C', 'D'] as ManualGrade[])(
    'renders the %s route from the frozen FULL projection',
    async (grade) => {
      api.getCutoverApproval.mockResolvedValue(full(grade))
      const mounted = mount(CutoverApprovalPanel, { taskId: '9007199254740993' }, controls)
      await flush()

      expect(textOf(mounted.root)).toContain('P5 分级审批')
      expect(textOf(mounted.root)).toContain(grade)
      expect(textOf(mounted.root)).toContain('核心割接项目')
      expect(textOf(mounted.root)).toContain('未配置')
      expect(textOf(mounted.root)).toContain('HIGH')
      expect(textOf(mounted.root)).toContain('GOLD')
      if (grade !== 'D') {
        expect(textOf(mounted.root)).toContain('核心链路风险')
        expect(textOf(mounted.root)).toContain('{"value":"已核验"}')
        expect(textOf(mounted.root)).toContain('业务回归结果')
      }
      expect(findByTestId(mounted.root, 'frozen-plan')).toBeDefined()
      mounted.app.unmount()
    }
  )

  it('submits five review items and service-manager reassessment from allowedActions', async () => {
    const detail = full('A', 'SERVICE_MANAGER')
    api.getCutoverApproval.mockResolvedValue(detail)
    api.approveCutoverApproval.mockResolvedValue(detail)
    const mounted = mount(CutoverApprovalPanel, { taskId: '9007199254740993' }, controls)
    await flush()
    await update(mounted.root, 'approval-feedback', '复核通过')
    await click(mounted.root, 'approve-approval')
    await flush()

    expect(api.approveCutoverApproval).toHaveBeenCalledWith(
      '9007199254740993',
      4,
      9,
      expect.objectContaining({
        action: 'APPROVE',
        feedback: '复核通过',
        reviewItems: expect.arrayContaining([
          expect.objectContaining({ itemCode: 'PREPARATION', decision: 'YES' })
        ]),
        assessmentReview: { decision: 'CONFIRMED', reason: null }
      }),
      expect.any(String)
    )
    expect(api.approveCutoverApproval.mock.calls[0]?.[3]).not.toHaveProperty('leadTimeCompliance')
    mounted.app.unmount()
  })

  it.each([
    ['A', '迟交：是', '0 个自然日'],
    ['B', '迟交：否', '2 个自然日']
  ] as const)(
    'renders frozen lead-time compliance for grade %s',
    async (grade, status, actualDays) => {
      api.getCutoverApproval.mockResolvedValue(full(grade))
      const mounted = mount(CutoverApprovalPanel, { taskId: '9007199254740993' }, controls)
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
    api.getCutoverApproval.mockResolvedValue(full('C'))
    const mounted = mount(CutoverApprovalPanel, { taskId: '9007199254740993' }, controls)
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
    api.getCutoverApproval.mockResolvedValueOnce({
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
    const terminal = mount(CutoverApprovalPanel, { taskId: '9007199254740993' }, controls)
    await flush()
    expect(textOf(terminal.root)).toContain('审批已通过')
    expect(findByTestId(terminal.root, 'approve-approval')).toBeUndefined()
    terminal.app.unmount()

    api.getCutoverApproval.mockResolvedValueOnce(reassignment())
    api.reassignCutoverApproval.mockResolvedValue(reassignment())
    const admin = mount(CutoverApprovalPanel, { taskId: '9007199254740993' }, controls)
    await flush()
    await update(admin.root, 'new-approver-id', '9007199254740995')
    await update(admin.root, 'reassignment-reason', '当前审批人请假')
    await click(admin.root, 'submit-reassignment')
    await flush()
    expect(api.reassignCutoverApproval).toHaveBeenCalledWith(
      '9007199254740993',
      4,
      {
        nodeNo: 1,
        newApproverUserId: '9007199254740995',
        reason: '当前审批人请假'
      },
      expect.any(String)
    )
    expect(findByTestId(admin.root, 'approve-approval')).toBeUndefined()
    admin.app.unmount()
  })

  it('retries only refresh after a successful decision whose first refresh fails', async () => {
    const detail = full('A')
    api.getCutoverApproval
      .mockReset()
      .mockResolvedValueOnce(detail)
      .mockRejectedValueOnce(new Error('refresh failed'))
      .mockResolvedValue(detail)
    api.approveCutoverApproval.mockResolvedValue(detail)
    const mounted = mount(CutoverApprovalPanel, { taskId: '9007199254740993' }, controls)
    await flush()
    await update(mounted.root, 'approval-feedback', '同意')

    await click(mounted.root, 'approve-approval')
    await flush()
    expect(api.approveCutoverApproval).toHaveBeenCalledTimes(1)
    await click(mounted.root, 'approve-approval')
    await flush()
    expect(api.approveCutoverApproval).toHaveBeenCalledTimes(1)
    await click(mounted.root, 'approve-approval')
    await flush()
    await vi.waitFor(() => expect(api.approveCutoverApproval).toHaveBeenCalledTimes(2))
    mounted.app.unmount()
  })

  it('uses approval recovery actions and refreshes before a later write', async () => {
    const keys = ['intent-1', 'intent-2']
    const coordinator = createCutoverApprovalWriteCoordinator(() => keys.shift()!)
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
    api.getCutoverApproval.mockResolvedValue(full('A'))
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
    expect(api.getCutoverApproval).toHaveBeenCalledWith('9007199254740993')
    expect(api.getCutoverTaskDetail).not.toHaveBeenCalled()
    await click(mounted.root, 'open-reassignment-queue')
    await flush()
    expect(api.getCutoverApprovalReassignmentCandidates).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 10
    })
    mounted.app.unmount()
  })

  it.each([
    ['open-approval-from-task', 'FINAL_RESULT_ONLY'],
    ['open-approval-from-reassignment', 'REASSIGNMENT_ONLY']
  ])('opens %s through the approval-only route as %s', async (testId, viewMode) => {
    api.getCutoverTaskPage.mockResolvedValue({ list: [], total: 0, pageNo: 1, pageSize: 20 })
    api.getCutoverApproval.mockResolvedValue(
      viewMode === 'FINAL_RESULT_ONLY' ? finalResult() : reassignment()
    )
    api.getCutoverApprovalReassignmentCandidates.mockResolvedValue({
      list: [],
      total: 0,
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
    if (viewMode === 'REASSIGNMENT_ONLY') {
      await click(mounted.root, 'open-reassignment-queue')
      await flush()
    }
    await click(mounted.root, testId)
    await flush()
    expect(api.getCutoverApproval).toHaveBeenCalledWith('9007199254740993')
    expect(api.getCutoverTaskDetail).not.toHaveBeenCalled()
    expect(textOf(mounted.root)).toContain(
      viewMode === 'FINAL_RESULT_ONLY' ? '审批已通过' : '审批改派'
    )
    mounted.app.unmount()
  })

  it('clears decision state when the same component switches tasks', async () => {
    api.getCutoverApproval.mockResolvedValueOnce(full('A')).mockResolvedValueOnce({
      ...full('B'),
      approvalInstanceId: '702',
      taskId: '9007199254740994'
    })
    const host = defineComponent({
      setup(_, { expose }) {
        const taskId = ref('9007199254740993')
        expose({
          switchTask: () => {
            taskId.value = '9007199254740994'
          }
        })
        return () => h(CutoverApprovalPanel, { taskId: taskId.value })
      }
    })
    const mounted = mount(host, {}, controls)
    await flush()
    await update(mounted.root, 'approval-feedback', '上一任务意见')
    ;(mounted.vm as any).switchTask()
    await flush()
    expect(api.getCutoverApproval).toHaveBeenLastCalledWith('9007199254740994')
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
    api.getCutoverApproval.mockResolvedValueOnce(first).mockResolvedValueOnce(second)
    api.approveCutoverApproval.mockResolvedValue(second)
    const mounted = mount(CutoverApprovalPanel, { taskId: first.taskId }, controls)
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
    api.getCutoverApproval.mockResolvedValueOnce(first).mockResolvedValueOnce(second)
    api.reassignCutoverApproval.mockResolvedValue(second)
    const mounted = mount(CutoverApprovalPanel, { taskId: first.taskId }, controls)
    await flush()
    await update(mounted.root, 'new-approver-id', '9007199254740995')
    await update(mounted.root, 'reassignment-reason', '当前审批人请假')
    await click(mounted.root, 'submit-reassignment')
    await flush()
    expect(findByTestId(mounted.root, 'new-approver-id')?.props?.modelValue).toBe('')
    expect(findByTestId(mounted.root, 'reassignment-reason')?.props?.modelValue).toBe('')
    mounted.app.unmount()
  })

  it.each([
    ['open-approval-from-task', 'FINAL_RESULT_ONLY'],
    ['open-approval-from-reassignment', 'REASSIGNMENT_ONLY']
  ])('opens %s through the approval-only route as %s', async (testId, viewMode) => {
    api.getCutoverTaskPage.mockResolvedValue({ list: [], total: 0, pageNo: 1, pageSize: 20 })
    api.getCutoverApproval.mockResolvedValue(
      viewMode === 'FINAL_RESULT_ONLY' ? finalResult() : reassignment()
    )
    api.getCutoverApprovalReassignmentCandidates.mockResolvedValue({
      list: [],
      total: 0,
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
    if (viewMode === 'REASSIGNMENT_ONLY') {
      await click(mounted.root, 'open-reassignment-queue')
      await flush()
    }
    await click(mounted.root, testId)
    await flush()
    expect(api.getCutoverApproval).toHaveBeenCalledWith('9007199254740993')
    expect(api.getCutoverTaskDetail).not.toHaveBeenCalled()
    expect(textOf(mounted.root)).toContain(
      viewMode === 'FINAL_RESULT_ONLY' ? '审批已通过' : '审批改派'
    )
    mounted.app.unmount()
  })

  it('clears decision state when the same component switches tasks', async () => {
    api.getCutoverApproval.mockResolvedValueOnce(full('A')).mockResolvedValueOnce({
      ...full('B'),
      approvalInstanceId: '702',
      taskId: '9007199254740994'
    })
    const host = defineComponent({
      setup(_, { expose }) {
        const taskId = ref('9007199254740993')
        expose({
          switchTask: () => {
            taskId.value = '9007199254740994'
          }
        })
        return () => h(CutoverApprovalPanel, { taskId: taskId.value })
      }
    })
    const mounted = mount(host, {}, controls)
    await flush()
    await update(mounted.root, 'approval-feedback', '上一任务意见')
    ;(mounted.vm as any).switchTask()
    await flush()
    expect(api.getCutoverApproval).toHaveBeenLastCalledWith('9007199254740994')
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
    api.getCutoverApproval.mockResolvedValueOnce(first).mockResolvedValueOnce(second)
    api.approveCutoverApproval.mockResolvedValue(second)
    const mounted = mount(CutoverApprovalPanel, { taskId: first.taskId }, controls)
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
    api.getCutoverApproval.mockResolvedValueOnce(first).mockResolvedValueOnce(second)
    api.reassignCutoverApproval.mockResolvedValue(second)
    const mounted = mount(CutoverApprovalPanel, { taskId: first.taskId }, controls)
    await flush()
    await update(mounted.root, 'new-approver-id', '9007199254740995')
    await update(mounted.root, 'reassignment-reason', '当前审批人请假')
    await click(mounted.root, 'submit-reassignment')
    await flush()
    expect(findByTestId(mounted.root, 'new-approver-id')?.props?.modelValue).toBe('')
    expect(findByTestId(mounted.root, 'reassignment-reason')?.props?.modelValue).toBe('')
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
      officeDepartmentId: '601',
      officeCode: 'O-001',
      officeName: '办事处',
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
