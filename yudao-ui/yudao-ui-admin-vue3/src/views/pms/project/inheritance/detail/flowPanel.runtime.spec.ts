import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import FlowPanel from '../../project-master-detail/components/ProjectFlowPanel.vue'
import { mount, passthrough, tableColumn, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const api = vi.hoisted(() => ({ getProjectWorkspace: vi.fn(), getProjectTasks: vi.fn(), getTaskWorkbench: vi.fn() }))
const leave = vi.hoisted(() => vi.fn())
const businessRefresh = vi.hoisted(() => vi.fn())
const gates = vi.hoisted(() => ({ getStageGateWorkbench: vi.fn() }))
const projects = vi.hoisted(() => ({ getProjectStageAdvanceReadiness: vi.fn(), advanceProjectStage: vi.fn(), startProjectStageGateProcess: vi.fn() }))
vi.mock('@/api/pms/project/stage-gates', () => gates)
vi.mock('@/api/pms/project/task-workbench', () => api)
vi.mock('@/api/pms/project/projects', () => projects)
vi.mock('@/config/axios', () => ({ default: {} }))
vi.mock('@/components/UserTag/index.vue', () => ({ default: defineComponent({
  inheritAttrs: false, setup: (_, { attrs }) => () => h('span', String(attrs.userId ?? ''))
}) }))
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => ref(false) }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }), onBeforeRouteLeave: vi.fn(), onBeforeRouteUpdate: vi.fn() }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {} }))
// 用户store在node环境依赖浏览器storage，浅mock挡住加载链
vi.mock('@/store/modules/user', () => ({ useUserStore: vi.fn(), useUserStoreWithOut: vi.fn() }))
// 任务点击时刻的预取会同步调用业务上下文API，mock为立即完成的Promise避免拖垮工作区加载
vi.mock('@/api/pms/project/task-business', () => ({ getTaskBusinessContext: vi.fn(() => Promise.resolve({})) }))
vi.mock('@/utils/formatTime', () => ({ formatDate: (v: unknown) => String(v) }))
vi.mock('./StageBusinessPanel.vue', () => ({ default: defineComponent({
  props: { readonly: Boolean },
  setup(props, { expose }) { expose({ requestLeave: async () => true, isBusy: () => false, refresh: businessRefresh }); return () => h('div', { 'data-stage-readonly': String(props.readonly) }, '阶段绑定上下文') }
}) }))
vi.mock('./TaskStateActions.vue', () => ({ default: defineComponent({
  setup(_, { expose, slots }) { expose({ isBusy: () => false }); return () => h('section', { 'aria-label': '任务状态操作' }, ['任务状态操作', slots.default?.()]) }
}) }))
vi.mock('./TaskApprovalPanel.vue', () => ({ default: defineComponent({
  props: { readonly: Boolean },
  setup(props, { expose }) { expose({ requestLeave: leave, isBusy: () => false }); return () => h('div', { 'data-approval-readonly': String(props.readonly) }, '原BPM审批办理') }
}) }))
vi.mock('./TaskApprovalForm.vue', () => ({ default: defineComponent({
  setup(_, { expose }) { expose({ requestLeave: leave, isBusy: () => false }); return () => h('div', '原BPM审批表单') }
}) }))
vi.mock('../../project-master-detail/components/ProjectTaskDetailsEditor.vue', () => ({ default: defineComponent({
  setup(_, { expose }) { expose({ requestLeave: () => true }); return () => h('div', '任务资料与进度') }
}) }))
vi.mock('../../project-master-detail/components/TaskBusinessPanel.vue', () => ({ default: defineComponent({
  props: { readonly: Boolean },
  setup(props, { expose }) { expose({ requestLeave: leave, refresh: businessRefresh, loading: false }); return () => h('div', { 'data-task-readonly': String(props.readonly) }, 'Owner业务内容') }
}) }))
// 交付件弹窗内部走真实文件API（node环境无window），浅mock为入口桩
vi.mock('../../project-master-detail/components/ProjectDeliverableDialog.vue', () => ({ default: defineComponent({
  setup(_, { expose }) { expose({ open: vi.fn(), requestLeave: async () => true, isBusy: () => false }); return () => h('div', '交付件弹窗') }
}) }))
// 页面嵌入的真实工作台整页组件会拉入store/axios链（node环境无window），与本用例无关，统一浅mock
vi.mock('./ProjectArrivalReceiptPanel.vue', () => ({ default: defineComponent({ setup: () => () => h('div', '到货回执') }) }))
vi.mock('@/views/pms/engineering/installation/index.vue', () => ({ default: defineComponent({
  props: { readonly: Boolean },
  setup: (props) => () => h('div', { 'data-embed-readonly': String(props.readonly) }, '实施部署')
}) }))
vi.mock('@/views/pms/engineering/configuration/index.vue', () => ({ default: defineComponent({ setup: () => () => h('div', '配置实施') }) }))
vi.mock('@/views/pms/engineering/joint-test/index.vue', () => ({ default: defineComponent({ setup: () => () => h('div', '联调测试') }) }))
vi.mock('@/views/pms/engineering/training/index.vue', () => ({ default: defineComponent({ setup: () => () => h('div', '培训') }) }))
vi.mock('@/views/pms/customer/contacts/index.vue', () => ({ default: defineComponent({ setup: () => () => h('div', '客户联系人') }) }))
const apps: { unmount: () => void }[] = []
const flush = async () => { for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() } }
const render = (selection: Record<string, unknown>, stageGates: { stageCode: string }[] = [], instancesExtra: Record<string, unknown> = {}) => {
  const child = ref<any>()
  const wrapper = defineComponent({ setup: () => () => h(FlowPanel, {
    ref: child, projectId: 9, project: { id: 9 }, selection,
    instances: { stages: [{ stageCode: 'S2', entryCriteria: '真实准入', exitCriteria: '真实准出' }], gates: stageGates, ...instancesExtra }
  } as any) })
  const view = mount(wrapper, {}, { ElDescriptions: passthrough, ElDescriptionsItem: passthrough,
    ElTable: passthrough, ElTableColumn: tableColumn, DictTag: passthrough })
  apps.push(view.app)
  return { ...view, state: () => child.value.$.setupState, exposed: () => child.value }
}
beforeEach(() => {
  vi.clearAllMocks()
  leave.mockResolvedValue(false)
  api.getProjectWorkspace.mockResolvedValue({ stageTaskNavigation: [{ stageCode: 'S2', stageName: '计划', taskCount: 0 }] })
  api.getProjectTasks.mockResolvedValue({ rows: [], taskTreeVersion: 1 })
  api.getTaskWorkbench.mockResolvedValue({ task: { taskId: 10, name: '任务A', version: 1 }, bindingType: 'BUSINESS_OBJECT' })
  projects.getProjectStageAdvanceReadiness.mockResolvedValue({ currentStage: 'S2', nextStage: null, advanceAllowed: false, gates: [] })
})
afterEach(() => apps.splice(0).forEach(app => app.unmount()))

it('loads configured gates for the selected parallel stage and does not show an empty extra panel for other stages', async () => {
  gates.getStageGateWorkbench.mockResolvedValue({ projectId: '9', stageCode: 'S2', planVersionId: '51', executionId: '61', executionRound: 2, gates: [] })
  const selected = reactive({ kind: 'stage', stageCode: 'S2' })
  const view = render(selected, [{ stageCode: 'S2' }]); await flush()
  expect(textOf(view.root)).toContain('阶段门禁条件')
  expect(gates.getStageGateWorkbench).toHaveBeenCalledWith(9, 'S2')
  selected.stageCode = 'S3'; await flush()
  expect(textOf(view.root)).not.toContain('阶段门禁条件')
  expect(gates.getStageGateWorkbench).toHaveBeenCalledTimes(1)
})

it('mounts approval handling in the shared delivery task panel and preserves its leave guard', async () => {
  api.getTaskWorkbench.mockResolvedValue({ task: { taskId: 10, version: 1 }, bindingType: 'APPROVAL' })
  const view = render({ kind: 'task', stageCode: 'S2', taskId: 10 }); await flush()
  expect(textOf(view.root)).toContain('原BPM审批办理')
  expect(textOf(view.root)).toContain('刷新审批结果')
  expect(textOf(view.root)).not.toContain('Owner业务内容')
  expect(textOf(view.root)).not.toContain('尚未取得可用的任务业务绑定')
  expect(await view.exposed().requestLeave()).toBe(false)
})

it('shows the template-frozen deliverables of the task as a read-only checklist', async () => {
  api.getTaskWorkbench.mockResolvedValue({ task: { taskId: 10, name: '任务A', version: 1 },
    bindingType: 'TASK_NATIVE',
    deliverables: [
      { id: 1, deliverableCode: 'PLAN', name: '施工计划', required: true, status: 'PENDING' },
      { id: 2, deliverableCode: 'REPORT', name: '勘察报告', required: false, status: 'ACCEPTED' }
    ] })
  const view = render({ kind: 'task', stageCode: 'S2', taskId: 10 }); await flush()
  const text = textOf(view.root)
  expect(text).toContain('交付件清单')
  expect(text).toContain('施工计划')
  expect(text).toContain('勘察报告')
  expect(text).toContain('必选')
  expect(text).toContain('可选')
  expect(text).toContain('自动满足交付要求')
})

it('starts independent task reads together and waits for both before exposing Owner actions', async () => {
  let finishWorkspace!: (value: unknown) => void
  api.getProjectWorkspace.mockReturnValueOnce(new Promise(resolve => { finishWorkspace = resolve }))
  const view = render({ kind: 'task', stageCode: 'S2', taskId: 10 })
  await flush()
  expect(api.getTaskWorkbench).toHaveBeenCalledWith(10)
  expect(textOf(view.root)).not.toContain('Owner业务内容')
  finishWorkspace({ stageTaskNavigation: [] })
  await flush()
  expect(textOf(view.root)).toContain('Owner业务内容')
})

it('keeps task actions closed if the parallel workspace request fails', async () => {
  api.getProjectWorkspace.mockRejectedValueOnce(new Error('403'))
  const view = render({ kind: 'task', stageCode: 'S2', taskId: 10 })
  await flush()
  expect(textOf(view.root)).toContain('内容加载失败')
  expect(textOf(view.root)).not.toContain('Owner业务内容')
  expect(textOf(view.root)).not.toContain('任务状态操作')
})

it('ignores a completed parallel load from the previously selected task', async () => {
  let finishOld!: (value: unknown) => void
  api.getTaskWorkbench.mockReturnValueOnce(new Promise(resolve => { finishOld = resolve }))
  const selection = reactive({ kind: 'task', stageCode: 'S2', taskId: 10 })
  const view = render(selection)
  await flush()
  api.getTaskWorkbench.mockResolvedValueOnce({ task: { taskId: 11, name: '任务B', version: 1 }, bindingType: 'BUSINESS_OBJECT' })
  selection.taskId = 11
  await flush()
  finishOld({ task: { taskId: 10, name: '旧任务A', version: 1 }, bindingType: 'BUSINESS_OBJECT' })
  await flush()
  expect(textOf(view.root)).toContain('任务B')
  expect(textOf(view.root)).not.toContain('旧任务A')
})

it('propagates the Owner leave refusal and does not clear its content during a refused reload', async () => {
  const view = render({ kind: 'task', stageCode: 'S2', taskId: 10 }); await flush()
  expect(textOf(view.root)).toContain('Owner业务内容')
  expect(await view.exposed().requestLeave()).toBe(false)
  await view.exposed().reload()
  expect(api.getTaskWorkbench).toHaveBeenCalledTimes(1)
  expect(textOf(view.root)).toContain('Owner业务内容')
  leave.mockResolvedValue(true)
  await view.exposed().reload(); await flush()
  expect(api.getTaskWorkbench).toHaveBeenCalledTimes(2)
})

it('does not equate S2 with duration or call a task workbench with a stage identity', async () => {
  const view = render({ kind: 'stage', stageCode: 'S2' }); await flush()
  // 阶段编码不再作为徽标展示，头部以阶段名称呈现
  expect(textOf(view.root)).toContain('计划')
  expect(textOf(view.root)).not.toContain('S2')
  expect(textOf(view.root)).toContain('阶段绑定上下文')
  expect(textOf(view.root)).not.toContain('阶段信息')
  expect(api.getTaskWorkbench).not.toHaveBeenCalled()
})

it('places one refresh action in the bottom business bar and delegates to the guarded Owner refresh', async () => {
  const view = render({ kind: 'task', stageCode: 'S2', taskId: 10 }); await flush()
  const find = (node: TestNode, predicate: (node: TestNode) => boolean): TestNode | undefined =>
    predicate(node) ? node : node.children.map(child => find(child, predicate)).find(Boolean)
  const heading = find(view.root, node => String(node.props?.class || '').includes('task-business-heading'))
  expect(heading).toBeTruthy()
  expect(textOf(heading!)).toContain('任务业务办理')
  // 刷新入口统一收口在底部吸附栏业务操作区，不与业务标题同行
  expect(find(heading!, node => node.type === 'button' && textOf(node) === '刷新业务结果')).toBeUndefined()
  const bar = find(view.root, node => String(node.props?.class || '').includes('flow-bar-business'))
  expect(bar).toBeTruthy()
  const refresh = find(bar!, node => node.type === 'button' && textOf(node) === '刷新业务结果')
  expect(refresh).toBeTruthy()
  await (refresh!.props!.onClick as () => unknown)()
  expect(businessRefresh).toHaveBeenCalledTimes(1)
  expect(api.getTaskWorkbench).toHaveBeenCalledTimes(1)
})

it('omits the task breadcrumb and description block and keeps task editing in the action group', async () => {
  const view = render({ kind: 'task', stageCode: 'S2', taskId: 10 }); await flush()
  const find = (node: TestNode, predicate: (node: TestNode) => boolean): TestNode | undefined =>
    predicate(node) ? node : node.children.map(child => find(child, predicate)).find(Boolean)
  const text = textOf(view.root)
  expect(text).not.toContain('任务说明')
  expect(text).not.toContain('任务的基本说明')
  expect(find(view.root, node => String(node.props?.class || '').includes('task-breadcrumb'))).toBeUndefined()
  expect(find(view.root, node => String(node.props?.class || '').includes('task-description-block'))).toBeUndefined()
  const actions = find(view.root, node => node.props?.['aria-label'] === '任务状态操作')
  expect(textOf(actions!)).toContain('任务资料与进度')
})

const pendingStageInstances = {
  stages: [
    { stageCode: 'S2', entryCriteria: '真实准入', exitCriteria: '真实准出' },
    { stageCode: 'S4', name: '实施部署', status: 'PENDING' }
  ],
  deliverables: [
    { id: 7, deliverableCode: 'IMPL-RECORD', name: '实施记录', stageCode: 'S4', required: true, status: 'PENDING' }
  ]
}

const findNode = (node: TestNode, predicate: (node: TestNode) => boolean): TestNode | undefined =>
  predicate(node) ? node : node.children.map(child => findNode(child, predicate)).find(Boolean)

it('renders a not-yet-entered stage normally while hiding its operation entries', async () => {
  const view = render({ kind: 'stage', stageCode: 'S4' }, [], pendingStageInstances); await flush()
  const text = textOf(view.root)
  // 界面照常渲染：阶段头部、业务办理区内容与交付件清单全部可见，不附加状态横幅
  expect(text).toContain('S4')
  expect(text).toContain('阶段业务办理')
  expect(text).toContain('阶段绑定上下文')
  expect(text).toContain('实施记录')
  expect(text).not.toContain('只读预览')
  // 业务区以只读方式呈现（readonly 强制透传）
  expect(findNode(view.root, node => node.props?.['data-stage-readonly'] === 'true')).toBeTruthy()
  // 办理/提交/推进入口全部隐藏；刷新业务结果为重读入口，保留
  expect(findNode(view.root, node => node.type === 'button' && textOf(node) === '提交与查看')).toBeUndefined()
  expect(text).not.toContain('推进至')
  expect(text).toContain('刷新业务结果')
})

it('keeps the stage advance button hidden while exit gates are unmet but still explains the progress', async () => {
  projects.getProjectStageAdvanceReadiness.mockResolvedValue({
    currentStage: 'S2', nextStage: 'S3', advanceAllowed: false, guidance: null,
    gates: [{ gateId: '1', gateCode: 'G1', name: 'G1', status: 'OPEN', satisfied: true, references: [] },
      { gateId: '2', gateCode: 'G2', name: 'G2', status: 'OPEN', satisfied: false, references: [] }]
  })
  const view = render({ kind: 'stage', stageCode: 'S2' }); await flush()
  const text = textOf(view.root)
  expect(text).not.toContain('推进至 S3')
  expect(text).toContain('出口门禁 1/2 满足；全部通过后可推进。')
  // 当前阶段的业务操作区不受影响
  expect(text).toContain('刷新业务结果')
})

it('shows the stage advance button only when every exit gate is satisfied', async () => {
  projects.getProjectStageAdvanceReadiness.mockResolvedValue({
    currentStage: 'S2', nextStage: 'S3', advanceAllowed: true, guidance: null, gates: []
  })
  const view = render({ kind: 'stage', stageCode: 'S2' }); await flush()
  expect(textOf(view.root)).toContain('推进至 S3')
})

it('renders tasks of a not-yet-entered stage normally without state actions', async () => {
  api.getTaskWorkbench.mockResolvedValue({
    task: { taskId: 30, name: '硬实施', stageCode: 'S4', version: 1 },
    bindingType: 'BUSINESS_OBJECT',
    deliverables: [{ id: 8, deliverableCode: 'IMPL-PLAN', name: '实施方案', required: true, status: 'PENDING' }]
  })
  const view = render({ kind: 'task', stageCode: 'S4', taskId: 30, task: { taskId: 30, name: '硬实施', stageCode: 'S4' } }, [], pendingStageInstances)
  await flush()
  const text = textOf(view.root)
  expect(text).toContain('硬实施')
  expect(text).toContain('实施方案')
  expect(text).not.toContain('阶段尚未进入')
  // 业务内容照常渲染，以只读方式呈现（不附加状态横幅）
  expect(text).toContain('Owner业务内容')
  expect(findNode(view.root, node => node.props?.['data-task-readonly'] === 'true')).toBeTruthy()
  // 状态操作与提交入口仍隐藏；刷新业务结果为重读入口，保留
  expect(text).not.toContain('任务状态操作')
  expect(findNode(view.root, node => node.type === 'button' && textOf(node) === '提交与查看')).toBeUndefined()
  expect(text).toContain('刷新业务结果')
})

it('renders an approval task of a not-yet-entered stage without its handling form', async () => {
  api.getTaskWorkbench.mockResolvedValue({ task: { taskId: 31, name: '审批任务', stageCode: 'S4', version: 1 }, bindingType: 'APPROVAL' })
  const view = render({ kind: 'task', stageCode: 'S4', taskId: 31, task: { taskId: 31, name: '审批任务', stageCode: 'S4' } }, [], pendingStageInstances)
  await flush()
  const text = textOf(view.root)
  expect(text).toContain('原BPM审批办理')
  expect(findNode(view.root, node => node.props?.['data-approval-readonly'] === 'true')).toBeTruthy()
})

it('renders the page-bound task of a not-yet-entered stage inline with its write operations hidden', async () => {
  api.getTaskWorkbench.mockResolvedValue({ task: { taskId: 32, name: '页面任务', stageCode: 'S4', version: 1 },
    bindingType: 'PAGE', trustedTargetRef: '/pms/engineering/execution/imp-installation' })
  const view = render({ kind: 'task', stageCode: 'S4', taskId: 32, task: { taskId: 32, name: '页面任务', stageCode: 'S4' } }, [], pendingStageInstances)
  await flush()
  const text = textOf(view.root)
  // 页面照常内嵌展示（不退化为冻结路由提示），并以只读方式挂载
  expect(text).toContain('实施部署')
  expect(text).not.toContain('本任务通过专用页面办理业务')
  expect(findNode(view.root, node => node.props?.['data-embed-readonly'] === 'true')).toBeTruthy()
  // 任务状态操作与跳转入口隐藏；刷新工作区为重读入口，保留（栏不空壳）
  expect(findNode(view.root, node => node.props?.['aria-label'] === '任务状态操作')).toBeUndefined()
  expect(findNode(view.root, node => node.type === 'button' && textOf(node) === '打开页面')).toBeUndefined()
  expect(text).toContain('刷新工作区')
})
