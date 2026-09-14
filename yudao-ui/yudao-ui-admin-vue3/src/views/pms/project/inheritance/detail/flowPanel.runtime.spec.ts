import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import FlowPanel from '../../project-master-detail/components/ProjectFlowPanel.vue'
import { mount, passthrough, tableColumn, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const api = vi.hoisted(() => ({ getProjectWorkspace: vi.fn(), getProjectTasks: vi.fn(), getTaskWorkbench: vi.fn() }))
const leave = vi.hoisted(() => vi.fn())
const businessRefresh = vi.hoisted(() => vi.fn())
const gates = vi.hoisted(() => ({ getStageGateWorkbench: vi.fn() }))
vi.mock('@/api/pms/project/stage-gates', () => gates)
vi.mock('@/api/pms/project/task-workbench', () => api)
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => ref(false) }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }), onBeforeRouteLeave: vi.fn(), onBeforeRouteUpdate: vi.fn() }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/api/pms/project/projects', () => ({ startProjectStageGateProcess: vi.fn() }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {} }))
vi.mock('@/utils/formatTime', () => ({ formatDate: (v: unknown) => String(v) }))
vi.mock('./StageBusinessPanel.vue', () => ({ default: defineComponent({
  setup(_, { expose }) { expose({ requestLeave: async () => true, isBusy: () => false, refresh: businessRefresh }); return () => h('div', '阶段绑定上下文') }
}) }))
vi.mock('./TaskStateActions.vue', () => ({ default: defineComponent({
  setup(_, { expose, slots }) { expose({ isBusy: () => false }); return () => h('section', { 'aria-label': '任务状态操作' }, ['任务状态操作', slots.default?.()]) }
}) }))
vi.mock('../wbs/TaskMaintenancePanel.vue', () => ({ default: defineComponent({
  setup(_, { expose }) { expose({ requestLeave: () => true, description: { description: '任务的基本说明', descriptionFormat: 'PLAIN' }, canEditDescription: true, openDescription: vi.fn() }); return () => h('div', '任务职责维护') }
}) }))
vi.mock('./TaskApprovalPanel.vue', () => ({ default: defineComponent({
  setup(_, { expose }) { expose({ requestLeave: leave, isBusy: () => false }); return () => h('div', '原BPM审批办理') }
}) }))
vi.mock('./TaskApprovalForm.vue', () => ({ default: defineComponent({
  setup(_, { expose }) { expose({ requestLeave: leave, isBusy: () => false }); return () => h('div', '原BPM审批表单') }
}) }))
vi.mock('../../project-master-detail/components/ProjectTaskDetailsEditor.vue', () => ({ default: defineComponent({
  setup(_, { expose }) { expose({ requestLeave: () => true }); return () => h('div', '任务资料与进度') }
}) }))
vi.mock('../../project-master-detail/components/TaskBusinessPanel.vue', () => ({ default: defineComponent({
  setup(_, { expose }) { expose({ requestLeave: leave, refresh: businessRefresh, loading: false }); return () => h('div', 'Owner业务内容') }
}) }))
const apps: { unmount: () => void }[] = []
const flush = async () => { for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() } }
const render = (selection: Record<string, unknown>, stageGates: { stageCode: string }[] = []) => {
  const child = ref<any>()
  const wrapper = defineComponent({ setup: () => () => h(FlowPanel, {
    ref: child, projectId: 9, project: { id: 9 }, selection,
    instances: { stages: [{ stageCode: 'S2', entryCriteria: '真实准入', exitCriteria: '真实准出' }], gates: stageGates }
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
  expect(textOf(view.root)).toContain('真实准入')
  expect(textOf(view.root)).toContain('阶段绑定上下文')
  expect(api.getTaskWorkbench).not.toHaveBeenCalled()
})

it('places one refresh action next to the task business heading and delegates to the guarded Owner refresh', async () => {
  const view = render({ kind: 'task', stageCode: 'S2', taskId: 10 }); await flush()
  const find = (node: TestNode, predicate: (node: TestNode) => boolean): TestNode | undefined =>
    predicate(node) ? node : node.children.map(child => find(child, predicate)).find(Boolean)
  const heading = find(view.root, node => String(node.props?.class || '').includes('task-business-heading'))
  expect(heading).toBeTruthy()
  expect(textOf(heading!)).toContain('任务业务办理')
  const refresh = find(heading!, node => node.type === 'button' && textOf(node) === '刷新业务结果')
  expect(refresh).toBeTruthy()
  await (refresh!.props!.onClick as () => unknown)()
  expect(businessRefresh).toHaveBeenCalledTimes(1)
  expect(api.getTaskWorkbench).toHaveBeenCalledTimes(1)
})

it('places the description in a full-width basic-information field and task editing in the action group', async () => {
  const view = render({ kind: 'task', stageCode: 'S2', taskId: 10 }); await flush()
  const find = (node: TestNode, predicate: (node: TestNode) => boolean): TestNode | undefined =>
    predicate(node) ? node : node.children.map(child => find(child, predicate)).find(Boolean)
  const description = find(view.root, node => node.props?.label === '任务说明')
  expect(description?.props?.span).toBe(2)
  expect(textOf(description!)).toContain('任务的基本说明')
  const actions = find(view.root, node => node.props?.['aria-label'] === '任务状态操作')
  expect(textOf(actions!)).toContain('任务资料与进度')
})
