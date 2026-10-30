import { beforeEach, expect, it, vi } from 'vitest'
import Page from './index.vue'
import { mount, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const api = vi.hoisted(() => ({ project: vi.fn(), instances: vi.fn(), contacts: vi.fn(), risk: vi.fn(), navigation: vi.fn(), refresh: vi.fn(), route: { query: { projectId: '84577' } } }))
vi.mock('@/api/pms/project/projects', () => ({ getProject: api.project, getProjectInstances: api.instances }))
vi.mock('@/api/pms/customer/contacts', () => ({ getProjectPage: api.contacts }))
vi.mock('@/api/pms/project/project-risk', () => ({ getProjectRiskListByProjectId: api.risk }))
vi.mock('@/api/pms/commerce/projectOrderEquipment', () => ({ loadProjectOrderEquipmentList: vi.fn() }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true, checkRole: () => false }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getDictLabel: () => '' }))
vi.mock('@/utils/formatTime', () => ({ formatDate: () => '' }))
vi.mock('@/utils/officeProjectReadonly', () => ({ officeProjectReadContext: () => undefined }))
vi.mock('vue-router', () => ({ useRoute: () => api.route, useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@vueuse/core', async () => { const { ref } = await import('vue'); return { useMediaQuery: () => ref(false) } })
vi.mock('./components/businessModules', () => ({ businessFlowGroups: [], businessNavItems: [], businessModuleConfigs: {}, flowEmbedKeys: new Set() }))
vi.mock('./components/ProjectBaseInfo.vue', () => ({ default: { render: () => null } }))
vi.mock('./components/ProjectCurrentPlanTag.vue', () => ({ default: { render: () => null } }))
vi.mock('./components/BusinessFlowNav.vue', () => ({ default: { render: () => null } }))
vi.mock('./components/ProjectApprovalDrawer.vue', () => ({ default: { render: () => null } }))
vi.mock('../projects/ProjectStatusTag.vue', () => ({ default: { render: () => null } }))
vi.mock('./components/ProjectDeviceExportButton.vue', () => ({ default: { render: () => null } }))
vi.mock('./components/ProjectDeviceConfigLogDrawer.vue', () => ({ default: { render: () => null } }))
vi.mock('./components/StageProgressRail.vue', () => ({ default: { render: () => null } }))
vi.mock('./components/ProjectDeliverableDialog.vue', () => ({ default: { render: () => null } }))
// The child contract loads on mount and revalidates on refreshToken. Observe the real parent's scheduling.
vi.mock('./components/ProjectTaskTree.vue', async () => {
  const { defineComponent, onMounted, watch } = await import('vue')
  return { __esModule: true, default: defineComponent({
    props: { projectId: [String, Number], stageCode: String, refreshToken: [String, Number] },
    setup(props) {
      onMounted(() => api.navigation(props.projectId, props.stageCode))
      watch(() => props.projectId, () => api.navigation(props.projectId, props.stageCode))
      watch(() => props.refreshToken, () => api.refresh(props.projectId, props.stageCode))
      return () => null
    }
  }) }
})

const stages = Array.from({ length: 7 }, (_, n) => ({ stageCode: `S${n}`, name: `Stage ${n}`, sortOrder: n, status: 'ACTIVE' }))
const instances = () => ({ projectId: 84577, stages, tasks: stages.map((stage, n) => ({ id: n + 1, stageCode: stage.stageCode, status: 'IN_PROGRESS' })), milestones: [], deliverables: [], gates: [] })
const deferred = () => { let resolve!: (value: any) => void; const promise = new Promise<any>(done => { resolve = done }); return { promise, resolve } }
const stateOf = (view: ReturnType<typeof mount>) => (view.vm as any).$.setupState
const render = () => mount(Page, {}, { ElTable: passthrough, ElTableColumn: tableColumn })

beforeEach(() => {
  vi.clearAllMocks()
  api.route.query.projectId = '84577'
  api.project.mockResolvedValue({ id: 84577, projectName: '采样项目', lifecycleStatus: 'ACTIVE', version: 1 })
  api.instances.mockResolvedValue(instances())
  api.contacts.mockResolvedValue({ list: [] })
  api.risk.mockResolvedValue([])
})

it('does not invalidate a different project using the old project navigation identity', async () => {
  const view = render()
  try {
    await vi.waitFor(() => expect(api.navigation).toHaveBeenCalledTimes(7))
    await vi.waitFor(() => expect(stateOf(view).loading).toBe(false))
    api.refresh.mockClear()
    api.route.query.projectId = '84578'
    api.project.mockResolvedValue({ id: 84578, lifecycleStatus: 'ACTIVE', version: 1 })
    api.instances.mockResolvedValue({ ...instances(), projectId: 84578 })
    await stateOf(view).loadAll()
    await vi.waitFor(() => expect(api.navigation).toHaveBeenCalledTimes(14))
    expect(api.refresh).not.toHaveBeenCalled()
    expect(stateOf(view).instances.projectId).toBe(84578)
  } finally { view.app.unmount() }
})

it('retains existing navigation after a failed reload and revalidates on the successful retry', async () => {
  const view = render()
  try {
    await vi.waitFor(() => expect(api.navigation).toHaveBeenCalledTimes(7))
    await vi.waitFor(() => expect(stateOf(view).loading).toBe(false))
    api.refresh.mockClear()
    api.contacts.mockRejectedValueOnce(new Error('offline'))
    // The existing contact reader records its failure and the full refresh still revalidates the tree.
    await stateOf(view).loadAll()
    await vi.waitFor(() => expect(api.refresh).toHaveBeenCalledTimes(7))
    expect(stateOf(view).primaryContactPending).toBe(true)
    api.refresh.mockClear()
    await stateOf(view).loadAll()
    await vi.waitFor(() => expect(api.refresh).toHaveBeenCalledTimes(7))
    expect(stateOf(view).primaryContactPending).toBe(false)
  } finally { view.app.unmount() }
})

it('does not invalidate seven newly mounted stage trees when the slower contact read completes', async () => {
  const contact = deferred()
  api.contacts.mockReturnValue(contact.promise)
  const view = render()
  try {
    await vi.waitFor(() => expect(api.navigation).toHaveBeenCalledTimes(7))
    expect(stateOf(view).loading).toBe(true)
    contact.resolve({ list: [] })
    await vi.waitFor(() => expect(stateOf(view).loading).toBe(false))
    expect(api.refresh).not.toHaveBeenCalled()
    expect(api.navigation).toHaveBeenCalledTimes(7)
  } finally { view.app.unmount() }
})

it('still refreshes all existing stage trees after a subsequent business or manual reload', async () => {
  const view = render()
  try {
    await vi.waitFor(() => expect(api.navigation).toHaveBeenCalledTimes(7))
    await vi.waitFor(() => expect(stateOf(view).loading).toBe(false))
    api.refresh.mockClear()
    await stateOf(view).loadAll()
    await vi.waitFor(() => expect(api.refresh).toHaveBeenCalledTimes(7))
    expect(api.project).toHaveBeenCalledTimes(2)
    expect(api.instances).toHaveBeenCalledTimes(2)
    expect(api.contacts).toHaveBeenCalledTimes(2)
    expect(api.risk).toHaveBeenCalledTimes(2)
  } finally { view.app.unmount() }
})
