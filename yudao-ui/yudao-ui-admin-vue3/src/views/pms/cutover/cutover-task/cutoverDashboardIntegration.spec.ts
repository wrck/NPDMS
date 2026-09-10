import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CutoverTaskWorkbench from './index.vue'
import {
  findByTestId,
  mount,
  passthrough,
  tableColumn,
  textOf
} from '../../platform/dynamic-form/components/runtimeTestHarness'

const api = vi.hoisted(() => ({
  resolveCreateContext: vi.fn(),
  createCutoverTask: vi.fn(),
  getCutoverDashboardKpis: vi.fn(),
  getCutoverTaskPage: vi.fn(),
  getCutoverTaskDetail: vi.fn(),
  getCutoverApprovalTodos: vi.fn(),
  getCutoverApprovalReassignmentCandidates: vi.fn(),
  saveCutoverAssessment: vi.fn(),
  submitCutoverAssessment: vi.fn(),
  getCutoverChecklist: vi.fn(),
  generateCutoverChecklist: vi.fn(),
  saveCutoverChecklist: vi.fn(),
  saveManualChecklistResult: vi.fn(),
  addCustomChecklistItem: vi.fn(),
  removeCustomChecklistItem: vi.fn(),
  requestChecklistCollection: vi.fn(),
  submitCutoverChecklist: vi.fn(),
  getCutoverPlan: vi.fn(),
  createCutoverPlanDraft: vi.fn(),
  saveCutoverPlanDraft: vi.fn(),
  downloadCutoverPlanDraft: vi.fn(),
  submitCutoverPlan: vi.fn(),
  patchApprovedCutoverPlanContact: vi.fn(),
  reviseCutoverPlan: vi.fn(),
  getCutoverApproval: vi.fn(),
  approveCutoverApproval: vi.fn(),
  rejectCutoverApproval: vi.fn(),
  reassignCutoverApproval: vi.fn(),
  getCutoverClosure: vi.fn(),
  saveCutoverClosure: vi.fn(),
  requestCutoverClosureCollection: vi.fn(),
  linkCutoverClosureManualResult: vi.fn(),
  submitCutoverClosure: vi.fn()
}))

vi.mock('@/api/pms/cutover/cutover-task', () => api)
vi.mock('@/api/pms/platform/file', () => ({
  getArtifact: vi.fn(),
  getVersions: vi.fn(),
  createAccessTicket: vi.fn()
}))
vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({ success: vi.fn(), warning: vi.fn() })
}))
vi.mock('@/components/PmsFileArtifact', () => ({
  PmsFileUploader: defineComponent({
    setup: () => () => h('button', 'upload')
  })
}))

const controls = {
  ElInput: passthrough,
  ElSelect: passthrough,
  ElOption: passthrough,
  ElTable: passthrough,
  ElTableColumn: tableColumn,
  ElDialog: passthrough,
  ElDescriptions: passthrough,
  ElDescriptionsItem: passthrough,
  ElSteps: passthrough,
  ElStep: passthrough,
  ElResult: passthrough
}

describe('CUT dashboard production page integration', () => {
  beforeEach(() => vi.clearAllMocks())

  it('loads the main task list first and queries KPI facts only after explicit expansion', async () => {
    api.getCutoverTaskPage.mockResolvedValue({ list: [], total: 0, pageNo: 1, pageSize: 20 })
    api.getCutoverDashboardKpis
      .mockResolvedValueOnce(kpis('3', '1', '1', '2', 1788310800000))
      .mockResolvedValueOnce(kpis('0', '4', '0', '0', 1788310860000))

    const mounted = mount(CutoverTaskWorkbench, {}, controls)
    await flush()

    expect(api.getCutoverTaskPage).toHaveBeenCalledTimes(1)
    expect(api.getCutoverDashboardKpis).not.toHaveBeenCalled()
    const toggle = findByTestId(mounted.root, 'toggle-kpis')
    await (toggle!.props?.onClick as () => Promise<void>)()
    await flush()
    expect(api.getCutoverDashboardKpis).toHaveBeenCalledTimes(1)
    expect(textOf(mounted.root)).toContain('待办3')
    expect(textOf(mounted.root)).toContain('已归档1')
    expect(textOf(mounted.root)).toContain('审批中1')
    expect(textOf(mounted.root)).toContain('驳回待修改2')

    const refresh = findByTestId(mounted.root, 'refresh-workbench')
    expect(refresh).toBeDefined()
    await (refresh!.props?.onClick as () => Promise<void>)()
    await flush()

    expect(api.getCutoverTaskPage).toHaveBeenCalledTimes(2)
    expect(api.getCutoverDashboardKpis).toHaveBeenCalledTimes(2)
    expect(textOf(mounted.root)).toContain('待办0')
    expect(textOf(mounted.root)).toContain('已归档4')
    mounted.app.unmount()
  })

  it('does not hold task-list refresh behind a slow or failed auxiliary KPI request', async () => {
    api.getCutoverTaskPage.mockResolvedValue({ list: [], total: 0, pageNo: 1, pageSize: 20 })
    let rejectKpi!: (reason: Error) => void
    api.getCutoverDashboardKpis.mockImplementation(() => new Promise((_resolve, reject) => { rejectKpi = reject }))
    const mounted = mount(CutoverTaskWorkbench, {}, controls)
    await flush()
    const toggle = findByTestId(mounted.root, 'toggle-kpis')
    const pending = (toggle!.props?.onClick as () => Promise<void>)()
    await flush()
    const refresh = findByTestId(mounted.root, 'refresh-workbench')
    await (refresh!.props?.onClick as () => Promise<void>)()
    await flush()
    expect(api.getCutoverTaskPage).toHaveBeenCalledTimes(2)
    expect(api.getCutoverDashboardKpis).toHaveBeenCalledTimes(1)
    expect(findByTestId(mounted.root, 'refresh-workbench')?.props?.loading).toBe(false)
    rejectKpi(new Error('KPI provider unavailable'))
    await pending
    await flush()
    expect(textOf(mounted.root)).toContain('割接任务概览加载失败')
    mounted.app.unmount()
  })

  it('distinguishes an unavailable task service from an empty task list', async () => {
    api.getCutoverTaskPage.mockRejectedValueOnce(new Error('endpoint not assembled'))
    const mounted = mount(CutoverTaskWorkbench, {}, controls)
    await flush()
    expect(textOf(mounted.root)).toContain('任务列表未成功刷新')
    expect(findByTestId(mounted.root, 'create-cutover-task')?.props?.disabled).toBe(true)
    expect(api.getCutoverDashboardKpis).not.toHaveBeenCalled()
    mounted.app.unmount()
  })
})

const kpis = (todoCount: string, archivedCount: string, approvingCount: string,
  rejectedPendingModificationCount: string, generatedAt: number) => ({
  todoCount,
  archivedCount,
  approvingCount,
  rejectedPendingModificationCount,
  generatedAt
})

const flush = async () => {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}
