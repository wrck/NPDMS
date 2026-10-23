import { defineComponent, h, KeepAlive, nextTick, reactive, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CutoverTaskWorkbench from './index.vue'
import { getProject } from '@/api/pms/project/projects'
const navigation = vi.hoisted(() => ({ route: undefined as any, push: vi.fn(), replace: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => navigation.route, useRouter: () => navigation }))
vi.mock('../task-create/index.vue', () => ({ default: defineComponent({
  emits: ['created', 'close'],
  setup: (_, { emit }) => () => h('div', [
    h('button', { 'data-testid': 'finish-create', onClick: () => emit('created', '701') }, '保存成功'),
    h('button', { 'data-testid': 'cancel-create', onClick: () => emit('close') }, '返回任务列表')
  ])
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
  getSupportPersonChoices: vi.fn(async () => []),
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

  it('does not refresh a disposed creation page after navigation mounted a new workbench', async () => {
    navigation.route.query.create = '1'
    api.getCutoverTaskPage.mockResolvedValue({ list: [], total: 0 })
    const view = mount(CutoverTaskWorkbench, {}, controls); await flush()
    navigation.push.mockImplementation(async () => {
      view.app.unmount()
      delete navigation.route.query.create
      await nextTick()
    })
    await (findByTestId(view.root, 'cancel-create')!.props!.onClick as Function)(); await flush()
    expect(api.getCutoverTaskPage).not.toHaveBeenCalled()
    expect(api.getCutoverDashboardKpis).not.toHaveBeenCalled()
  })

  it('refreshes the retained workbench when closing its inline creation form', async () => {
    navigation.route.query.create = '1'
    api.getCutoverTaskPage.mockResolvedValue({ list: [], total: 0 })
    const view = mount(CutoverTaskWorkbench, {}, controls); await flush()
    navigation.push.mockImplementation(async () => { delete navigation.route.query.create; await nextTick() })
    await (findByTestId(view.root, 'cancel-create')!.props!.onClick as Function)(); await flush()
    expect(api.getCutoverTaskPage).toHaveBeenCalledTimes(2)
    expect(api.getCutoverDashboardKpis).toHaveBeenCalledTimes(1)
    view.app.unmount()
  })

  it('does not refresh the inactive cached creation page after navigation', async () => {
    navigation.route.query.create = '1'
    api.getCutoverTaskPage.mockResolvedValue({ list: [], total: 0 })
    const visible = ref(true)
    const host = defineComponent({ setup: () => () => h(KeepAlive, () => visible.value ? h(CutoverTaskWorkbench) : h('div')) })
    const view = mount(host, {}, controls); await flush()
    navigation.push.mockImplementation(async () => {
      visible.value = false
      delete navigation.route.query.create
      await nextTick()
    })
    await (findByTestId(view.root, 'cancel-create')!.props!.onClick as Function)(); await flush()
    expect(api.getCutoverTaskPage).not.toHaveBeenCalled()
    expect(api.getCutoverDashboardKpis).not.toHaveBeenCalled()
    view.app.unmount()
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

  it('shows the self-created list while todos are still loading and does not fetch its unused manager details', async () => {
    let resolveTodos!: (result: any) => void
    api.getCutoverTaskPage.mockImplementation(query => query.listKind === 'TODO'
      ? new Promise(resolve => { resolveTodos = resolve })
      : Promise.resolve({ list: [listRow('101', 'self-project', '先显示自建任务')], total: 1 }))
    const view = mount(CutoverTaskWorkbench, {}, controls)
    try {
      await flush()
      expect(textOf(view.root)).toContain('先显示自建任务')
      expect(getProject).not.toHaveBeenCalled()
      resolveTodos({ list: [], total: 0 }); await flush()
    } finally { view.app.unmount() }
  })

  it('shows todo rows before auxiliary manager details and only resolves managers used by that list', async () => {
    let resolveProject!: (project: any) => void
    vi.mocked(getProject).mockImplementationOnce(() => new Promise(resolve => { resolveProject = resolve }))
    api.getCutoverTaskPage.mockImplementation(query => Promise.resolve({ list: [listRow(query.listKind, query.listKind === 'TODO' ? '1' : '2', query.listKind === 'TODO' ? '可立即办理' : '自建任务')], total: 1 }))
    const view = mount(CutoverTaskWorkbench, {}, controls)
    try {
      await flush()
      expect(textOf(view.root)).toContain('可立即办理')
      expect(findByTestId(view.root, 'refresh-workbench')?.props?.loading).toBe(false)
      expect(getProject).toHaveBeenCalledExactlyOnceWith('1')
      resolveProject({ id: '1', managerName: '负责人' }); await flush()
      expect(textOf(view.root)).toContain('负责人')
    } finally { view.app.unmount() }
  })

  it('preserves the successful list when the other list fails', async () => {
    api.getCutoverTaskPage.mockImplementation(query => query.listKind === 'TODO'
      ? Promise.reject(new Error('todo unavailable'))
      : Promise.resolve({ list: [listRow('101', '2', '仍可打开的自建任务')], total: 1 }))
    const view = mount(CutoverTaskWorkbench, {}, controls)
    try {
      await flush()
      expect(textOf(view.root)).toContain('割接任务加载失败：待办列表')
      expect(textOf(view.root)).toContain('仍可打开的自建任务')
    } finally { view.app.unmount() }
  })
})

const listRow = (id: string, projectId: string, projectName: string) => ({ id, projectId, projectName,
  taskName: projectName, currentStage: 'P3', taskStatus: 'SURVEYING', intakeSourceType: 'SELF_CREATED', generatedAt: 1788310800000 })

const kpis = (todoCount: string, archivedCount: string, approvingCount: string,
  rejectedPendingModificationCount: string, generatedAt: number) => ({
  todoCount,
  archivedCount,
  approvingCount,
  rejectedPendingModificationCount,
  generatedAt
})

const flush = async () => {
  await vi.dynamicImportSettled()
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}
