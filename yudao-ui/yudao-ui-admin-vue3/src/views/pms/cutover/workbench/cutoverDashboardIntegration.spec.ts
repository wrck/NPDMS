import { defineComponent, h, nextTick, reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CutoverTaskWorkbench from './index.vue'
const navigation = vi.hoisted(() => ({ route: undefined as any, push: vi.fn(), replace: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => navigation.route, useRouter: () => navigation }))
vi.mock('../task-create/index.vue', () => ({ default: defineComponent({
  emits: ['created', 'close'],
  setup: (_, { emit }) => () => h('button', { 'data-testid': 'finish-create', onClick: () => emit('created', '701') }, '保存成功')
}) }))
import {
  findByTestId,
  mount,
  passthrough,
  tableColumn,
  textOf
} from '../cutoverTestHarness'

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
  getCutoverChecklistMatchingOptions: vi.fn(async () => ({ matchingInput: { cutoverType: 'CORE_REPLACEMENT', networkMode: null, deviceTypeCodes: [] }, deviceTypes: [] })),
  getCutoverExternalState: vi.fn(async () => null),
  getCutoverChecklist: vi.fn(),
  getCutoverChecklistRevisions: vi.fn(async () => []),
  generateCutoverChecklist: vi.fn(),
  saveCutoverChecklist: vi.fn(),
  saveManualChecklistResult: vi.fn(),
  addCustomChecklistItem: vi.fn(),
  removeCustomChecklistItem: vi.fn(),
  requestChecklistCollection: vi.fn(),
  submitCutoverChecklist: vi.fn(),
  getProjectContactChoices: vi.fn(async () => []),
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

vi.mock('@/api/pms/cutover', () => api)
vi.mock('@/api/pms/platform/file/version-reference', () => ({
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
  beforeEach(() => {
    vi.clearAllMocks()
    navigation.route = reactive({ path: '/pms/cutover/workbench', query: { category: 'todo' } as Record<string, string> })
    navigation.push.mockResolvedValue(undefined); navigation.replace.mockResolvedValue(undefined)
    api.getCutoverDashboardKpis.mockReset().mockResolvedValue(kpis('0', '0', '0', '0', 1788310800000))
    api.getCutoverTaskPage.mockReset()
  })

  it('opens creation within the current list route and retains its query', async () => {
    api.getCutoverTaskPage.mockResolvedValue({ list: [], total: 0 })
    const view = mount(CutoverTaskWorkbench, {}, controls); await flush()
    await (findByTestId(view.root, 'create-cutover-task')!.props!.onClick as Function)()
    expect(navigation.push).toHaveBeenCalledWith({ path: '/pms/cutover/workbench', query: { category: 'todo', create: '1' } })
    view.app.unmount()
  })

  it('removes creation from list history and opens the saved task detail', async () => {
    navigation.route.query.create = '1'
    const view = mount(CutoverTaskWorkbench, {}, controls); await flush()
    await (findByTestId(view.root, 'finish-create')!.props!.onClick as Function)(); await flush()
    expect(navigation.replace).toHaveBeenCalledWith({ path: '/pms/cutover/workbench', query: { category: 'todo' } })
    expect(navigation.push).toHaveBeenCalledWith('/pms/cutover/tasks/701')
    expect(navigation.replace.mock.invocationCallOrder[0]).toBeLessThan(navigation.push.mock.invocationCallOrder[0])
    view.app.unmount()
  })

  it('loads both workbench lists and KPI facts, then refreshes them together', async () => {
    api.getCutoverTaskPage.mockResolvedValue({ list: [], total: 0, pageNo: 1, pageSize: 20 })
    api.getCutoverDashboardKpis
      .mockResolvedValueOnce(kpis('3', '1', '1', '2', 1788310800000))
      .mockResolvedValueOnce(kpis('0', '4', '0', '0', 1788310860000))

    const mounted = mount(CutoverTaskWorkbench, {}, controls)
    await flush()

    expect(api.getCutoverTaskPage).toHaveBeenCalledTimes(2)
    expect(api.getCutoverTaskPage).toHaveBeenCalledWith(expect.objectContaining({ listKind: 'TODO' }))
    expect(api.getCutoverTaskPage).toHaveBeenCalledWith(expect.objectContaining({ listKind: 'SELF_CREATED' }))
    expect(api.getCutoverDashboardKpis).toHaveBeenCalledTimes(1)
    expect(textOf(mounted.root)).toContain('3待办割接任务')
    expect(textOf(mounted.root)).toContain('1已完成割接')
    expect(textOf(mounted.root)).toContain('1审批中')
    expect(textOf(mounted.root)).toContain('2驳回待修改')

    const refresh = findByTestId(mounted.root, 'refresh-workbench')
    expect(refresh).toBeDefined()
    await (refresh!.props?.onClick as () => Promise<void>)()
    await flush()

    expect(api.getCutoverTaskPage).toHaveBeenCalledTimes(4)
    expect(api.getCutoverDashboardKpis).toHaveBeenCalledTimes(2)
    expect(textOf(mounted.root)).toContain('0待办割接任务')
    expect(textOf(mounted.root)).toContain('4已完成割接')
    mounted.app.unmount()
  })

  it('does not hold task-list refresh behind a slow or failed auxiliary KPI request', async () => {
    api.getCutoverTaskPage.mockResolvedValue({ list: [], total: 0, pageNo: 1, pageSize: 20 })
    let rejectKpi!: (reason: Error) => void
    api.getCutoverDashboardKpis.mockImplementation(() => new Promise((_resolve, reject) => { rejectKpi = reject }))
    const mounted = mount(CutoverTaskWorkbench, {}, controls)
    await flush()
    const refresh = findByTestId(mounted.root, 'refresh-workbench')
    await (refresh!.props?.onClick as () => Promise<void>)()
    await flush()
    expect(api.getCutoverTaskPage).toHaveBeenCalledTimes(4)
    expect(api.getCutoverDashboardKpis).toHaveBeenCalledTimes(1)
    expect(findByTestId(mounted.root, 'refresh-workbench')?.props?.loading).toBe(false)
    rejectKpi(new Error('KPI provider unavailable'))
    await flush()
    expect(textOf(mounted.root)).toContain('割接任务概览加载失败')
    mounted.app.unmount()
  })

  it('distinguishes an unavailable task service from an empty task list', async () => {
    api.getCutoverTaskPage.mockRejectedValueOnce(new Error('endpoint not assembled'))
    const mounted = mount(CutoverTaskWorkbench, {}, controls)
    await flush()
    expect(textOf(mounted.root)).toContain('割接任务加载失败')
    expect(api.getCutoverDashboardKpis).toHaveBeenCalledTimes(1)
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
