import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import FlowPanel from '../../project-master-detail/components/ProjectFlowPanel.vue'
import { mount, passthrough, tableColumn, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const api = vi.hoisted(() => ({ getProjectWorkspace: vi.fn(), getProjectTasks: vi.fn(), getTaskWorkbench: vi.fn() }))
const leave = vi.hoisted(() => vi.fn())
vi.mock('@/api/pms/project/task-workbench', () => api)
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => ref(false) }))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn(), onBeforeRouteUpdate: vi.fn() }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {} }))
vi.mock('@/utils/formatTime', () => ({ formatDate: (v: unknown) => String(v) }))
vi.mock('./StageBusinessPanel.vue', () => ({ default: defineComponent({
  setup(_, { expose }) { expose({ requestLeave: async () => true }); return () => h('div', '阶段绑定上下文') }
}) }))
vi.mock('./TaskStateActions.vue', () => ({ default: defineComponent({
  setup(_, { expose }) { expose({ isBusy: () => false }); return () => h('div', '任务状态操作') }
}) }))
vi.mock('../wbs/TaskMaintenancePanel.vue', () => ({ default: defineComponent({
  setup(_, { expose }) { expose({ requestLeave: () => true }); return () => h('div', '任务职责维护') }
}) }))
vi.mock('../../project-master-detail/components/TaskBusinessPanel.vue', () => ({ default: defineComponent({
  setup(_, { expose }) { expose({ requestLeave: leave }); return () => h('div', 'Owner业务内容') }
}) }))
const apps: { unmount: () => void }[] = []
const flush = async () => { for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() } }
const render = (selection: Record<string, unknown>) => {
  const child = ref<any>()
  const wrapper = defineComponent({ setup: () => () => h(FlowPanel, {
    ref: child, projectId: 9, project: { id: 9 }, selection,
    instances: { stages: [{ stageCode: 'S2', entryCriteria: '真实准入', exitCriteria: '真实准出' }] }
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
